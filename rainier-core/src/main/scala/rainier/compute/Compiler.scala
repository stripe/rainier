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
  var default: Compiler = InstrumentingCompiler(asm.IRCompiler)
}

case class InstrumentingCompiler(orig: Compiler) extends Compiler {
  var count = 0L
  var nanos = 0L
  def compile(inputs: Seq[Variable],
              outputs: Seq[Real]): Array[Double] => Array[Double] = {
    val cf = orig.compile(inputs, outputs)
    val fn = { array: Array[Double] =>
      count += 1
      val t1 = System.nanoTime
      val result = cf(array)
      val t2 = System.nanoTime
      nanos += (t2 - t1)
      if (count % 1000000 == 0) {
        println(s"[InstrumentingCompiler] $count runs, ${nanos / count} ns/run")
      }
      result
    }
    fn
  }
}
