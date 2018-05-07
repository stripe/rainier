package rainier.compute.asm

import rainier.compute._

private class Translator {
  private val binary = new SymCache[BinaryOp]
  private val unary = new SymCache[UnaryOp]
  private val ifs = new SymCache[Unit]

  def toIR(r: Real): IR = r match {
    case v: Variable         => Parameter(v)
    case Constant(value)     => Const(value)
    case Unary(original, op) => unaryIR(toIR(original), op)
    case i: If               => ifIR(toIR(i.whenNonZero), toIR(i.whenZero), toIR(i.test))
    case l: Line             => lineIR(l)
    case l: LogLine          => logLineIR(l)
  }

  private def unaryIR(original: IR, op: UnaryOp): IR =
    unary.memoize(List(List(original)), op, new UnaryIR(original, op))

  private def binaryIR(left: IR, right: IR, op: BinaryOp): IR = {
    val key = List(left, right)
    val keys =
      if (op.isCommutative)
        List(key)
      else
        List(key, key.reverse)
    binary.memoize(keys, op, new BinaryIR(left, right, op))
  }

  private def ifIR(whenZero: IR, whenNonZero: IR, test: IR): IR =
    ifs.memoize(List(List(test, whenZero, whenNonZero)),
                (),
                new IfIR(test, whenZero, whenNonZero))

  private def lineIR(line: Line): IR = {
    val (y, k) = LineOps.factor(line)
    factoredLine(y.ax, y.b, k, multiplyRing)
  }

  private def logLineIR(line: LogLine): IR = {
    val (y, k) = LogLineOps.factor(line)
    factoredLine(y.ax, 0.0, k, powRing)
  }

  private def factoredLine(ax: Map[NonConstant, Double],
                           b: Double,
                           factor: Double,
                           ring: Ring): IR = {
    val posTerms = ax.filter(_._2 > 0.0).toList
    val negTerms =
      ax.filter(_._2 < 0.0).map { case (x, a) => x -> a.abs }.toList

    val allPosTerms =
      if (b == 0.0)
        posTerms
      else
        (Constant(b), 1.0) :: posTerms

    val (ir, sign) =
      (allPosTerms.isEmpty, negTerms.isEmpty) match {
        case (true, true)  => (Const(0.0), 1.0)
        case (true, false) => (combineTerms(negTerms, ring), -1.0)
        case (false, true) => (combineTerms(allPosTerms, ring), 1.0)
        case (false, false) =>
          val posSum = combineTerms(allPosTerms, ring)
          val negSum = combineTerms(negTerms, ring)
          (binaryIR(posSum, negSum, ring.minus), 1.0)
      }

    (factor * sign) match {
      case 1.0 => ir
      case -1.0 =>
        binaryIR(Const(ring.zero), ir, ring.minus)
      case 2.0 =>
        binaryIR(ir, ref(ir), ring.plus)
      case k =>
        binaryIR(ir, Const(k), ring.times)
    }
  }

  private def combineTerms(terms: Seq[(Real, Double)], ring: Ring): IR = {
    val ir = terms.map {
      case (x, 1.0) => toIR(x)
      case (x, 2.0) =>
        binaryIR(toIR(x), toIR(x), ring.plus)
      case (l: LogLine, a) => //this can only happen for a Line's terms
        factoredLine(l.ax, a, 1.0, powRing)
      case (x, a) =>
        binaryIR(toIR(x), Const(a), ring.times)
    }
    combineTree(ir, ring)
  }

  private def combineTree(terms: Seq[IR], ring: Ring): IR =
    if (terms.size == 1)
      terms.head
    else
      combineTree(
        terms.grouped(2).toList.map {
          case oneOrTwo =>
            if (oneOrTwo.size == 1)
              oneOrTwo.head
            else {
              val left = oneOrTwo(0)
              val right = oneOrTwo(1)
              binaryIR(left, right, ring.plus)
            }
        },
        ring
      )

  private def ref(ir: IR): Ref =
    ir match {
      case r: Ref         => r
      case VarDef(sym, _) => VarRef(sym)
      case _              => sys.error("Should only see refs and vardefs")
    }

  private case class Ring(times: BinaryOp,
                          plus: BinaryOp,
                          minus: BinaryOp,
                          zero: Double)
  private val multiplyRing = Ring(MultiplyOp, AddOp, SubtractOp, 0.0)
  private val powRing = Ring(PowOp, MultiplyOp, DivideOp, 1.0)

  private class SymCache[K] {
    var cache = Map.empty[(List[Ref], K), Sym]
    def memoize(irKeys: Seq[List[IR]], opKey: K, ir: => IR): IR = {
      val refKeys = irKeys.map { l =>
        l.map(ref)
      }
      val hit = refKeys.foldLeft(Option.empty[Sym]) {
        case (opt, k) =>
          opt.orElse { cache.get((k, opKey)) }
      }
      hit match {
        case Some(sym) => VarRef(sym)
        case None =>
          val sym = Sym.freshSym()
          cache += (refKeys.head, opKey) -> sym
          new VarDef(sym, ir)
      }
    }
  }
}
