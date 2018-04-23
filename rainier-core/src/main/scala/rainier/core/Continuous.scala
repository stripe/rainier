package rainier.core

import rainier.compute._
import rainier.sampler.RNG
import scala.annotation.tailrec

trait Continuous extends Distribution[Double] { self =>
  def param: RandomVariable[Real]
  def realLogDensity(real: Real): Real

  def logDensity(t: Double) = realLogDensity(Real(t))

  def scale[M: ToReal](a: M): Continuous = Scale(a).transform(this)
  def translate[N: ToReal](b: N): Continuous = Translate(b).transform(this)
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

  def apply[M: ToReal, N: ToReal_+](location: M, scale: N): Continuous =
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
  def apply[M: ToReal_+, N: ToReal_+](shape: M, scale: N) =
    standard(shape).scale(scale)

  def standard[M](shape: M)(implicit ev: ToReal_+[M]): Continuous =
    new Continuous {
      val s = ev(shape)
      def realLogDensity(real: Real) =
        (s - 1) * real.log - Combinatrics.gamma(s) - real

      def param = {
        val x = new Variable
        RandomVariable(x.exp, x + realLogDensity(x.exp))
      }

      def generator = Generator.from { (r, n) =>
        val a = n.toDouble(s)
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
  def apply[M](rate: M)(implicit ev: ToReal_+[M]) =
    standard.scale(Real.one / rate)
}

object LogNormal {
  def apply[M: ToReal, N: ToReal_+](location: M, scale: N) =
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

  def apply[M, N](from: M, to: N)(implicit ev: ToReal[N],
                                  ev2: ToReal[M]): Continuous =
    standard.scale(ev(to) - from).translate(from)
}
