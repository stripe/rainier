package com.stripe.rainier.ir

sealed trait IR {
  def level: Int
}

sealed trait Ref extends IR

final class Parameter(val level: Int) extends Ref {
  val sym = Sym.freshSym
}

final case class Const(value: Double) extends Ref {
  val level = 0
}

final case class VarRef(sym: Sym, level: Int) extends Ref

final case class BinaryIR(left: IR, right: IR, op: BinaryOp) extends IR {
  val level = left.level.max(right.level)
}
final case class UnaryIR(original: IR, op: UnaryOp) extends IR {
  val level = original.level
}
final case class IfIR(test: IR, whenNonZero: IR, whenZero: IR) extends IR {
  val level = List(test, whenNonZero, whenZero).map(_.level).max
}

final case class VarDef(sym: Sym, rhs: IR) extends IR {
  val level = rhs.level
}

final case class MethodDef(sym: Sym, rhs: IR) extends IR {
  val level = rhs.level
}

final case class MethodRef(sym: Sym, level: Int) extends IR

final case class Sym private (id: Int)
object Sym {
  @volatile private var id: Int = 0
  def freshSym(): Sym = this.synchronized {
    val sym = Sym(id)
    id += 1
    sym
  }
}
