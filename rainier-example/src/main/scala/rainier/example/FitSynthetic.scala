package rainier.example

import rainier.compute._
import rainier.core._
import rainier.sampler._
import rainier.report._

object FitSynthetic {
  implicit val rng = RNG.default

  def report(description: String)(fn: Real => Continuous): Unit = {
    println(description)
    List(0.1, 1.0, 10.0).foreach { trueValue =>
      val trueDist = fn(Real(trueValue))
      val syntheticData = RandomVariable(trueDist.generator).sample().take(1000)
      val x = new Variable
      val model =
        for {
          x <- LogNormal(0, 10).param
          _ <- fn(x).fit(syntheticData)
        } yield Map("x" -> x)

      println("True value: " + trueValue)
      Report.printReport(model, Emcee(1000, 100, 100))
      println("")
    }
    println("")
  }

  def main(args: Array[String]) {
    report("Normal(0,x)") { x =>
      Normal(0, x)
    }
    report("LogNormal(0,x)") { x =>
      LogNormal(0, x)
    }
    report("Exponential(x)") { x =>
      Exponential(x)
    }
    report("Uniform(x,x*2)") { x =>
      Uniform(x, x * 2)
    }
    report("Laplace(0,x)") { x =>
      Laplace(0, x)
    }
  }
}
