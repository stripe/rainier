package rainier.core

import rainier.sampler.RNG
import rainier.compute.Real

abstract class Predictor[X, Y, Z](implicit ev: Z <:< Distribution[Y])
    extends Likelihood[(X, Y)] {
  def apply(x: X): Z

  def fit(pair: (X, Y)) = {
    val (x, y) = pair
    ev(apply(x)).fit(y).map { g =>
      g.map { yy =>
        (x, yy)
      }
    }
  }

  def predict(seq: Seq[X]): Generator[Seq[(X, Y)]] =
    Generator.traverse(seq.map { x =>
      ev(apply(x)).generator.map { y =>
        (x, y)
      }
    })

  def predict(x: X): Generator[Y] = ev(apply(x)).generator
}

object Predictor {
  def from[X, Y, Z](fn: X => Z)(implicit ev: Z <:< Distribution[Y]) =
    new Predictor[X, Y, Z] {
      def apply(x: X) = fn(x)
    }
}
