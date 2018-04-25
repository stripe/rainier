package rainier.compute.compiler

import org.objectweb.asm.Opcodes._
import org.objectweb.asm.tree.MethodNode
import rainier.compute._

private trait MethodGenerator {
  lazy val methodNode: MethodNode =
    new MethodNode(ASM6, //api
                   if (isPrivate) ACC_PRIVATE else ACC_PUBLIC, //access
                   methodName, //name
                   methodDesc, //desc
                   null, //signature
                   Array.empty) //exceptions

  def methodName: String
  def methodDesc: String
  def isPrivate: Boolean
  def className: String

  def loadLocalVar(pos: Int): Unit =
    methodNode.visitVarInsn(DLOAD, localVarSlot(pos))

  def storeLocalVar(pos: Int): Unit = {
    methodNode.visitVarInsn(DSTORE, localVarSlot(pos))
    loadLocalVar(pos)
  }

  def loadGlobalVar(pos: Int): Unit = {
    loadGlobalVars()
    methodNode.visitLdcInsn(pos)
    methodNode.visitInsn(DALOAD)
  }

  def storeGlobalVar(pos: Int)(fn: => Unit): Unit = {
    loadGlobalVars()
    methodNode.visitLdcInsn(pos)
    fn
    methodNode.visitInsn(DASTORE)
    loadGlobalVar(pos)
  }

  def loadParameter(pos: Int): Unit = {
    loadParams()
    methodNode.visitLdcInsn(pos)
    methodNode.visitInsn(DALOAD)
  }

  def binaryOp(op: BinaryOp): Unit = {
    val insn = op match {
      case AddOp      => DADD
      case SubtractOp => DSUB
      case MultiplyOp => DMUL
      case DivideOp   => DDIV
      case _          => ???
    }
    methodNode.visitInsn(insn)
  }

  def unaryOp(op: UnaryOp): Unit = {
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

  def exprMethodName(id: Int) = s"_$id"
  def callExprMethod(id: Int): Unit = {
    loadThis()
    loadParams()
    loadGlobalVars()
    methodNode.visitMethodInsn(INVOKESPECIAL,
                               className,
                               exprMethodName(id),
                               "([D[D)D",
                               false)
  }

  def returnArray(): Unit =
    methodNode.visitInsn(ARETURN)

  def returnDouble(): Unit =
    methodNode.visitInsn(DRETURN)

  def constant(value: Double): Unit =
    methodNode.visitLdcInsn(value)

  def newArrayOfSize(size: Int): Unit = {
    methodNode.visitLdcInsn(size)
    methodNode.visitIntInsn(NEWARRAY, T_DOUBLE)
  }

  def newArray[T](values: Seq[T])(fn: T => Unit): Unit = {
    newArrayOfSize(values.size)
    values.zipWithIndex.foreach {
      case (v, i) =>
        methodNode.visitInsn(DUP)
        methodNode.visitLdcInsn(i)
        fn(v)
        methodNode.visitInsn(DASTORE)
    }
  }

  /**
  The local var layout is assumed to be:
  0: this
  1: params array (always passed in)
  2: globals array (either passed in or locally allocated)
  3..N: locally allocated doubles (two slots each)
  **/
  def loadThis(): Unit =
    methodNode.visitVarInsn(ALOAD, 0)

  def loadParams(): Unit =
    methodNode.visitVarInsn(ALOAD, 1)

  def loadGlobalVars(): Unit =
    methodNode.visitVarInsn(ALOAD, 2)

  def storeGlobalVars(): Unit =
    methodNode.visitVarInsn(ASTORE, 2)

  private def localVarSlot(pos: Int) = 3 + (pos * 2)
}
