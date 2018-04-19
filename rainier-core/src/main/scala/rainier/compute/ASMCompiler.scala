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
  def compile(inputs: Seq[Variable],
              outputs: Seq[Real]): Array[Double] => Array[Double] = {
    val methods = compileMethods(inputs, outputs)
    val cls = ASMClass.methods(methods)
    cls.instance.apply(_)
  }

  def compileMethods(variables: Seq[Real],
                     targets: Seq[Real]): Seq[MethodNode] = {
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
                           "apply", //name
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

    List(m)
  }
}

class ASMClass(name: String, methods: Seq[MethodNode]) {
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
              Array("rainier/compute/ASMCompiledFunction"))
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

  private def createInstance: ASMCompiledFunction = {
    val parentClassloader = this.getClass.getClassLoader
    val classLoader =
      new ASMClassLoader(name, bytes, parentClassloader)
    val cls = classLoader.clazz
    cls.newInstance().asInstanceOf[ASMCompiledFunction]
  }
}

object ASMClass {
  private var id = 0
  def methods(seq: Seq[MethodNode]): ASMClass = {
    id += 1
    new ASMClass("ASMCompiledFunction$" + id, seq)
  }
}

class ASMClassLoader(name: String, bytes: Array[Byte], parent: ClassLoader)
    extends ClassLoader(parent) {
  lazy val clazz: Class[_] = defineClass(name, bytes, 0, bytes.length)
}
