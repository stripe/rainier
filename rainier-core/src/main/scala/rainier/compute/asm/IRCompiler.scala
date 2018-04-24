package rainier.compute.asm

import rainier.compute._

object IRCompiler extends Compiler {
  def compile(inputs: Seq[Variable],
              outputs: Seq[Real]): Array[Double] => Array[Double] = {
    val className = CompiledClass.freshName
    val (outputMeths, moreMeths) = outputs.map { real =>
      IR.packIntoMethods(IR.toIR(real))
    }.unzip
    val allMeths = moreMeths.reduce(_ ++ _)
    val varTypes = VarTypes(IR.DepStats(allMeths.toList))
    val methodNodes = allMeths.map { meth =>
      val mg = new ExprMethodGenerator(meth, inputs, varTypes, className)
      mg.methodNode
    }
    val amg = new ApplyMethodGenerator(className,
                                       outputMeths.map(_.sym.id),
                                       varTypes.globals.size)
    val cc = new CompiledClass(className, amg.methodNode :: methodNodes.toList)
    cc.writeToTmpFile
    cc.instance.apply(_)
  }
}
