package rainier.compute.asm

import rainier.compute._

object IRCompiler extends Compiler {
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
