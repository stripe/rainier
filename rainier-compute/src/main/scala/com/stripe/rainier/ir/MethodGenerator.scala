package com.stripe.rainier.ir

import com.stripe.rainier.internal.asm.Opcodes._
import com.stripe.rainier.internal.asm.tree.MethodNode
import com.stripe.rainier.internal.asm.Label

private trait MethodGenerator {
  def access = {
    if (isStatic)
      ACC_STATIC | ACC_PUBLIC | ACC_FINAL
    else
      ACC_PUBLIC | ACC_FINAL
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
  def isStatic: Boolean
  def classSizeLimit: Int

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

  def binaryOp(op: BinaryOp, keepInt: Boolean = false): Unit =
    op match {
      case AddOp      => methodNode.visitInsn(DADD)
      case SubtractOp => methodNode.visitInsn(DSUB)
      case MultiplyOp => methodNode.visitInsn(DMUL)
      case DivideOp   => methodNode.visitInsn(DDIV)
      case CompareOp =>
        methodNode.visitInsn(DCMPL)
        if (!keepInt)
          methodNode.visitInsn(I2D)
      case PowOp =>
        methodNode.visitMethodInsn(INVOKESTATIC,
                                   "java/lang/Math",
                                   "pow",
                                   "(DD)D",
                                   false)
    }

  def unaryOp(op: UnaryOp): Unit = {
    (op match {
      case LogOp  => Some(("java/lang/Math", "log"))
      case ExpOp  => Some(("java/lang/Math", "exp"))
      case AbsOp  => Some(("java/lang/Math", "abs"))
      case CosOp  => Some(("java/lang/Math", "cos"))
      case SinOp  => Some(("java/lang/Math", "sin"))
      case TanOp  => Some(("java/lang/Math", "tan"))
      case AsinOp => Some(("java/lang/Math", "asin"))
      case AcosOp => Some(("java/lang/Math", "acos"))
      case AtanOp => Some(("java/lang/Math", "atan"))
      case NoOp   => None
    }).foreach {
      case (className, methodName) =>
        methodNode.visitMethodInsn(INVOKESTATIC,
                                   className,
                                   methodName,
                                   "(D)D",
                                   false)
    }
  }

  def classNameForMethod(classPrefix: String, id: Int): String = {
    val n = id / classSizeLimit
    if (n > 0)
      s"classPrefix$$$n"
    else
      classPrefix
  }

  def exprMethodName(id: Int): String = s"_$id"
  def callExprMethod(classPrefix: String, id: Int): Unit = {
    loadParams()
    loadGlobalVars()
    methodNode.visitMethodInsn(INVOKESTATIC,
                               classNameForMethod(classPrefix, id),
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

  def doubleToInt(): Unit = {
    methodNode.visitInsn(D2I)
  }

  def pop(): Unit = {
    methodNode.visitInsn(POP2)
  }

  def tableSwitch[K](items: Seq[K], low: Int)(fn: Option[K] => Unit): Unit = {
    val defaultLabel = new Label
    val endLabel = new Label
    val itemsAndLabels = items.map { k =>
      k -> (new Label)
    }
    val labels = itemsAndLabels.map(_._2)
    methodNode.visitTableSwitchInsn(low,
                                    low + items.size - 1,
                                    defaultLabel,
                                    labels: _*)
    itemsAndLabels.foreach {
      case (k, l) =>
        methodNode.visitLabel(l)
        fn(Some(k))
        methodNode.visitJumpInsn(GOTO, endLabel)
    }
    methodNode.visitLabel(defaultLabel)
    fn(None)
    methodNode.visitLabel(endLabel)
  }

  def throwNPE(): Unit = {
    methodNode.visitInsn(ACONST_NULL)
    methodNode.visitInsn(ATHROW)
  }

  /**
  The local var layout is assumed to be:
  For static methods:
  0: params array
  1: globals array
  2..N: locally allocated doubles (two slots each)

  for output():
  0: this
  1: params array
  2: globals array
  3: output index
  **/
  def loadParams(): Unit =
    methodNode.visitVarInsn(ALOAD, if (isStatic) 0 else 1)

  def loadGlobalVars(): Unit =
    methodNode.visitVarInsn(ALOAD, if (isStatic) 1 else 2)

  private def localVarSlot(pos: Int) = 2 + (pos * 2)

  def loadThis(): Unit =
    methodNode.visitVarInsn(ALOAD, 0)

  def loadOutputIndex(): Unit =
    methodNode.visitVarInsn(ILOAD, 3)
}
