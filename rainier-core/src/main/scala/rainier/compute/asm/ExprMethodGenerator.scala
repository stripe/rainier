package rainier.compute.asm

import rainier.compute._

private case class ExprMethodGenerator(method: MethodDef,
                                       inputs: Seq[Variable],
                                       locals: Seq[Sym],
                                       globals: Seq[Sym],
                                       className: String)
    extends MethodGenerator {
  val isPrivate = true
  val methodName = exprMethodName(method.sym.id)
  val methodDesc = "([D[D])D"

  private val varIndices = inputs.zipWithIndex.toMap
  private val localIndices = locals.zipWithIndex.toMap
  private val globalIndices = globals.zipWithIndex.toMap

  traverse(method.rhs)
  ret()

  //could almost use ForEachTraverse here but the operand ordering for
  //array stores makes that not really work
  def traverse(ir: IR): Unit = {
    ir match {
      case Const(value) =>
        constant(value)
      case Parameter(variable) =>
        loadParameter(varIndices(variable))
      case BinaryIR(left, right, op) =>
        traverse(left)
        traverse(right)
        binaryOp(op)
      case UnaryIR(original, op) =>
        traverse(original)
        unaryOp(op)
      case VarDef(sym, rhs) =>
        localIndices.get(sym) match {
          case Some(i) =>
            traverse(rhs)
            storeLocalVar(i)
          case None =>
            storeGlobalVar(globalIndices(sym)) {
              traverse(rhs)
            }
        }
      case VarRef(sym) =>
        localIndices.get(sym) match {
          case Some(i) => loadLocalVar(i)
          case None    => loadGlobalVar(globalIndices(sym))
        }
      case MethodRef(sym) =>
        callExprMethod(sym.id)
      case MethodDef(sym, rhs) =>
        sys.error("Should not have nested method defs")
    }
  }
}
