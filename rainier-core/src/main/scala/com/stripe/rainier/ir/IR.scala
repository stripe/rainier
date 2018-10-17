package com.stripe.rainier.ir

sealed trait IR

sealed trait Ref extends IR
final class Parameter extends Ref {
  val sym = Sym.freshSym
}
final case class Const(value: Double) extends Ref
final case class VarRef(sym: Sym) extends Ref

final case class NaryIR(irs: List[IR], op: BinaryOp) extends IR
final case class BinaryIR(left: IR, right: IR, op: BinaryOp) extends IR
final case class UnaryIR(original: IR, op: UnaryOp) extends IR
final case class IfIR(test: IR, whenNonZero: IR, whenZero: IR) extends IR

final case class VarDef(sym: Sym, rhs: IR) extends IR

final case class MethodDef(sym: Sym, rhs: IR) extends IR
final case class MethodRef(sym: Sym) extends IR

final case class Sym private (id: Int)
object Sym {
  @volatile private var id: Int = 0
  def freshSym(): Sym = this.synchronized {
    val sym = Sym(id)
    id += 1
    sym
  }
}
