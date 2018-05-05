package rainier.core

import rainier.compute._
import rainier.sampler.RNG
import scala.annotation.tailrec

trait Continuous extends Distribution[Double] { self =>
  def param: RandomVariable[Real]

  def logDensity(t: Double) = realLogDensity(Real(t))

  def scale(a: Real): Continuous = Scale(a).transform(this)
  def translate(b: Real): Continuous = Translate(b).transform(this)
  def exp: Continuous = Exp.transform(this)

  private[rainier] def realLogDensity(real: Real): Real
}

trait LocationScaleFamily { self =>
  def logDensity(x: Real): Real
  def generate(r: RNG): Double

  val standard: Continuous = new Continuous {
    val generator = Generator.from { (r, n) =>
      generate(r)
    }
    def realLogDensity(real: Real) = self.logDensity(real)
    def param = {
      val x = new Variable
      RandomVariable(x, self.logDensity(x))
    }
  }

  def apply(location: Real, scale: Real): Continuous =
    standard.scale(scale).translate(location)
}

object Normal extends LocationScaleFamily {
  def logDensity(x: Real) = (x * x) / -2.0
  def generate(r: RNG) = r.standardNormal
}

object Cauchy extends LocationScaleFamily {
  def logDensity(x: Real) = (((x * x) + 1) * Math.PI).log * -1
  def generate(r: RNG) = r.standardNormal / r.standardNormal
}

object Laplace extends LocationScaleFamily {
  def logDensity(x: Real) = Real(0.5).log - x.abs
  def generate(r: RNG) = {
    val u = r.standardUniform - 0.5
    Math.signum(u) * -1 * Math.log(1 - (2 * Math.abs(u)))
  }
}

object Gamma {
  def apply(shape: Real, scale: Real) =
    standard(shape).scale(scale)

  def standard(shape: Real): Continuous = new Continuous {
    def realLogDensity(real: Real) =
      If(real > 0,
         (shape - 1) * real.log -
           Combinatrics.gamma(shape) - real,
         Real.zero.log)

    /*
    Jacobian time: we need pdf(x) and we have pdf(f(x)) where f(x) = e^x.
    This is pdf(f(x))f'(x) which is pdf(e^x)e^x.
    If we take the logs we get logPDF(e^x) + x.
     */
    def param = {
      val x = new Variable
      RandomVariable(x.exp, x + realLogDensity(x.exp))
    }

    def generator = Generator.from { (r, n) =>
      val a = n.toDouble(shape)
      if (a < 1) {
        val u = r.standardUniform
        generate(a + 1, r) * Math.pow(u, 1.0 / a)
      } else
        generate(a, r)
    }

    @tailrec
    private def generate(a: Double, r: RNG): Double = {
      val d = a - 1.0 / 3.0
      val c = (1.0 / 3.0) / Math.sqrt(d)

      var x = r.standardNormal
      var v = 1.0 + c * x
      while (v <= 0) {
        x = r.standardNormal
        v = 1.0 + c * x
      }

      val v3 = v * v * v
      val u = r.standardUniform

      if ((u < 1 - 0.0331 * x * x * x * x) ||
          (Math.log(u) < 0.5 * x * x + d * (1 - v3 + Math.log(v3))))
        d * v3
      else
        generate(a, r)
    }
  }
}

object Exponential {
  val standard = Gamma.standard(1.0)
  def apply(rate: Real) = standard.scale(Real.one / rate)
}

object LogNormal {
  def apply(location: Real, scale: Real) =
    Normal(location, scale).exp
}

object Uniform {
  val standard: Continuous = new Continuous {
    def realLogDensity(real: Real) =
      If(real >= 0, If(real <= 1, Real.zero, Real.zero.log), Real.zero.log)

    val generator = Generator.from { (r, n) =>
      r.standardUniform
    }
    def param = {
      val x = new Variable
      val logistic = Real.one / (Real.one + (x * -1).exp)
      val density = (logistic * (Real.one - logistic)).log
      RandomVariable(logistic, density)
    }
  }

  def apply(from: Real, to: Real): Continuous =
    standard.scale(to - from).translate(from)
}
