package com.stripe.rainier.compute

import com.stripe.rainier.ir._

private class Translator {
  private val binary = new SymCache[BinaryOp]
  private val unary = new SymCache[UnaryOp]
  private val ifs = new SymCache[Unit]
  private var reals = Map.empty[Real, Expr]

  def toExpr(r: Real): Expr = reals.get(r) match {
    case Some(expr) => ref(expr)
    case None =>
      val expr = r match {
        case v: Variable         => v.param
        case Infinity            => Const(1.0 / 0.0)
        case NegInfinity         => Const(-1.0 / 0.0)
        case Constant(value)     => Const(value.toDouble)
        case Unary(original, op) => unaryExpr(toExpr(original), op)
        case i: If =>
          ifExpr(toExpr(i.whenNonZero), toExpr(i.whenZero), toExpr(i.test))
        case l: Line    => lineExpr(l)
        case l: LogLine => logLineExpr(l)
        case Pow(base, exponent) =>
          binaryExpr(toExpr(base), toExpr(exponent), PowOp)
      }
      reals += r -> expr
      expr
  }

  private def unaryExpr(original: Expr, op: UnaryOp): Expr =
    unary.memoize(List(List(original)), op, new UnaryIR(original, op))

  private def binaryExpr(left: Expr, right: Expr, op: BinaryOp): Expr = {
    val key = List(left, right)
    val keys =
      if (op.isCommutative)
        List(key)
      else
        List(key, key.reverse)
    binary.memoize(keys, op, new BinaryIR(left, right, op))
  }

  private def ifExpr(whenZero: Expr, whenNonZero: Expr, test: Expr): Expr =
    ifs.memoize(List(List(test, whenZero, whenNonZero)),
                (),
                new IfIR(test, whenZero, whenNonZero))

  private def lineExpr(line: Line): Expr = {
    val (y, k) = LineOps.factor(line)
    factoredSumLine(y.ax, y.b, k.toDouble)
  }

  private def logLineExpr(line: LogLine): Expr = {
    val (y, k) = LogLineOps.factor(line)
    factoredLine(y.ax, Real.BigOne, k.toDouble, powRing)
  }

  /**
  factoredLine(), along with combineTerms() and combineTree(),
  is responsible for producing IR for both Line and LogLine.
  It is expressed, and most easily understood, in terms of the Line case,
  where it is computing ax + b. The LogLine case uses the same logic, but
  under a non-standard ring, where the + operation is multiplication,
  the * operation is exponentiation, and the identity element is 1.0. All
  of the logic and optimizations work just the same for either ring.

  (Pedantic note: what LogLine uses is technically a Rig not a Ring because you can't
  divide by zero, but this code will not introduce any divisions by zero that
  were not already there to begin with.)

  In general, the strategy is to split the summation into a set of positively-weighted
  terms and negatively-weighted terms, sum the positive terms to get x, sum the
  absolute value of the negative terms to get y, and return x-y.

  Each of the sub-summations proceeds by recursively producing a balanced binary tree
  where every interior node is the sum of its two children; this keeps the tree-depth
  of the AST small.

  Since the summation is a dot product, most of the terms will be of the form a*x.
  If a=1, we can just take x. If a=2, it can be a minor optimization to take x+x.

  The result may also be multiplied by a constant scaling factor (generally
  factored out of the original summation).
  **/
  private def factoredSumLine(ax: Coefficients,
                              b: BigDecimal,
                              factor: Double): IR = {
    val ring = multiplyRing
    val terms = ax.toList
    val allTerms =
      if (b == ring.zero)
        terms
      else
        (Constant(b), Real.BigOne) :: terms
    val ir = combineSumTerms(allTerms, ring)
    factor match {
      case 1.0 => ir
      case -1.0 =>
        binaryIR(Const(ring.zero), ir, ring.minus)
      case 2.0 =>
        binaryIR(ir, ref(ir), ring.plus)
      case k =>
        binaryIR(ir, Const(k), ring.times)
    }
  }

