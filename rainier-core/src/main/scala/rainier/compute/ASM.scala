package rainier.compute

import java.io.File

import org.apache.commons.io.FileUtils
import org.objectweb.asm.{ClassVisitor, ClassWriter, tree}
import org.objectweb.asm.tree.{ClassNode, MethodNode}
import org.objectweb.asm.Opcodes._

object ASM {
  class SingleClassClassLoader(name: String,
                               bytes: Array[Byte],
                               parent: ClassLoader)
      extends ClassLoader(parent) {
    lazy val clazz: Class[_] = defineClass(name, bytes, 0, bytes.length)
  }

  def compileToFunction(program: Real): Double => Double = {
    val classNode = compile(program)
    val bytes = writeBytecode(classNode)
    val parentClassloader = this.getClass.getClassLoader
    val classLoader =
      new SingleClassClassLoader("Foo", bytes, parentClassloader)
    val cls = classLoader.clazz
    val inst = cls.newInstance()
    val method = cls.getMethod("foo_method", classOf[Double])
    val result = { x: Double =>
      method
        .invoke(inst, new java.lang.Double(x))
        .asInstanceOf[Double]
    }
    result
  }

  def compile(program: Real): tree.ClassNode = {
    var numReferences = Map.empty[Real, Int]
    def incReference(real: Real): Unit = {
      val prev = numReferences.getOrElse(real, 0)
      numReferences += real -> (prev + 1)
    }

    def countReferences(real: Real): Unit = numReferences.get(real) match {
      case Some(n) => ()
      case None =>
        real match {
          case u: UnaryReal =>
            incReference(u.original)
            countReferences(u.original)
          case b: BinaryReal =>
            incReference(b.left)
            incReference(b.right)
            countReferences(b.left)
            countReferences(b.right)
          case _ => ()
        }
    }

    countReferences(program)

    // public MethodNode(api: Int, access: Int, name: String, desc: String, signature: String, exceptions: Array[String])
    //
    val m = new MethodNode(ASM6,
                           ACC_PUBLIC + ACC_STATIC,
                           "foo_method",
                           "(D)D",
                           null,
                           Array.empty)

    var nextID = 0
    var ids = Map.empty[Real, Int]

    def localVarSlot(id: Int) = 2 + (id * 2)

    def interpret(ast: Real): Unit = {
      val nRefs = numReferences.getOrElse(ast, 0)
      if (nRefs <= 1)
        basicInterpret(ast)
      else {
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
        val pos = 0 //only one param allowed for now
        // double occupies two slots local variable table
        m.visitVarInsn(DLOAD, pos * 2)
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
