package rainier

import rainier.sampler._

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

  implicit val rng = RNG.default
}
