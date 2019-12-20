package com.stripe.rainier.core

import com.stripe.rainier.sampler._
import com.stripe.rainier.compute._
import org.scalatest.FunSuite

class WAICTest extends FunSuite {
  implicit val rng = RNG.default
  val cars = List(
    (4.0, 2.0),
    (4.0, 10.0),
    (7.0, 4.0),
    (7.0, 22.0),
    (8.0, 16.0),
    (9.0, 10.0),
    (10.0, 18.0),
    (10.0, 26.0),
    (10.0, 34.0),
    (11.0, 17.0),
    (11.0, 28.0),
    (12.0, 14.0),
    (12.0, 20.0),
    (12.0, 24.0),
    (12.0, 28.0),
    (13.0, 26.0),
    (13.0, 34.0),
    (13.0, 34.0),
    (13.0, 46.0),
    (14.0, 26.0),
    (14.0, 36.0),
    (14.0, 60.0),
    (14.0, 80.0),
    (15.0, 20.0),
    (15.0, 26.0),
    (15.0, 54.0),
    (16.0, 32.0),
    (16.0, 40.0),
    (17.0, 32.0),
    (17.0, 40.0),
    (17.0, 50.0),
    (18.0, 42.0),
    (18.0, 56.0),
    (18.0, 76.0),
    (18.0, 84.0),
    (19.0, 36.0),
    (19.0, 46.0),
    (19.0, 68.0),
    (20.0, 32.0),
    (20.0, 48.0),
    (20.0, 52.0),
    (20.0, 56.0),
    (20.0, 64.0),
    (22.0, 66.0),
    (23.0, 54.0),
    (24.0, 70.0),
    (24.0, 92.0),
    (24.0, 93.0),
    (24.0, 120.0),
    (25.0, 85.0)
  )

  test("cars linear model") {
    val a = Normal(0, 100).param
    val b = Normal(0, 10).param
    val sigma = Uniform(0, 30).param
    val fn = Fn.numeric[Double].map { s =>
      val mu = a + b * s
      Normal(mu, sigma)
    }
    val m = Model.observe(cars.map(_._1), cars.map(_._2), fn)

    def logit(x: Double) = math.log(x / (1.0 - x))

    //samples of (a,b,sigma) taken from external sampler	
    val samples = List(
      Array(-8.780982, 3.702219, 16.34632),
      Array(-17.308180, 3.793232, 14.12478),
      Array(-20.655049, 4.064332, 14.65831),
      Array(-11.534922, 3.705626, 17.13509),
      Array(-22.650850, 3.934438, 16.23996)
    ).map { a =>
      Array(
        a(0) / 100.0, //correct for scaling in prior	
        a(1) / 10.0, //correct for scaling in prior	
        logit(a(2) / 30.0) //correct for scaling & logistic transform	
      )
    }

    val w =
      m.targets
        .map { t =>
          WAIC(samples, m.parameters, t)
        }
        .reduce(_ + _)

    def assertWithinEps(x: Double, y: Double) = {
      val err = math.abs((x - y) / y)
      assert(err < 0.02)
    }

    //results from external WAIC implementation	
    assertWithinEps(w.pWAIC, 4.020387)
    assertWithinEps(w.lppd, -206.6377)
  }
}
