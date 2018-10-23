package com.stripe.rainier.ir

final private case class ExprMethodGenerator(method: MethodDef,
                                             inputs: Seq[Parameter],
                                             varTypes: VarTypes,
                                             classPrefix: String,
                                             classSizeLimit: Int)
    extends MethodGenerator {
  val isStatic: Boolean = true
  val methodName: String = exprMethodName(method.sym.id)
  val className: String = classNameForMethod(method.sym.id)
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
    }

  def traverse(ir: IR): Unit =
    ir match {
      case s: SumIR =>
        s.exprs.foreach(traverse)
        binaryOp(AddOp)
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
      case l: LookupIR =>
        traverse(l.index)
        tableSwitch(l.table) {
          case Some(r) => traverse(r)
          case None    => throwNPE()
        }
      case s: SeqIR =>
        traverse(s.first)
        pop()
        traverse(s.second)
      case m: MethodRef =>
        callExprMethod(m.sym.id)
    }
}
