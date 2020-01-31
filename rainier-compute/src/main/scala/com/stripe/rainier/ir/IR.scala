package com.stripe.rainier.ir

sealed trait Expr
sealed trait Ref extends Expr
final class Param extends Ref {
  val sym: Sym = Sym.freshSym
}
final case class Const(value: Double) extends Ref
final case class VarRef(sym: Sym) extends Ref

final case class VarDef(sym: Sym, rhs: IR) extends Expr

object VarDef {
  def apply(ir: IR): VarDef =
    VarDef(Sym.freshSym, ir)
}

sealed trait IR
final case class BinaryIR(left: Expr, right: Expr, op: BinaryOp) extends IR
final case class UnaryIR(original: Expr, op: UnaryOp) extends IR
final case class LookupIR(index: Expr, table: List[Ref], low: Int) extends IR
final case class MethodRef(sym: Sym) extends IR
final case class SeqIR(first: VarDef, second: VarDef) extends IR

object SeqIR {
  def apply(seq: Seq[VarDef]): VarDef =
    tree(seq.toList, seq.size)

  private def tree(list: List[VarDef], size: Int): VarDef =
    size match {
      case 1 => list.head
      case 2 => VarDef(SeqIR(list.head, list.tail.head))
      case _ =>
        val leftSize = size / 2
        val left = tree(list, leftSize)
        val right = tree(list.drop(leftSize), size - leftSize)
        VarDef(SeqIR(left, right))
    }
}

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
