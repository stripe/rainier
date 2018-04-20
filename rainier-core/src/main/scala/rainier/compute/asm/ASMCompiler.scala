package rainier.compute.asm

import rainier.compute._

object ASMCompiler extends Compiler {
  def compile(inputs: Seq[Variable],
              outputs: Seq[Real]): Array[Double] => Array[Double] = {
    val m = compileMethods(inputs, outputs)
    val c = m.compiledClass
    c.writeToTmpFile
    c.instance.apply(_)
  }

  private def compileMethods(inputs: Seq[Variable],
                             outputs: Seq[Real]): MethodStack = {
    val locals = new Locals(outputs)
    val m = new MethodStack
    val varIndices = inputs.zipWithIndex.toMap

    def interpret(real: Real): Unit =
      locals.find(real) match {
        case Some((id, true)) =>
          walk(real)
          m.storeLocalVar(id)
        case Some((id, false)) =>
          m.loadLocalVar(id)
        case None =>
          walk(real)
      }

    def walk(real: Real): Unit = real match {
      case v: Variable =>
        m.loadParameter(varIndices(v))
      case b: BinaryReal =>
        interpret(b.left)
        interpret(b.right)
        m.binaryOp(b.op)
      case u: UnaryReal =>
        interpret(u.original)
        m.unaryOp(u.op)
      case Constant(v) => m.constant(v)
    }

    m.newArray(outputs) { target =>
      interpret(target)
    }

    m.ret
    m
  }
}
