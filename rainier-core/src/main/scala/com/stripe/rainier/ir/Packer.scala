package com.stripe.rainier.ir

import scala.collection.mutable

private class Packer(methodSizeLimit: Int) {
  private val methodDefs: mutable.Map[Sym, MethodDef] = mutable.Map.empty
  def methods: Set[MethodDef] = methodDefs.values.toSet

  def pack(p: Expr): MethodRef = {
    val (pExpr, _) = traverse(p, 0)
    createMethod(UnaryIR(pExpr, NoOp))
  }

  private def traverse(p: Expr, parentSize: Int): (Expr, Int) =
    p match {
      case v: VarDef =>
        val (ir, irSize) =
          traverseAndMaybePack(v.rhs, parentSize: Int)
        (new VarDef(v.sym, ir), irSize + 1)
      case _: Ref => (p, 1)
    }

  private def traverseVarDef(vd: VarDef, parentSize: Int): (IR, Int) = {
    val (rhsIR, rhsSize) =
      traverseAndMaybePack(v.rhs, parentSize)
    (new VarDef(v.sym, rhsIR), rhsSize + 1)
  }

  private def traverseAndMaybePack(p: IR, parentSize: Int): (IR, Int) = {
    val (ir, irSize) = traverseIR(p)
    if ((irSize + parentSize) > methodSizeLimit)
      (createMethod(ir), 1)
    else
      (ir, irSize)
  }

  private def traverseIR(p: IR): (IR, Int) =
    p match {
      case b: BinaryIR =>
        val (leftExpr, leftSize) =
          traverse(b.left, 2)
        val (rightExpr, rightSize) =
          traverse(b.right, leftSize + 1)
        (new BinaryIR(leftExpr, rightExpr, b.op), leftSize + rightSize + 1)
      case u: UnaryIR =>
        val (expr, exprSize) =
          traverse(u.original, 1)
        (new UnaryIR(expr, u.op), exprSize + 1)
      case f: IfIR =>
        val (testExpr, testSize) =
          traverse(f.test, 3)
        val (nzExpr, nzSize) =
          traverse(f.whenNonZero, testSize + 2)
        val (zExpr, zSize) =
          traverse(f.whenZero, testSize + nzSize + 1)
        (new IfIR(testExpr, nzExpr, zExpr), testSize + nzSize + zSize + 1)
      case _: MethodRef =>
        sys.error("there shouldn't be any method refs yet")
    }

  private def createMethod(rhs: IR): MethodRef = {
    val s = Sym.freshSym()
    val md = new MethodDef(s, rhs)
    methodDefs(s) = md
    MethodRef(s)
  }
}
