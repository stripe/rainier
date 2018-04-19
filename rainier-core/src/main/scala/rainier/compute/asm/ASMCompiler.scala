package rainier.compute.asm

import rainier.compute._

object ASMCompiler extends Compiler {
  def compile(inputs: Seq[Variable],
              outputs: Seq[Real]): Array[Double] => Array[Double] = {
    val m = compileMethods(inputs, outputs)
    m.compiledClass.instance.apply(_)
  }

  private def compileMethods(variables: Seq[Real],
                             targets: Seq[Real]): MethodStack = {
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

    var nextID = 0
    var ids = Map.empty[Real, Int]

    val m = new MethodStack

    def interpret(ast: Real): Unit = {
      val nRefs = numReferences.getOrElse(ast, 0)
      if (nRefs <= 1)
        walk(ast)
      else
        ast match {
          case Constant(_) =>
            walk(ast)
          case _ =>
            ids.get(ast) match {
              case Some(id) =>
                m.loadLocalVar(id)
              case None =>
                val id = nextID
                ids += ast -> id
                nextID += 1
                walk(ast)
                m.storeLocalVar(id)
            }
        }
    }

    def walk(ast: Real): Unit = ast match {
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

    m.newArray(targets) { target =>
      interpret(target)
    }

    m.ret
    m
  }
}
