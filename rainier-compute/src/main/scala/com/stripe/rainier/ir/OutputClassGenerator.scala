package com.stripe.rainier.ir

import com.stripe.rainier.internal.asm.tree.MethodNode

private[ir] case class OutputClassGenerator(name: String,
                                            classSizeLimit: Int,
                                            outputIDs: Seq[(String, Int)],
                                            numInputs: Int,
                                            numGlobals: Int,
                                            numOutputs: Int)
    extends ClassGenerator {

  def superClasses = Array("com/stripe/rainier/ir/CompiledFunction")
  def methods: Seq[MethodNode] =
    0.to(9).map { i =>
      val ids =
        outputIDs.zipWithIndex.filter { case (_, j) => j % 10 == i }.map(_._1)
      OutputMethodGenerator(i, classSizeLimit, ids).methodNode
    } ++
      List(
        createConstantMethod("numInputs", numInputs),
        createConstantMethod("numGlobals", numGlobals),
        createConstantMethod("numOutputs", numOutputs)
      )
}
