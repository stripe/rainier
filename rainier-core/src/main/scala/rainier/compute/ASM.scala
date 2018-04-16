package rainier.compute

sealed trait AST
case class Const(x: Double) extends AST
case class ParamRef(pos: Int) extends AST
case class Plus(x1: AST, x2: AST) extends AST
case class Minus(x1: AST, x2: AST) extends AST
case class Multiply(x1: AST, x2: AST) extends AST
case class Divide(x1: AST, x2: AST) extends AST
case class Exp(x: AST) extends AST
case class Log(x: AST) extends AST

import java.io.File

import org.apache.commons.io.FileUtils
import org.objectweb.asm.{ClassVisitor, ClassWriter, tree}
import org.objectweb.asm.tree.{ClassNode, MethodNode}
import org.objectweb.asm.Opcodes._

object ASM {

  def compile(program: AST): tree.ClassNode = {
    // public MethodNode(api: Int, access: Int, name: String, desc: String, signature: String, exceptions: Array[String])
    //
    val m = new MethodNode(ASM6,
                           ACC_PUBLIC + ACC_STATIC,
                           "foo_method",
                           "(DD)D",
                           null,
                           Array.empty)
    def interpret(ast: AST): Unit = ast match {
      case ParamRef(pos) =>
        // double occupies two slots local variable table
        m.visitVarInsn(DLOAD, pos * 2)
      case Plus(x1, x2) =>
        interpret(x1)
        interpret(x2)
        m.visitInsn(DADD)
      case Minus(x1, x2) =>
        interpret(x1)
        interpret(x2)
        m.visitInsn(DSUB)
      case Multiply(x1, x2) =>
        interpret(x1)
        interpret(x2)
        m.visitInsn(DMUL)
      case Divide(x1, x2) =>
        interpret(x1)
        interpret(x2)
        m.visitInsn(DDIV)
      case Exp(x) =>
        interpret(x)
        m.visitMethodInsn(INVOKESTATIC, "java/lang/Math", "exp", "(D)D", false)
      case Log(x) =>
        interpret(x)
        m.visitMethodInsn(INVOKESTATIC, "java/lang/Math", "log", "(D)D", false)
      case Const(x) =>
        m.visitLdcInsn(x)
    }
    interpret(program)
    m.visitInsn(DRETURN)
    val cls = new tree.ClassNode()
    cls.visit(V1_8,
              ACC_PUBLIC | ACC_SUPER,
              "Foo",
              null,
              "java/lang/Object",
              null)
    cls.methods.add(createInit)
    cls.methods.add(m)
    cls
  }

  def createInit: MethodNode = {
    val m = new MethodNode(ACC_PUBLIC, "<init>", "()V", null, null)
    m.visitCode()
    m.visitVarInsn(ALOAD, 0)
    m.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
    m.visitInsn(RETURN)
    m.visitMaxs(1, 1)
    m.visitEnd()
    m
  }

  def writeBytecode(classNode: ClassNode): Array[Byte] = {
    val cw = new ClassWriter(
      ClassWriter.COMPUTE_FRAMES + ClassWriter.COMPUTE_MAXS)
    classNode.accept(cw)
    cw.toByteArray
  }

  def writeBytesToFile(where: File, bytes: Array[Byte]): Unit = {
    FileUtils.writeByteArrayToFile(where, bytes)
  }

}

class SingleClassClassLoader(name: String,
                             bytes: Array[Byte],
                             parent: ClassLoader)
    extends ClassLoader(parent) {
  lazy val clazz: Class[_] = defineClass(name, bytes, 0, bytes.length)
}
