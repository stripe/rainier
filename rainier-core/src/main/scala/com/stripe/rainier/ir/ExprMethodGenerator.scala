package com.stripe.rainier.ir

final private case class ExprMethodGenerator(method: MethodDef,
                                             inputs: Seq[Parameter],
                                             varTypes: VarTypes,
                                             classPrefix: String,
                                             classSizeLimit: Int)
    extends MethodGenerator {
  val isStatic: Boolean = true
  val methodName: String = exprMethodName(method.sym.id)
  val className: String = classNameForMethod(classPrefix, method.sym.id)
  val methodDesc: String = "([D[D)D"

  private val varIndices = inputs.zipWithIndex.toMap

  traverse(method.rhs)
  returnDouble()

  def traverse(expr: Expr): Unit =
    expr match {
      case Const(value) =>
        constant(value)
      case p: Parameter =>
        loadParameter(varIndices(p))
      case v: VarDef =>
        traverseVarDef(v, true)
      case VarRef(sym) =>
        varTypes(sym) match {
          case Inline =>
            sys.error("Should not have references to inlined vars")
          case Local(i) =>
            loadLocalVar(i)
          case Global(i) =>
            loadGlobalVar(i)
        }
    }

  def traverseVarDef(v: VarDef, loadAfter: Boolean): Unit =
    varTypes(v.sym) match {
      case Inline =>
        require(loadAfter)
        traverse(v.rhs)
      case Local(i) =>
        traverse(v.rhs)
        storeLocalVar(i)
        if (loadAfter)
          loadLocalVar(i)
      case Global(i) =>
        storeGlobalVar(i) {
          traverse(v.rhs)
        }
        if (loadAfter)
          loadGlobalVar(i)
    }

  def traverseInt(expr: Expr): Unit =
    expr match {
      //special case since compare returns an int
      case VarDef(sym, BinaryIR(left, right, CompareOp))
          if (varTypes(sym) == Inline) =>
        traverse(left)
        traverse(right)
        compareDoubles()
      case _ =>
        traverse(expr)
        doubleToInt()
    }

  def traverse(ir: IR): Unit =
    ir match {
      case b: BinaryIR =>
        traverse(b.left)
        traverse(b.right)
        binaryOp(b.op)
      case u: UnaryIR =>
        traverse(u.original)
        unaryOp(u.op)
      case l: LookupIR =>
        traverseInt(l.index)
        tableSwitch(l.table, l.low) {
          case Some(r) => traverse(r)
          case None    => throwNPE()
        }
      case s: SeqIR =>
        traverseVarDef(s.first, false)
        traverseVarDef(s.second, true)
      case m: MethodRef =>
        callExprMethod(classPrefix, m.sym.id)
    }
}
