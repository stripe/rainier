package com.stripe.rainier.ir

import scala.collection.mutable

private class Packer(methodSizeLimit: Int) {
  private val methodDefs: mutable.Map[Sym, MethodDef] = mutable.Map.empty
  def methods: Set[MethodDef] = methodDefs.values.toSet

  def pack(p: IR): MethodRef = {
    val (pIR, _) = traverse(p)
    createMethod(pIR)
  }

  private def traverse(p: IR): (IR, Int) = p match {
    case v: VarDef =>
      val (rhsIR, rhsSize) =
        traverseAndMaybePack(v.rhs, methodSizeLimit - 1)
      (new VarDef(v.sym, rhsIR), rhsSize + 1)
    case b: BinaryIR =>
      val (leftIR, leftSize) =
        traverseAndMaybePack(b.left, methodSizeLimit / 2)
      val (rightIR, rightSize) =
        traverseAndMaybePack(b.right, methodSizeLimit / 2)
      (new BinaryIR(leftIR, rightIR, b.op), leftSize + rightSize + 1)
    case u: UnaryIR =>
      val (originalIR, irSize) =
        traverseAndMaybePack(u.original, methodSizeLimit - 1)
      (new UnaryIR(originalIR, u.op), irSize + 1)
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
    val md = new MethodDef(s, rhs)
    methodDefs(s) = md
    MethodRef(s, md.level)
  }
}
