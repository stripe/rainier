package rainier.compute.asm

import rainier.compute._

private sealed trait IR {
  def consKey: IR = this
}

private case class Parameter(original: Variable) extends IR
private case class Const(value: Double) extends IR

private class BinaryIR(val left: IR, val right: IR, val op: BinaryOp) extends IR
private class UnaryIR(val original: IR, val op: UnaryOp) extends IR

private case class Sym private (id: Int)
private object Sym {
  private var curIdx = 0
  def freshSym(): Sym = {
    val r = Sym(curIdx)
    curIdx += 1
    r
  }
}

private class VarDef(val sym: Sym, val rhs: IR) extends IR {
  override def consKey = VarRef(sym)
}
private case class VarRef(sym: Sym) extends IR

private class MethodDef(val sym: Sym, val rhs: IR) extends IR
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
