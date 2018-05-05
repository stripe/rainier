package rainier.sampler

import rainier.core._
import rainier.compute._

case class Variational(tolerance: Double, maxIterations: Int) extends Sampler {
  def description: (String, Map[String, Double]) =
    ("Variational",
     Map(
       "Tolerance" -> tolerance,
       "MaxIterations" -> maxIterations.toDouble,
     ))

  override def sample(density: Real)(implicit rng: RNG): Iterator[Sample] = {
    //VariationalOptimizer(tolerance, maxIterations)

    val modelVariables = density.variables

    // use a set of independent normals as the guide
    val K = modelVariables.length
    val niterations = 100
    val nsamples = 100
    val stepsize = .1

    val mus = List.fill(K)(new Variable)
    val sigmas = List.fill(K)(new Variable)
    val epsilonDistribution = Normal(0.0, 1.0)
    val epsilons = List.fill(K)(epsilonDistribution.param)

    def sampleFromGuide(): Seq[(Variable, Double)] = {
      epsilons.map(eps => eps.density.variables.head -> epsilonDistribution.generator.get)
    }

    val eps: List[RandomVariable[Real]] = (mus zip sigmas zip epsilons) map {
      case ((mu, sigma), epsilon) =>
        for {
          epsS <- epsilon
        } yield mu + sigma * epsS
    }

    val guideLogDensity = eps.foldLeft(Real(0.0)) {
      case (d, e) => d + e.density
    }
    // need to change this to density wrt fixed eps.
    // can I just modify the gradients?
    // are gradients/variables ordered in any way?
    val transformedDensity = density
    val surrogateLoss = transformedDensity + guideLogDensity
    val variables = surrogateLoss.variables
//    val guideVariables = guideLogDensity.variables
    val muSigmaVariables = mus ++ sigmas
    val gradientsWithVariables = (surrogateLoss.gradient zip surrogateLoss.variables) filter {
      case (gradient, variable) => muSigmaVariables.contains(variable)
    }
    val (gradients, variablesForCompiling) = gradientsWithVariables.unzip
    val cf = Compiler.default(variables, gradients)

    val initialValues = muSigmaVariables.flatMap(_ => List(0.0, 1.0))

    def collectMaps[T, U](m: Seq[Map[T, U]]): Map[T, Seq[U]] = {
      m.flatten.groupBy(_._1).mapValues(seqTuples => seqTuples.map(_._2))
    }

    val finalValues = 1.to(niterations).foldLeft(initialValues) {
      case (values, _) =>
        val gradSamples: Seq[Array[Double]] = (1 to nsamples) map { _ =>
          val samples = sampleFromGuide()
          val inputs = variables.map((samples ++ (muSigmaVariables zip values)).toMap)
          val outputs = cf(inputs.toArray)
          outputs
        }
        val perDimGradSamples = gradSamples.transpose
        val perDimGrads = perDimGradSamples.map(samples =>
          samples.sum * 1.0 / nsamples.toDouble)
        (values zip variables.map(perDimGrads)).map {
          case (v, g) => v + stepsize * g
        }
    }
    ???
  }

//  def substituteVariable(x: Real, f: Variable => Real): Real = x match {
//    case v: Variable => f(v)
//    case c: Constant => c
//    case b: BinaryReal =>
//      BinaryReal()
//    case u: UnaryReal =>
//      println(padding + u.op)
//      print(u.original, depth + 1)
//    case s: SumReal =>
//      println(padding + "Sum{" + s.seq.size + "}")
//      print(s.seq.head, depth + 1)
//
//  }
}
