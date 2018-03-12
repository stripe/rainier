package rainier.sampler

import rainier.compute._

case class EmceeChain(density: Real,
                      cf: Compiler.CompiledFunction,
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
            vars.map {
              case (k, from) =>
                val to = otherVars(k)
                k -> (to + (z * (from - to)))
            }
        }

    val newScores = proposals.map { variables =>
      val outputs = cf(variables)
      outputs(density)
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
  def apply(density: Real, walkers: Int)(implicit rng: RNG): EmceeChain = {
    require(walkers % 2 == 0)
    val cf = Compiler(List(density))
    val left = initialize(density, walkers / 2, cf)
    val right = initialize(density, walkers / 2, cf)
    val walker = walkers - 1 //trigger update next time
    EmceeChain(density, cf, walkers, left, right, walker)
  }

  def initialize(density: Real, walkers: Int, cf: Compiler.CompiledFunction)(
      implicit rng: RNG): EmceeSet = {
    val vars = Real.variables(density)
    val params =
      1.to(walkers)
        .map { i =>
          vars.map { v =>
            v -> rng.standardNormal
          }.toMap
        }
        .toVector

    val scores =
      params.map { variables =>
        cf(variables)(density)
      }

    val accepted = scores.map { s =>
      true
    }

    EmceeSet(params, accepted, scores)
  }
}

case class EmceeSet(
    params: Vector[Map[Variable, Double]],
    accepted: Vector[Boolean],
    scores: Vector[Double]
) {
  def walker(w: Int): (Map[Variable, Double], Boolean) =
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
