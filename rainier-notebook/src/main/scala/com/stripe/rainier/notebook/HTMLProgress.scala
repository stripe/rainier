package com.stripe.rainier.notebook

import com.stripe.rainier.sampler._
import almond.api.JupyterApi

case class HTMLProgress(kernel: JupyterApi, delay: Double) extends Progress {
  val id = java.util.UUID.randomUUID().toString

  val outputEverySeconds = 0.1

  def start(state: SamplerState) = {
    val idN = id + "-" + state.chain
    val chain = s"<b>Chain ${state.chain}</b>"
    kernel.publish.html(chain, idN)
  }

  def refresh(state: SamplerState) = {
    val idN = id + "-" + state.chain
    val chain = s"<b>Chain ${state.chain}</b>"
    kernel.publish.updateHtml(chain + ": " + render(state), idN)
  }

  def finish(state: SamplerState) = {
    val idN = id + "-" + state.chain
    val chain = s"<b>Chain ${state.chain} complete</b>"
    kernel.publish.updateHtml(chain + ": " + render(state), idN)
  }

  private def renderTime(nanos: Long): String =
    if (nanos < 1000)
      s"${nanos}ns"
    else if (nanos < 1e6)
      f"${nanos / 1000}us"
    else if (nanos < 1e9)
      f"${nanos / 1000000}ms"
    else {
      val totalSeconds = nanos / 1000000000
      if (totalSeconds < 60)
        totalSeconds.toString + "s"
      else {
        val totalMinutes = totalSeconds / 60
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        val seconds = totalSeconds % 60
        f"${hours}%2d:${minutes}%2d:${seconds}%2d"
      }
    }

  private def render(p: SamplerState): String = {
    val t = System.nanoTime()
    val itNum = s"Iteration: ${p.currentIteration}/${p.totalIterations}"
    val stepSize = f"Step size: ${p.stepSize}%.5f"
    val totalTime = "Total time elapsed: " + renderTime(t - p.startTime)
    val gradientTime =
      if (p.gradientEvaluations > 0)
        "(" + renderTime(p.meanGradientTime) + ")"
      else ""
    val gradient =
      f"Total gradient evaluations: ${p.gradientEvaluations.toDouble}%.1g $gradientTime"
    val acceptance =
      if (p.currentIteration > 0)
        f"Acceptance rate: ${p.meanAcceptanceProb}%.2f"
      else ""
    val pathLength =
      if (p.currentIteration > 0)
        f"Mean step count: ${p.meanStepCount}%.1f"
      else ""
    s"<div>$itNum</div> <div>$acceptance</div> <div>$pathLength</div> <div>$stepSize</div> <div>$gradient</div> <div>$totalTime</div>"
  }
}
