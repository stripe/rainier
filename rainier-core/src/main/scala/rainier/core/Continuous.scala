package rainier.core

import rainier.compute._
import rainier.sampler.RNG

trait Continuous extends Distribution[Double] { self =>
  def param: RandomVariable[Real]
  def realLogDensity(real: Real): Real

  def logDensity(t: Double) = realLogDensity(Constant(t))

  def scale(a: Real): Continuous = Scale(a).transform(this)
  def translate(b: Real): Continuous = Translate(b).transform(this)
  def exp: Continuous = Exp.transform(this)
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
  def generate(r: RNG) = ???
}

object Laplace extends LocationScaleFamily {
  def logDensity(x: Real) = Real(0.5).log - x.abs
  def generate(r: RNG) = ???
}

object Gamma {
  def standard(shape: Real): Continuous = new Continuous {
    def realLogDensity(real: Real) =
      (shape - 1) * real.log - Combinatrics.gamma(shape) - real

    def param = {
      val x = new Variable
      RandomVariable(x.exp, x + realLogDensity(x.exp))
    }
    def generator = ???
  }

  def apply(shape: Real, scale: Real) =
    standard(shape).scale(scale)
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
    def realLogDensity(real: Real) = Real.one
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
