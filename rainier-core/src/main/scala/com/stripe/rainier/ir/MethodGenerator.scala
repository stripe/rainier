package com.stripe.rainier.ir

import com.stripe.rainier.internal.asm.Opcodes._
import com.stripe.rainier.internal.asm.tree.MethodNode
import com.stripe.rainier.internal.asm.Label

private trait MethodGenerator {
  def access = {
    val priv = if (isPrivate) ACC_PRIVATE else ACC_PUBLIC
    if (isStatic)
      priv | ACC_STATIC | ACC_FINAL
    else
      priv | ACC_FINAL
  }

  lazy val methodNode: MethodNode =
    new MethodNode(ASM6,
                   access,
                   methodName,
                   methodDesc,
                   null, //signature
                   Array.empty) //exceptions

  def methodName: String
  def methodDesc: String
  def isPrivate: Boolean
  def isStatic: Boolean
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

  def storeOutput(pos: Int)(fn: => Unit): Unit = {
    loadOutputs()
    methodNode.visitLdcInsn(pos)
    fn
    methodNode.visitInsn(DASTORE)
  }

  def loadParameter(pos: Int): Unit = {
    loadParams()
    methodNode.visitLdcInsn(pos)
    methodNode.visitInsn(DALOAD)
  }

  def binaryOp(op: BinaryOp): Unit =
    op match {
      case AddOp      => methodNode.visitInsn(DADD)
      case SubtractOp => methodNode.visitInsn(DSUB)
      case MultiplyOp => methodNode.visitInsn(DMUL)
      case DivideOp   => methodNode.visitInsn(DDIV)
      case PowOp =>
        methodNode.visitMethodInsn(INVOKESTATIC,
                                   "java/lang/Math",
                                   "pow",
                                   "(DD)D",
                                   false)
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

  def exprMethodName(id: Int): String = s"_$id"
  def callExprMethod(id: Int): Unit = {
    loadParams()
    loadGlobalVars()
    methodNode.visitMethodInsn(INVOKESTATIC,
                               className,
                               exprMethodName(id),
                               "([D[D)D",
                               false)
  }

  def returnVoid(): Unit =
    methodNode.visitInsn(RETURN)

  def returnDouble(): Unit =
    methodNode.visitInsn(DRETURN)

  def constant(value: Double): Unit =
    methodNode.visitLdcInsn(value)

  def swapIfEqThenPop(): Unit = {
    val label = new Label
    methodNode.visitInsn(DCMPL)
    methodNode.visitJumpInsn(IFNE, label)
    methodNode.visitInsn(DUP2_X2)
    methodNode.visitInsn(POP2)
    methodNode.visitLabel(label)
    methodNode.visitInsn(POP2)
  }

  /**
  The local var layout is assumed to be:
  For static methods:
  0: params array
  1: globals array
  2..N: locally allocated doubles (two slots each)

  Otherwise (eg apply):
  0: this
  1: params array
  2: globals array
  3: output array
  **/
  def loadParams(): Unit =
    methodNode.visitVarInsn(ALOAD, if (isStatic) 0 else 1)

  def loadGlobalVars(): Unit =
    methodNode.visitVarInsn(ALOAD, if (isStatic) 1 else 2)

  private def localVarSlot(pos: Int) = 2 + (pos * 2)

  def loadThis(): Unit =
    methodNode.visitVarInsn(ALOAD, 0)

  def loadOutputs(): Unit =
    methodNode.visitVarInsn(ALOAD, 3)
}
