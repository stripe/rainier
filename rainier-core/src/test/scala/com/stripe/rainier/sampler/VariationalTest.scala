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
    plot1D(samples)
  }

  test(" 2d independant normal") {
    val x = Normal(3.0, 0.4).param
    val y = x.flatMap(a => Normal(1.5, 0.2).param)
    val xy = x.zip(y)
    val variationalSampler = new Variational(0.0, 0, 100, 100, .1)
    val samples = xy.sample(variationalSampler, 1, 100000, 1)
    plot2D(samples)
  }

  test(" 2d normal") {
    val x = Normal(2.0, 1.0).param
    val y = x.flatMap(a => Normal(a, 0.3).param)
    val xy = x.zip(y)
    val variationalSampler = new Variational(0.0, 0, 100, 100, .1)
    val samples = xy.sample(variationalSampler, 1, 100000, 1)
    plot2D(samples)
  }

}
