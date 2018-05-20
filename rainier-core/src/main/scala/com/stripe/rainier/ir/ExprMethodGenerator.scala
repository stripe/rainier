package com.stripe.rainier.ir

private case class ExprMethodGenerator(method: MethodDef,
                                       inputs: Seq[Parameter],
                                       varTypes: VarTypes,
                                       className: String)
    extends MethodGenerator {
  val isPrivate = true
  val methodName = exprMethodName(method.sym.id)
  val methodDesc = "([D[D)D"

  private val varIndices = inputs.zipWithIndex.toMap

  traverse(method.rhs)
  returnDouble()

  def traverse(ir: IR): Unit = {
    ir match {
      case Const(value) =>
        constant(value)
      case p: Parameter =>
        loadParameter(varIndices(p))
      case b: BinaryIR =>
        traverse(b.left)
        traverse(b.right)
        binaryOp(b.op)
      case u: UnaryIR =>
        traverse(u.original)
        unaryOp(u.op)
      case i: IfIR =>
        traverse(i.whenNonZero)
        traverse(i.whenZero)
        traverse(i.test)
        constant(0.0)
        swapIfEqThenPop()
      case v: VarDef =>
        varTypes(v.sym) match {
          case Inline =>
            traverse(v.rhs)
          case Local(i) =>
            traverse(v.rhs)
            storeLocalVar(i)
          case Global(i) =>
            storeGlobalVar(i) {
              traverse(v.rhs)
            }
        }
      case VarRef(sym) =>
        varTypes(sym) match {
          case Inline =>
            sys.error("Should not have references to inlined vars")
          case Local(i) =>
            loadLocalVar(i)
          case Global(i) =>
            loadGlobalVar(i)
        }
      case MethodRef(sym) =>
        callExprMethod(sym.id)
      case _: MethodDef =>
        sys.error("Should not have nested method defs")
    }
  }
}
