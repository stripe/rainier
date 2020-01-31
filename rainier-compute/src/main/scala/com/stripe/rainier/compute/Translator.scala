package com.stripe.rainier.compute

import com.stripe.rainier.ir._

private class Translator {
  private val binary = new SymCache[BinaryOp]
  private val unary = new SymCache[UnaryOp]
  private var reals = Map.empty[Real, Expr]

  def toExpr(r: Real): Expr = reals.get(r) match {
    case Some(expr) => ref(expr)
    case None =>
      val expr = r match {
        case v: Parameter        => v.param
        case c: Constant         => constToExpr(c)
        case Unary(original, op) => unaryExpr(toExpr(original), op)
        case l: Line             => lineExpr(l)
        case l: LogLine          => logLineExpr(l)
        case Pow(base, exponent) =>
          binaryExpr(toExpr(base), toExpr(exponent), PowOp)
        case Compare(left, right) =>
          binaryExpr(toExpr(left), toExpr(right), CompareOp)
        case l: Lookup => lookupExpr(l)
      }
      reals += r -> expr
      expr
  }

  private def constToExpr(c: Constant) = c match {
    case Scalar(value) => Const(value)
    case c: Column =>
      c.maybeScalar match {
        case Some(value) => Const(value)
        case None        => c.param
      }
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

  private def lookupExpr(lookup: Lookup): Expr = {
    val tableExprs = lookup.table.map(toExpr).toList
    val defs = tableExprs.collect {
      case v: VarDef =>
        v
    }
    val index = toExpr(lookup.index)
    val refs = tableExprs.map(ref)
    val lookupExpr = VarDef(LookupIR(index, refs, lookup.low))
    SeqIR(defs :+ lookupExpr)
  }

  private def lineExpr(line: Line): Expr = {
    makeLine(line.ax, line.b, multiplyRing)
  }

  private def logLineExpr(line: LogLine): Expr = {
    makeLine(line.ax, Constant.One, powRing)
  }

  /**
  makeLine(), along with combineTerms() and combineTree(),
  is responsible for producing IR for both Line and LogLine.
  It is expressed, and most easily understood, in terms of the Line case,
  where it is computing ax + b. The LogLine case uses the same logic, but
  under a non-standard ring, where the + operation is multiplication,
  the * operation is exponentiation, and the identity element is 1.0. All
  of the logic and optimizations work just the same for either ring.

  (Pedantic note: what LogLine uses is technically a Rig not a Ring because you can't
  divide by zero, but this code will not introduce any divisions by zero that
  were not already there to begin with.)

  Each of the sub-summations proceeds by recursively producing a balanced binary tree
  where every interior node is the sum of its two children; this keeps the tree-depth
  of the AST small.

  Since the summation is a dot product, most of the terms will be of the form a*x.
  If a=1, we can just take x. If a=2, it can be a minor optimization to take x+x.
  **/
  private def makeLine(ax: Coefficients, b: Constant, ring: Ring): Expr = {
    val terms = ax.toList.map { case (x, a) => (x, constToExpr(a)) }
    val allTerms =
      if (b.isZero)
        terms
      else
        (b, Const(1.0)) :: terms
    combineTerms(allTerms, ring)
  }

  private def makeLazyExprs(terms: Seq[(Real, Expr)],
                            ring: Ring): Seq[() => Expr] = {
    terms.map {
      case (x, Const(1.0)) =>
        () =>
          toExpr(x)
      case (x, Const(2.0)) =>
        () =>
          binaryExpr(toExpr(x), toExpr(x), ring.plus)
      case (x, a) =>
        () =>
          binaryExpr(toExpr(x), a, ring.times)
    }
  }

  private def combineTerms(terms: Seq[(Real, Expr)], ring: Ring): Expr = {
    val lazyExprs = makeLazyExprs(terms, ring)
    if (ring.useTree)
      combineTree(lazyExprs, ring)
    else { //TODO: remember why we don't use the tree for summation
      lazyExprs.tail.foldLeft(lazyExprs.head()) {
        case (accum, t) => binaryExpr(accum, t(), ring.plus)
      }
    }
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
                           val zero: Double,
                           val useTree: Boolean)
  private val multiplyRing =
    new Ring(MultiplyOp, AddOp, SubtractOp, 0.0, false)
  private val powRing = new Ring(PowOp, MultiplyOp, DivideOp, 1.0, true)

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
