package rainier.sampler

import rainier.compute._

case class EmceeChain(density: Real,
                      cf: Array[Double] => Double,
                      walkers: Int,
                      left: EmceeSet,
                      right: EmceeSet,
                      walker: Int)(implicit rng: RNG) {

  val (variables, accepted) =
    if (walker < (walkers / 2))
      left.walker(walker)
    else
      right.walker(walker - (walkers / 2))

  def toStream: Stream[EmceeChain] =
    this #:: next.toStream

  def next: EmceeChain = {
    val nextWalker = (walker + 1) % walkers
    val (newLeft, newRight) =
      if (nextWalker == 0) {
        (update(left, right), right)
      } else if (nextWalker == walkers / 2) {
        (left, update(right, left))
      } else {
        (left, right)
      }
    copy(walker = nextWalker, left = newLeft, right = newRight)
  }

  private def update(target: EmceeSet, complement: EmceeSet): EmceeSet = {
    val mapping = 1.to(walkers / 2).map { i =>
      rng.int(walkers / 2)
    }

    val zs = 1.to(walkers / 2).map { i =>
      Math.pow(rng.standardUniform + 1.0, 2) / 2.0
    }

    val proposals =
      target.params
        .zip(zs)
        .zip(mapping)
        .map {
          case ((vars, z), i) =>
            val otherVars = complement.params(i)
            vars.zip(otherVars).map {
              case (from, to) =>
                to + (z * (from - to))
            }
        }

    val newScores = proposals.map { variables =>
      cf(variables)
    }

    val accepted = newScores.zip(target.scores).zip(zs).map {
      case ((l2, l1), z) =>
        val nVariables = proposals.head.size
        val diff = (Math.log(z) * (nVariables - 1)) + l2 - l1
        Math.log(rng.standardUniform) < diff
    }

    val proposalSet = EmceeSet(proposals, accepted, newScores)
    target.propose(proposalSet)
  }
}

object EmceeChain {
  def apply(density: Real, variables: Seq[Variable], walkers: Int)(
      implicit rng: RNG): EmceeChain = {
    require(walkers % 2 == 0)
    val cf = Compiler.default.compile(variables, density)
    val left = initialize(variables.size, walkers / 2, cf)
    val right = initialize(variables.size, walkers / 2, cf)
    val walker = walkers - 1 //trigger update next time
    EmceeChain(density, cf, walkers, left, right, walker)
  }

  def initialize(nVars: Int, walkers: Int, cf: Array[Double] => Double)(
      implicit rng: RNG): EmceeSet = {
    val params =
      1.to(walkers)
        .map { i =>
          1.to(nVars)
            .map { j =>
              rng.standardNormal
            }
            .toArray
        }
        .toVector

    val scores =
      params.map { variables =>
        cf(variables)
      }

    val accepted = scores.map { s =>
      true
    }

    EmceeSet(params, accepted, scores)
  }
}

case class EmceeSet(
    params: Vector[Array[Double]],
    accepted: Vector[Boolean],
    scores: Vector[Double]
) {
  def walker(w: Int): (Array[Double], Boolean) =
    (params(w), accepted(w))

  def propose(other: EmceeSet): EmceeSet = {
    def merge[V](a: Seq[V], b: Seq[V]) = {
      a.zip(b).zip(other.accepted).map {
        case ((x, y), accept) =>
          if (accept) y else x
      }
    }

    val keepParams = merge(params, other.params).toVector
    val keepScores = merge(scores, other.scores).toVector
    EmceeSet(keepParams, other.accepted, keepScores)
  }
}
