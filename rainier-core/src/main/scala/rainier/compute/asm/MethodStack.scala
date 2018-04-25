package rainier.compute.asm

import rainier.compute._
import org.objectweb.asm.Opcodes._
import org.objectweb.asm.tree.MethodNode

private class MethodStack {

  //in anticipation of a world where we split methods up
  var methods = List(createMethod)

  def compiledClass = CompiledClass.methods(methods)

  def loadLocalVar(id: Int): Unit =
    methods.head.visitVarInsn(DLOAD, localVarSlot(id))

  def storeLocalVar(id: Int): Unit = {
    methods.head.visitVarInsn(DSTORE, localVarSlot(id))
    loadLocalVar(id)
  }

  def loadParameter(pos: Int): Unit = {
    methods.head.visitVarInsn(ALOAD, 1)
    methods.head.visitLdcInsn(pos)
    methods.head.visitInsn(DALOAD)
  }

  def binaryOp(op: BinaryOp): Unit = {
    val insn = op match {
      case AddOp      => DADD
      case SubtractOp => DSUB
      case MultiplyOp => DMUL
      case DivideOp   => DDIV
      case _          => ???
    }
    methods.head.visitInsn(insn)
  }

  def unaryOp(op: UnaryOp): Unit = {
    val methodName = op match {
      case LogOp => "log"
      case ExpOp => "exp"
      case _     => ???
    }
    methods.head.visitMethodInsn(INVOKESTATIC,
                                 "java/lang/Math",
                                 methodName,
                                 "(D)D",
                                 false)
  }

  def newArray[T](values: Seq[T])(fn: T => Unit): Unit = {
    methods.head.visitLdcInsn(values.size)
    methods.head.visitIntInsn(NEWARRAY, T_DOUBLE)
    values.zipWithIndex.foreach {
      case (v, i) =>
        methods.head.visitInsn(DUP)
        methods.head.visitLdcInsn(i)
        fn(v)
        methods.head.visitInsn(DASTORE)
    }
  }

  def ret: Unit =
    methods.head.visitInsn(ARETURN)

  def constant(value: Double): Unit =
    methods.head.visitLdcInsn(value)

  private def localVarSlot(id: Int) = 2 + (id * 2)

  private def createMethod: MethodNode =
    new MethodNode(ASM6, //api
                   ACC_PUBLIC, //access
                   "apply", //name
                   "([D)[D", //desc
                   null, //signature
                   Array.empty) //exceptions
}
