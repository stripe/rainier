package rainier.sampler

import org.scalatest.FunSuite
import rainier.compute._
import rainier.core._
import rainier.sampler._
import rainier.repl._


class VariationalTest extends FunSuite {
  test("basic normal") {
    val x = Normal(2.0, 3.0).param
    val variationalSampler = new Variational(0.0, 0, 100, 100, .1)
    val samples = x.sample(variationalSampler, 1, 100000, 1)
    println(samples.take(10))
    plot1D(samples)
  }
}
