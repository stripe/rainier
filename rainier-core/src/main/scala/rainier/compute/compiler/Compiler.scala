package rainier.compute.compiler

import rainier.compute._

object Compiler {
  def compile(inputs: Seq[Variable], output: Real): Array[Double] => Double =
    compile(inputs, List(output)).andThen { array =>
      array(0)
    }

  def compileGradient(inputs: Seq[Variable],
                      output: Real): Array[Double] => (Double, Array[Double]) =
    compile(inputs, output :: Gradient.derive(inputs, output).toList).andThen {
      array =>
        (array.head, array.tail)
    }

  def compile(inputs: Seq[Variable],
              outputs: Seq[Real]): Array[Double] => Array[Double] = {
    val className = CompiledClass.freshName
    val translator = new Translator
    val irs = outputs.map { real =>
      translator.toIR(real)
    }
    val packer = new Packer(200)
    val outputMeths = irs.map { ir =>
      packer.pack(ir)
    }
    val allMeths = packer.methods

    val varTypes = VarTypes.methods(allMeths.toList)
    val methodNodes = allMeths.map { meth =>
      val mg = new ExprMethodGenerator(meth, inputs, varTypes, className)
      mg.methodNode
    }
    val amg = new ApplyMethodGenerator(className,
                                       outputMeths.map(_.sym.id),
                                       varTypes.globals.size)
    val cc = new CompiledClass(className, amg.methodNode :: methodNodes.toList)
    //cc.writeToTmpFile
    cc.instance.apply(_)
  }
}
