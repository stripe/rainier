package rainier.compute

trait Compiler {
  def compile(inputs: Seq[Variable],
              outputs: Seq[Real]): Array[Double] => Array[Double]

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
}

object Compiler {
  var default: Compiler = asm.IRCompiler
}
