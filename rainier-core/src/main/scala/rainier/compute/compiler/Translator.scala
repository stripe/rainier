package rainier.compute.compiler

import rainier.compute._
import scala.collection.mutable

private class Translator {
  private val alreadySeen = mutable.Map.empty[Real, Sym]

  def toIR(r: Real): IR = {
    if (alreadySeen.contains(r))
      VarRef(alreadySeen(r))
    else
      r match {
        case Constant(value) => Const(value)
        case v: Variable     => Parameter(v)
        case b: BinaryReal =>
          val bIR = BinaryIR(toIR(b.left), toIR(b.right), b.op)
          createVarDefFromOriginal(b, bIR)
        case u: UnaryReal =>
          val uIR = UnaryIR(toIR(u.original), u.op)
          createVarDefFromOriginal(u, uIR)
      }
  }

  private def createVarDefFromOriginal(original: Real, rhs: IR): VarDef = {
    val s = Sym.freshSym()
    val vd = VarDef(s, rhs)
    alreadySeen(original) = s
    vd
  }
}
