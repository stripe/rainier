package rainier.models

import rainier.compute._
import rainier.core._
import rainier.report._
import rainier.sampler._

object FitHamiltonian {

  val model = for {
    n <- Normal(3, 10).param
  } yield Map("Normal(3, 10)" -> n)

  def main(args: Array[String]) {
    Report.printReport(model, MAP(10000, 0.001))
    Report.printReport(model, Emcee(10000, 1000, 10))
    Report.printReport(model, Hamiltonian(10000, 10, 10, 10, SampleNUTS))
  }
}
