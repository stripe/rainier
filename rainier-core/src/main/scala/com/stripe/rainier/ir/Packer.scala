package com.stripe.rainier.ir

private class Packer(methodSizeLimit: Int) {
  private var methodDefs: List[MethodDef] = Nil
  def methods = methodDefs

  def pack(p: Expr): MethodRef = {
    val (pExpr, _) = traverse(p, 0)
    createMethod(UnaryIR(pExpr, NoOp))
  }

  private def traverse(p: Expr, parentSize: Int): (Expr, Int) =
    p match {
      case v: VarDef => traverseVarDef(v, parentSize)
      case _: Ref    => (p, 1)
    }

  private def traverseVarDef(v: VarDef, parentSize: Int): (VarDef, Int) = {
    val (ir, irSize) = traverseIR(v.rhs)
    val (newIR, newSize) =
      if ((irSize + parentSize) > methodSizeLimit)
        (createMethod(ir), 1)
      else
        (ir, irSize)
    (new VarDef(v.sym, newIR), newSize + 1)
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
      case l: LookupIR =>
        val (indexExpr, indexSize) =
          traverse(l.index, l.table.size)
        (new LookupIR(indexExpr, l.table), indexSize + l.table.size)
      case s: SeqIR =>
        val (firstDef, firstSize) =
          traverseVarDef(s.first, 1)
        val (secondDef, secondSize) =
          traverseVarDef(s.second, firstSize + 1)
        (SeqIR(firstDef, secondDef), firstSize + secondSize + 1)
      case _: MethodRef =>
        sys.error("there shouldn't be any method refs yet")
    }

  private def createMethod(rhs: IR): MethodRef = {
    val s = Sym.freshSym()
    val md = new MethodDef(s, rhs)
    methodDefs = md :: methodDefs
    MethodRef(s)
  }
}
