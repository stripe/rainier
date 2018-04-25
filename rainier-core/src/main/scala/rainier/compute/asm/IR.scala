package rainier.compute.asm

import rainier.compute

import scala.collection.mutable

private sealed trait IR

private case class Parameter(original: compute.Variable) extends IR
private case class Const(value: Double) extends IR

private case class BinaryIR(left: IR, right: IR, op: compute.BinaryOp)
    extends IR
private case class UnaryIR(original: IR, op: compute.UnaryOp) extends IR

private case class Sym private (id: Int)
private object Sym {
  private var curIdx = 0
  def freshSym(): Sym = {
    val r = Sym(curIdx)
    curIdx += 1
    r
  }
}

private case class VarDef(sym: Sym, rhs: IR) extends IR
private case class VarRef(sym: Sym) extends IR

private case class MethodDef(sym: Sym, rhs: IR) extends IR
private case class MethodRef(sym: Sym) extends IR
