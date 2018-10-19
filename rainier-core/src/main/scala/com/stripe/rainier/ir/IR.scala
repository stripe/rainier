package com.stripe.rainier.ir

sealed trait Expr
sealed trait Ref extends Expr
final class Parameter extends Ref {
  val sym = Sym.freshSym
}
final case class Const(value: Double) extends Ref
final case class VarRef(sym: Sym) extends Ref

final case class VarDef(sym: Sym, rhs: IR) extends Expr

sealed trait IR
final case class BinaryIR(left: Expr, right: Expr, op: BinaryOp) extends IR
final case class UnaryIR(original: Expr, op: UnaryOp) extends IR
final case class IfIR(test: Expr, whenNonZero: Expr, whenZero: Expr) extends IR
final case class LookupIR(index: Expr, table: Seq[(Option[VarDef], Ref)])
    extends IR
final case class MethodRef(sym: Sym) extends IR

final case class MethodDef(sym: Sym, rhs: IR)

final case class Sym private (id: Int)
object Sym {
  @volatile private var id: Int = 0
  def freshSym(): Sym = this.synchronized {
    val sym = Sym(id)
    id += 1
    sym
  }
}
