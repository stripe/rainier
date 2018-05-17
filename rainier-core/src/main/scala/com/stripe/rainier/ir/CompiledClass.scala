package com.stripe.rainier.ir

import java.io.File
import org.apache.commons.io.FileUtils

import com.stripe.rainier.internal.asm.Opcodes._
import com.stripe.rainier.internal.asm.tree.{ClassNode, MethodNode}
import com.stripe.rainier.internal.asm.ClassWriter

private[ir] trait CompiledFunction {
  def apply(inputs: Array[Double]): Array[Double]
}

private[ir] class CompiledClass(name: String, methods: Seq[MethodNode]) {

  val classNode = createClass
  val bytes = writeBytecode
  lazy val instance = createInstance

  def writeToTmpFile(): Unit =
    FileUtils.writeByteArrayToFile(new File("/tmp/" + name + ".class"), bytes)

  private def createClass: ClassNode = {
    val cls = new ClassNode()
    cls.visit(V1_5,
              ACC_PUBLIC | ACC_SUPER,
              name,
              null,
              "java/lang/Object",
              Array("com/stripe/rainier/ir/CompiledFunction"))
    cls.methods.add(createInit)
    methods.foreach { m =>
      cls.methods.add(m)
    }
    cls
  }

  private def createInit: MethodNode = {
    val m = new MethodNode(ACC_PUBLIC, "<init>", "()V", null, null)
    m.visitCode()
    m.visitVarInsn(ALOAD, 0)
    m.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
    m.visitInsn(RETURN)
    m.visitMaxs(1, 1)
    m.visitEnd()
    m
  }

  private def writeBytecode: Array[Byte] = {
    val cw = new ClassWriter(
      ClassWriter.COMPUTE_FRAMES + ClassWriter.COMPUTE_MAXS)
    classNode.accept(cw)
    cw.toByteArray
  }

  private def createInstance: CompiledFunction = {
    val parentClassloader = this.getClass.getClassLoader
    val classLoader =
      new SingleClassLoader(name, bytes, parentClassloader)
    val cls = classLoader.clazz
    cls.newInstance().asInstanceOf[CompiledFunction]
  }
}

private[ir] object CompiledClass {
  @volatile private var id: Int = 0
  def freshName: String = this.synchronized {
    val name = "CompiledFunction$" + id
    id += 1
    name
  }

  def methods(seq: Seq[MethodNode]): CompiledClass =
    new CompiledClass(freshName, seq)
}

private class SingleClassLoader(name: String,
                                bytes: Array[Byte],
                                parent: ClassLoader)
    extends ClassLoader(parent) {
  lazy val clazz: Class[_] = defineClass(name, bytes, 0, bytes.length)
}
