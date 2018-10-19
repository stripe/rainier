package com.stripe.rainier.ir

import scala.collection.mutable

private class Packer(methodSizeLimit: Int) {
  private val methodDefs: mutable.Map[Sym, MethodDef] = mutable.Map.empty
  def methods: Set[MethodDef] = methodDefs.values.toSet

  def pack(p: IR): MethodRef = {
    val (pIR, _) = traverse(p)
    createMethod(pIR)
  }

  private def traverse(p: IR): (IR, Int) =
    p match {
      case v: VarDef =>
        val (rhsIR, rhsSize) =
          traverseAndMaybePack(v.rhs, 1)
        (new VarDef(v.sym, rhsIR), rhsSize + 1)
      case s: SumIR =>
        (s, s.irs.size)
      case b: BinaryIR =>
        val (leftIR, leftSize) =
          traverseAndMaybePack(b.left, 2)
        val (rightIR, rightSize) =
          traverseAndMaybePack(b.right, leftSize + 1)
        (new BinaryIR(leftIR, rightIR, b.op), leftSize + rightSize + 1)
      case u: UnaryIR =>
        val (originalIR, irSize) =
          traverseAndMaybePack(u.original, 1)
        (new UnaryIR(originalIR, u.op), irSize + 1)
      case f: IfIR =>
        val (testIR, testSize) =
          traverseAndMaybePack(f.test, 3)
        val (nzIR, nzSize) =
          traverseAndMaybePack(f.whenNonZero, testSize + 2)
        val (zIR, zSize) =
          traverseAndMaybePack(f.whenZero, testSize + nzSize + 1)
        (new IfIR(testIR, nzIR, zIR), testSize + nzSize + zSize + 1)
      case _: Ref => (p, 1)
      case MethodDef(_, _) | MethodRef(_) =>
        sys.error("This should never happen")
    }

  private def traverseAndMaybePack(p: IR, parentSize: Int): (IR, Int) = {
    val (pt, size) = traverse(p)
    if ((size + parentSize) > methodSizeLimit)
      (createMethod(pt), 1)
    else
      (pt, size)
  }

  private def createMethod(rhs: IR): MethodRef = {
    val s = Sym.freshSym()
    val md = new MethodDef(s, rhs)
    methodDefs(s) = md
    MethodRef(s)
  }
}