  private def factoredLine(ax: Coefficients,
                           b: BigDecimal,
                           factor: Double,
                           ring: Ring): Expr = {
    val terms = ax.toList
    val posTerms = terms.filter(_._2 > Real.BigZero)
    val negTerms =
      terms.filter(_._2 < Real.BigZero).map { case (x, a) => x -> a.abs }

    val allPosTerms =
      if (b == ring.zero)
        posTerms
      else
        (Constant(b), Real.BigOne) :: posTerms

    val (expr, sign) =
      (allPosTerms.isEmpty, negTerms.isEmpty) match {
        case (true, true)  => (Const(0.0), 1.0)
        case (true, false) => (combineTerms(negTerms, ring), -1.0)
        case (false, true) => (combineTerms(allPosTerms, ring), 1.0)
        case (false, false) =>
          val posSum = combineTerms(allPosTerms, ring)
          val negSum = combineTerms(negTerms, ring)
          (binaryExpr(posSum, negSum, ring.minus), 1.0)
      }

    (factor * sign) match {
      case 1.0 => expr
      case -1.0 =>
        binaryExpr(Const(ring.zero), expr, ring.minus)
      case 2.0 =>
        binaryExpr(expr, ref(expr), ring.plus)
      case k =>
        binaryExpr(expr, Const(k), ring.times)
    }
  }

  private def makeLazyExprs(terms: Seq[(Real, BigDecimal)],
                          ring: Ring): Seq[() => Expr] = {
    terms.map {
      case (x, Real.BigOne) =>
        () =>
          toExpr(x)
      case (x, Real.BigTwo) =>
        () =>
          binaryExpr(toExpr(x), toExpr(x), ring.plus)
      case (l: LogLine, a) => //this can only happen for a Line's terms
        () =>
          factoredLine(l.ax, a, 1.0, powRing)
      case (x, a) =>
        () =>
          binaryExpr(toExpr(x), Const(a.toDouble), ring.times)
    }
  }

  private def combineTerms(terms: Seq[(Real, BigDecimal)], ring: Ring): Expr = {
    val lazyExprs = makeLazyExprs(terms, ring)
    combineTree(lazyExprs, ring)
  }

  private def combineSumTerms(terms: Seq[(Real, BigDecimal)],
                              ring: Ring): Expr = {
    val lazyExprs = makeLazyExprs(terms, ring)
    lazyExprs match {
      case ts if ts.size < 3 => combineTree(ts, ring)
      case ts =>
        val exprs = ts.map(_()).toList
        binary.memoize(List(exprs), AddOp, SumIR(exprs))
    }
    combineTree(lazyExpr, ring)
  }

  private def combineTree(terms: Seq[() => Expr], ring: Ring): Expr =
    terms match {
      case Seq(t) => t()
      case _ =>
        combineTree(
          terms.grouped(2).toList.map {
            case Seq(t) => t
            case Seq(left, right) =>
              () =>
                binaryExpr(left(), right(), ring.plus)
            case _ => sys.error("Should only have 1 or 2 elems")
          },
          ring
        )
    }

  private def ref(expr: Expr): Ref =
    expr match {
      case r: Ref         => r
      case VarDef(sym, _) => VarRef(sym)
    }

  private final class Ring(val times: BinaryOp,
                           val plus: BinaryOp,
                           val minus: BinaryOp,
                           val zero: Double)
  private val multiplyRing = new Ring(MultiplyOp, AddOp, SubtractOp, 0.0)
  private val powRing = new Ring(PowOp, MultiplyOp, DivideOp, 1.0)

  /*
  This performs hash-consing aka the flyweight pattern to ensure that we don't
  generate code to compute the same quantity twice. It keeps a cache keyed by one or more Expr objects
  along with some operation that will combine them to form a new IR. The Expr keys are required
  to be in their lightweight Ref form rather than VarDefs - this is both to avoid the expensive
  recursive equality/hashing of a def, and also to ensure that we can memoize values derived from a def
  and its ref equally well.
   */
  private class SymCache[K] {
    var cache: Map[(List[Ref], K), Sym] = Map.empty
    def memoize(exprKeys: Seq[List[Expr]], opKey: K, ir: => IR): Expr = {
      val refKeys = exprKeys.map { l =>
        l.map(ref)
      }
      val hit = refKeys.foldLeft(Option.empty[Sym]) {
        case (opt, k) =>
          opt.orElse { cache.get((k, opKey)) }
      }
      hit match {
        case Some(sym) =>
          if (!exprKeys.head.collect { case d: VarDef => d }.isEmpty)
            sys.error("VarRef was used before its VarDef")
          VarRef(sym)
        case None =>
          val sym = Sym.freshSym()
          cache += (refKeys.head, opKey) -> sym
          new VarDef(sym, ir)
      }
    }
  }
}
