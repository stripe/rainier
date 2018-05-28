package com.stripe.rainier

import java.io.File

import com.cibo.evilplot.numeric.Point
import com.cibo.evilplot.plot.ContourPlot
import com.stripe.rainier.sampler._

package object repl {
  def plot1D[N](seq: Seq[N])(implicit num: Numeric[N]): Unit = {
    println(DensityPlot().plot1D(seq.map(num.toDouble)).mkString("\n"))
  }

  def plot2D[M, N](seq: Seq[(M, N)])(implicit n: Numeric[N],
                                     m: Numeric[M]): Unit = {
    println(
      DensityPlot()
        .plot2D(seq.map { case (a, b) => (m.toDouble(a), n.toDouble(b)) })
        .mkString("\n"))
  }

  def contourPlot[M, N](seq: Seq[(M, N)], file: String, contours: Int = 5)(
      implicit n: Numeric[N],
      m: Numeric[M]): Unit = {
    val points = seq.map {
      case (x, y) => Point(m.toDouble(x), n.toDouble((y)))
    }

    import com.cibo.evilplot.plot.aesthetics.DefaultTheme.defaultTheme
    ContourPlot(points, contours = Some(contours))
      .xAxis()
      .yAxis()
      .frame()
      .render()
      .write(new File(file))
  }

  implicit val rng: RNG = RNG.default
}
