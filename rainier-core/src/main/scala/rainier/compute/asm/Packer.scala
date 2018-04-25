package rainier.compute.asm

import scala.collection.mutable

private class Packer(methodSizeLimit: Int) {
  private val methodDefs: mutable.Map[Sym, MethodDef] = mutable.Map.empty
  def methods: Set[MethodDef] = methodDefs.values.toSet

  def pack(p: IR): MethodRef = {
    val (pIR, _) = traverse(p)
    createMethod(pIR)
  }

  private def traverse(p: IR): (IR, Int) = p match {
    case VarDef(sym, rhs) =>
      val (rhsIR, rhsSize) =
        traverseAndMaybePack(rhs, methodSizeLimit - 1)
      (VarDef(sym, rhsIR), rhsSize + 1)
    case BinaryIR(left, right, op) =>
      val (leftIR, leftSize) =
        traverseAndMaybePack(left, methodSizeLimit / 2)
      val (rightIR, rightSize) =
        traverseAndMaybePack(right, methodSizeLimit / 2)
      (BinaryIR(leftIR, rightIR, op), leftSize + rightSize + 1)
    case UnaryIR(original, op) =>
      val (originalIR, irSize) =
        traverseAndMaybePack(original, methodSizeLimit - 1)
      (UnaryIR(originalIR, op), irSize + 1)
    case _ => (p, 1)
  }

  private def traverseAndMaybePack(p: IR, localSizeLimit: Int): (IR, Int) = {
    val (pt, size) = traverse(p)
    if (size >= localSizeLimit)
      (createMethod(pt), 1)
    else
      (pt, size)
  }

  private def createMethod(rhs: IR): MethodRef = {
    val s = Sym.freshSym()
    val md = MethodDef(s, rhs)
    methodDefs(s) = md
    MethodRef(s)
  }
}
