package rainier.example

import rainier.compute._
import rainier.core._
import rainier.sampler._
import rainier.repl._

object Funnel {
  val model =
    for {
      y <- Normal(0, 3).param
      x <- RandomVariable.traverse(1.to(9).map { _ =>
        Normal(0, (y / 2).exp).param
      })
    } yield (x(0), y)

  def main(args: Array[String]) {
    plot2D(model.sample(HMC(5), 1000, 10000))
  }
}
