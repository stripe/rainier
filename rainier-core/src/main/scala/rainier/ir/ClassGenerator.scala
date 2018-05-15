package rainier.ir

object ClassGenerator {
  def generate(inputs: Seq[Parameter],
               irs: Seq[IR],
               methodSizeLimit: Int,
               writeToTmpFile: Boolean): Array[Double] => Array[Double] = {
    val className = CompiledClass.freshName
    val packer = new Packer(methodSizeLimit)
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
    if (writeToTmpFile)
      cc.writeToTmpFile
    cc.instance.apply(_)
  }
}
