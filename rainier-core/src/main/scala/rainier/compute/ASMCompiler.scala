package rainier.compute

import java.io.File

import org.apache.commons.io.FileUtils
import org.objectweb.asm.{ClassVisitor, ClassWriter, tree}
import org.objectweb.asm.tree.{ClassNode, MethodNode}
import org.objectweb.asm.Opcodes._

trait ASMCompiledFunction {
  def apply(inputs: Array[Double]): Array[Double]
}

object ASMCompiler extends Compiler {
  val ClassName = "Compiled"
  val MethodName = "apply"
  var classID = 0

  class SingleClassClassLoader(name: String,
                               bytes: Array[Byte],
                               parent: ClassLoader)
      extends ClassLoader(parent) {
    lazy val clazz: Class[_] = defineClass(name, bytes, 0, bytes.length)
  }

  def compile(inputs: Seq[Variable],
              outputs: Seq[Real]): Array[Double] => Array[Double] = {
    classID += 1
    val classNode = compileClassNode(inputs, outputs)
    val bytes = writeBytecode(classNode)
    //println("writing Compiled" + classID)
    //writeBytesToFile(new File("/tmp/Compiled" + classID + ".class"), bytes)
    val parentClassloader = this.getClass.getClassLoader
    val classLoader =
      new SingleClassClassLoader(ClassName, bytes, parentClassloader)
    val cls = classLoader.clazz
    val inst = cls.newInstance().asInstanceOf[ASMCompiledFunction]
    inst.apply(_)
  }

  def compileClassNode(variables: Seq[Real],
                       targets: Seq[Real]): tree.ClassNode = {
    var numReferences = Map.empty[Real, Int]
    def incReference(real: Real): Unit = {
      val prev = numReferences.getOrElse(real, 0)
      numReferences += real -> (prev + 1)
    }

    val varIndices = variables.zipWithIndex.toMap

    def countReferences(real: Real): Unit = numReferences.get(real) match {
      case Some(n) => ()
      case None =>
        real match {
          case u: UnaryReal =>
            countReferences(u.original)
            incReference(u.original)
          case b: BinaryReal =>
            countReferences(b.left)
            countReferences(b.right)
            incReference(b.left)
            incReference(b.right)
          case _ => ()
        }
    }

    targets.foreach { target =>
      countReferences(target)
    }

    val m = new MethodNode(ASM6, //api
                           ACC_PUBLIC, //access
                           MethodName, //name
                           "([D)[D", //desc
                           null, //signature
                           Array.empty) //exceptions

    var nextID = 0
    var ids = Map.empty[Real, Int]

    def localVarSlot(id: Int) = 2 + (id * 2)

    def interpret(ast: Real): Unit = {
      val nRefs = numReferences.getOrElse(ast, 0)
      if (nRefs <= 1)
        basicInterpret(ast)
      else
        ast match {
          case Constant(_) => basicInterpret(ast)
          case _ =>
            ids.get(ast) match {
              case Some(id) =>
                m.visitVarInsn(DLOAD, localVarSlot(id))
              case None =>
                val id = nextID
                ids += ast -> id
                nextID += 1
                basicInterpret(ast)
                m.visitVarInsn(DSTORE, localVarSlot(id))
                m.visitVarInsn(DLOAD, localVarSlot(id))
            }
        }
    }

    def basicInterpret(ast: Real): Unit = ast match {
      case v: Variable =>
        m.visitVarInsn(ALOAD, 1)
        m.visitLdcInsn(varIndices(v))
        m.visitInsn(DALOAD)
      case b: BinaryReal =>
        interpret(b.left)
        interpret(b.right)
        val insn = b.op match {
          case AddOp      => DADD
          case SubtractOp => DSUB
          case MultiplyOp => DMUL
          case DivideOp   => DDIV
          case _          => ???
        }
        m.visitInsn(insn)
      case u: UnaryReal =>
        interpret(u.original)
        val methodName = u.op match {
          case LogOp => "log"
          case ExpOp => "exp"
          case _     => ???
        }
        m.visitMethodInsn(INVOKESTATIC,
                          "java/lang/Math",
                          methodName,
                          "(D)D",
                          false)
      case Constant(x) =>
        m.visitLdcInsn(x)
    }
    m.visitLdcInsn(targets.size)
    m.visitIntInsn(NEWARRAY, T_DOUBLE)
    targets.zipWithIndex.foreach {
      case (target, i) =>
        m.visitInsn(DUP)
        m.visitLdcInsn(i)
        interpret(target)
        m.visitInsn(DASTORE)
    }
    m.visitInsn(ARETURN)
    val cls = new tree.ClassNode()
    cls.visit(V1_8,
              ACC_PUBLIC | ACC_SUPER,
              ClassName,
              null,
              "java/lang/Object",
              Array("rainier/compute/ASMCompiledFunction"))
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
