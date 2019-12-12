package com.stripe.rainier.compute

import com.stripe.rainier.ir.CompiledFunction

final case class Compiler(methodSizeLimit: Int, classSizeLimit: Int) {
  def compile(base: Real, batches: Seq[Batch[Real]]): CompiledModel = ???

  def compile(inputs: Seq[Variable],
              outputs: Seq[(String, Real)]): CompiledFunction = {
    val translator = new Translator
    val params = inputs.map { v =>
      v.param
    }
    val exprs = outputs.map {
      case (s, r) =>
        s -> translator.toExpr(r)
    }
    CompiledFunction(params, exprs, methodSizeLimit, classSizeLimit)
  }
}

object Compiler {
  def default: Compiler = Compiler(200, 100)

  def withGradient(name: String,
                   real: Real,
                   parameters: List[Parameter]): List[(String, Real)] =
    if (parameters.isEmpty)
      List((name, real))
    else
      (name, real) :: Gradient
        .derive(parameters, real)
        .zipWithIndex
        .map {
          case (g, i) =>
            (s"${name}_grad${i}", g)
        }
}
/*
object DataFunction {
  def apply(targets: List[Real],
            compiler: Compiler = Compiler.default): DataFunction = {
    val (paramSet, placeholders, dataList, base, batch) =
      targets.foldLeft(
        (Set.empty[Parameter],
         List.empty[Placeholder],
         List.empty[Array[Array[Double]]],
         Real.zero,
         List.empty[Real])) {
        case ((paramAcc, phAcc, dataAcc, baseAcc, batchAcc), target) =>
          val variables = RealOps.variables(target)
          val targetParams = variables.collect { case x: Parameter => x }
          val targetPh = variables.collect { case x: Placeholder => x }.toList
          if (targetPh.isEmpty) {
            (targetParams ++ paramAcc,
             phAcc,
             dataAcc,
             baseAcc + target,
             batchAcc)
          } else {
            (targetParams ++ paramAcc,
             targetPh ++ phAcc,
             targetPh.map(_.values).toArray :: dataAcc,
             baseAcc,
             target :: batchAcc)
          }
      }

    val parameters = paramSet.toList
    val data = dataList.toArray
    val batchOutputs = batch.zipWithIndex.flatMap {
      case (o, i) =>
        Compiler.withGradient(s"target${i}", o, parameters)
    }

    val cf = compiler.compile(
      parameters ++ placeholders,
      Compiler.withGradient("base", base, parameters) ++ batchOutputs)

    DataFunction(cf, parameters, data)
  }
}
 */
