package com.stripe.rainier.ir

import com.stripe.rainier.internal.asm.Opcodes._
import com.stripe.rainier.internal.asm.tree.MethodNode
import com.stripe.rainier.internal.asm.Label

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
    val methodNameOpt = op match {
      case LogOp => Some("log")
      case ExpOp => Some("exp")
      case AbsOp => Some("abs")
      case NoOp  => None
    }
    methodNameOpt.foreach { methodName =>
      methodNode.visitMethodInsn(INVOKESTATIC,
                                 "java/lang/Math",
                                 methodName,
                                 "(D)D",
                                 false)
    }
  }

  def exprMethodName(id: Int): String = s"_$id"
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
  0: this
  1: params array
  2: globals array
  3: output array OR 3..N: locally allocated doubles (two slots each)
  **/
  def loadThis(): Unit =
    methodNode.visitVarInsn(ALOAD, 0)

  def loadParams(): Unit =
    methodNode.visitVarInsn(ALOAD, 1)

  def loadGlobalVars(): Unit =
    methodNode.visitVarInsn(ALOAD, 2)

  def loadOutputs(): Unit =
    methodNode.visitVarInsn(ALOAD, 3)

  private def localVarSlot(pos: Int) = 3 + (pos * 2)
}
