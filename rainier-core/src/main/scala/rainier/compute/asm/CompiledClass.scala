package rainier.compute.asm

import org.objectweb.asm.Opcodes._
import java.io.File
import org.apache.commons.io.FileUtils
import org.objectweb.asm.tree.{ClassNode, MethodNode}
import org.objectweb.asm.ClassWriter

private trait CompiledFunction {
  def apply(inputs: Array[Double]): Array[Double]
}

private class CompiledClass(name: String, methods: Seq[MethodNode]) {

  val classNode = createClass
  val bytes = writeBytecode
  val instance = createInstance

  def writeToTmpFile: Unit =
    FileUtils.writeByteArrayToFile(new File("/tmp/" + name + ".class"), bytes)

  private def createClass: ClassNode = {
    val cls = new ClassNode()
    cls.visit(V1_8,
              ACC_PUBLIC | ACC_SUPER,
              name,
              null,
              "java/lang/Object",
              Array("rainier/compute/asm/CompiledFunction"))
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

private object CompiledClass {
  private var id = 0
  def freshName: String = {
    id += 1
    "CompiledFunction$" + id
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
