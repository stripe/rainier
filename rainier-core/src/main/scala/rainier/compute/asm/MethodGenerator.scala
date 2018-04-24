package rainier.compute.asm

import rainier.compute._
import org.objectweb.asm.Opcodes._
import org.objectweb.asm.tree.MethodNode

private class MethodGenerator(method: MethodDef,
                              inputs: Seq[Variable],
                              locals: Seq[Sym],
                              globals: Seq[Sym],
                              className: String) {
  val methodNode =
    new MethodNode(ASM6, //api
                   ACC_PUBLIC, //access
                   methodName(method.sym.id), //name
                   "([D[D])D", //desc
                   null, //signature
                   Array.empty) //exceptions

  private val varIndices = inputs.zipWithIndex.toMap
  private val localIndices = locals.zipWithIndex.toMap
  private val globalIndices = globals.zipWithIndex.toMap

  traverse(method.rhs)
  ret

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
        callMethod(sym.id)
      case MethodDef(sym, rhs) =>
        sys.error("Should not have nested method defs")
    }
  }

  private def loadLocalVar(id: Int): Unit =
    methodNode.visitVarInsn(DLOAD, localVarSlot(id))

  private def storeLocalVar(id: Int): Unit = {
    methodNode.visitVarInsn(DSTORE, localVarSlot(id))
    loadLocalVar(id)
  }

  private def loadGlobalVar(pos: Int): Unit = {
    methodNode.visitVarInsn(ALOAD, globalVarSlot)
    methodNode.visitLdcInsn(pos)
    methodNode.visitInsn(DALOAD)
  }

  private def storeGlobalVar(pos: Int)(fn: => Unit): Unit = {
    methodNode.visitVarInsn(ALOAD, globalVarSlot)
    methodNode.visitLdcInsn(pos)
    fn
    methodNode.visitInsn(DASTORE)
  }

  private def loadParameter(pos: Int): Unit = {
    methodNode.visitVarInsn(ALOAD, paramSlot)
    methodNode.visitLdcInsn(pos)
    methodNode.visitInsn(DALOAD)
  }

  private def binaryOp(op: BinaryOp): Unit = {
    val insn = op match {
      case AddOp      => DADD
      case SubtractOp => DSUB
      case MultiplyOp => DMUL
      case DivideOp   => DDIV
      case _          => ???
    }
    methodNode.visitInsn(insn)
  }

  private def unaryOp(op: UnaryOp): Unit = {
    val methodName = op match {
      case LogOp => "log"
      case ExpOp => "exp"
      case AbsOp => "abs"
    }
    methodNode.visitMethodInsn(INVOKESTATIC,
                               "java/lang/Math",
                               methodName,
                               "(D)D",
                               false)
  }

  private def callMethod(id: Int): Unit = {
    methodNode.visitVarInsn(ALOAD, thisSlot)
    methodNode.visitVarInsn(ALOAD, paramSlot)
    methodNode.visitVarInsn(ALOAD, globalVarSlot)
    methodNode.visitMethodInsn(INVOKESPECIAL,
                               className,
                               methodName(id),
                               "([D[D)D",
                               false)
  }

  private def ret: Unit =
    methodNode.visitInsn(ARETURN)

  private def constant(value: Double): Unit =
    methodNode.visitLdcInsn(value)

  private val thisSlot = 0
  private val paramSlot = 1
  private val globalVarSlot = 2
  private def localVarSlot(id: Int) = 3 + (id * 2)

  private def methodName(id: Int) = s"_$id"
}

object MethodGenerator {
  def generate(method: MethodDef,
               inputs: Seq[Variable],
               locals: Seq[Sym],
               globals: Seq[Sym],
               className: String): MethodNode = {
    val mg = new MethodGenerator(method, inputs, locals, globals, className)
    mg.methodNode
  }
}
