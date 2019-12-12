package com.stripe.rainier.compute

import com.stripe.rainier.ir.CompiledFunction

final class Compiler(parameters: List[Parameter],
                     placeholders: List[Placeholder],
                     batchWidths: List[Int],
                     outputs: List[(String, Real)]) {
  def compiledModel(): CompiledModel = {
    val cf = Compiler.compile(parameters ++ placeholders, outputs)

    val batchInputStarts = batchWidths.scanLeft(0) { _ + _ }
    val compiledBatches = 0.until(batchWidths.size).map { i =>
      val inputStartIndex = parameters.size + batchInputStarts(i)
      val outputStartIndex = (parameters.size + 1) * i
      val data = placeholders
        .slice(batchInputStarts(i), batchInputStarts(i + 1))
        .map(_.values)
        .toArray
      CompiledBatch(cf, inputStartIndex, outputStartIndex, data)
    }

    new CompiledModel(parameters, cf, compiledBatches.toArray)
  }
}

object Compiler {
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
    CompiledFunction(params, exprs)
  }

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

  def apply(base: Real, batches: Seq[Batch[Real]]): Compiler = {
    val baseParameters =
      RealOps.variables(base).collect { case v: Parameter => v }

    val (paramSet, placeholders, batchWidths) =
      batches.reverse.foldLeft(
        (baseParameters, List.empty[Placeholder], List.empty[Int])) {
        case ((paramAcc, phAcc, widthAcc), batch) =>
          val variables = RealOps.variables(batch.value)
          val params = variables.collect { case v: Parameter => v }
          val phs = variables.collect { case v: Placeholder => v }.toList

          (params ++ paramAcc, phs ++ phAcc, phs.size :: widthAcc)
      }

    val parameters = paramSet.toList
    val outputs =
      withGradient("base", base, parameters) ++
        batches.zipWithIndex.flatMap {
          case (b, i) => withGradient(s"batch$i", b.value, parameters)
        }

    new Compiler(parameters, placeholders, batchWidths, outputs)
  }
}
