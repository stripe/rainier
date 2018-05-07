package rainier.compute.asm

import rainier.compute._

private sealed trait IR

private sealed trait Ref extends IR
private case class Parameter(original: Variable) extends Ref
private case class Const(value: Double) extends Ref
private case class VarRef(sym: Sym) extends Ref

private case class BinaryIR(left: IR, right: IR, op: BinaryOp) extends IR
private case class UnaryIR(original: IR, op: UnaryOp) extends IR
private case class IfIR(test: IR, whenNonZero: IR, whenZero: IR) extends IR

private case class VarDef(sym: Sym, rhs: IR) extends IR

private case class MethodDef(sym: Sym, rhs: IR) extends IR
private case class MethodRef(sym: Sym) extends IR

private sealed trait BinaryOp {
  def isCommutative: Boolean
}
private object AddOp extends BinaryOp {
  val isCommutative = true
}
private object MultiplyOp extends BinaryOp {
  val isCommutative = true
}
private object SubtractOp extends BinaryOp {
  val isCommutative = false
}
private object DivideOp extends BinaryOp {
  val isCommutative = false
}
private object PowOp extends BinaryOp {
  val isCommutative = false
}

private case class Sym private (id: Int)
private object Sym {
  private var curIdx = 0
  def freshSym(): Sym = {
    val r = Sym(curIdx)
    curIdx += 1
    r
  }
}
