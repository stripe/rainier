package com.stripe.rainier.optimizer

import com.stripe.rainier.core._
import org.scalatest.FunSuite

class OptimizerTest extends FunSuite {

  test("fit normal") {
    testLBFGS(for {
      mu <- Normal(0, 10).param
      sigma <- Uniform(0, 1).param
      n <- Normal(mu, sigma).fit(List(1.0, 2.0, 3.0))
    } yield n)
  }

  val m = 5
  val eps = 0.1

  def testLBFGS(rv: RandomVariable[_]): Unit = {
    val df = rv.density()
    testLBFGS(df.nVars) { x =>
      df.update(x)
      val f = df.density * -1
      val g = 0.until(df.nVars).toArray.map { i =>
        df.gradient(i) * -1
      }
      (f, g)
    }
  }

  def testLBFGS(n: Int)(fn: Array[Double] => (Double, Array[Double])): Unit = {
    val (x1, o1) = newLBFGS(n)
    val (x2, o2) = origLBFGS(n)
    0.to(10).foreach { _ =>
      val (f, g) = fn(x1)
      o1(f, g)
      o2(f, g)
      assert(x1.toList == x2.toList)
    }
  }

  def newLBFGS(n: Int) = {
    val x = new Array[Double](n)
    val lb = new LBFGS(x, m, eps)
    (x, { (f: Double, g: Array[Double]) =>
      lb(f, g)
      ()
    })
  }

  def origLBFGS(n: Int) = {
    val x = new Array[Double](n)
    val diag = new Array[Double](n)
    val iflag = Array(0)

    (x, { (f: Double, g: Array[Double]) =>
      LBFGSOrig.lbfgs(n, m, x, f, g, false, diag, eps, iflag)
      ()
    })
  }
}
