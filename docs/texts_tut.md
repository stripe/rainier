# Inferring Behavior from Text-Message Data

This is a port of [this example](http://nbviewer.jupyter.org/github/CamDavidsonPilon/Probabilistic-Programming-and-Bayesian-Methods-for-Hackers/blob/master/Chapter1_Introduction/Ch1_Introduction_PyMC3.ipynb#Example:-Inferring-behaviour-from-text-message-data).

Given the number of text messages a user receives per day for 70 days, can we infer their behavior?
The general strategy is:
* Model the data: choose a probability distribution which could have produced it. This distribution will depend on some parameters. Conditioning on a given choice of parameters `theta` gives us our  _likelihood_ `p(data | theta)`.
* Model the parameters: for each parameter, choose a distribution which could have produced it. These distributions are our _priors_: they represent our apriori belief in the values of the parameters before we observe any data. Since these distributions also have parameters, we could get fancy and model those--that is called Bayesian hierarchical modeling--but the basic thing is to just choose reasonable guesses for the parameters of our priors.
* Combine the priors over each parameter into a single prior `p(theta)` over all of the parameters,
* Feed our likelihood and prior into MCMC to generate samples from the posterior `p(theta | data)` which approximates the true posterior.

The data is counts of texts for 70 days. Let generate counts of data for 70 days with a actual change
in behavior and see if we can infer it. We'll generate the data with a Poisson and we'll have the parameter
of the Poisson change on a specific day in the interval.

```scala
val rand = new scala.util.Random
def poisson(lambda: Double) = {
    val l = math.exp(-lambda)
    var k = 0
    var p = 1.0
    while (p > l) {
      k += 1
      p *= rand.nextDouble
    }
    k - 1
}

val lambdaOne = 17.0
val lambdaTwo = 23.0
val tau = 44.0

val numDays = 70
val days = (1 to numDays).toSeq

val data = days.map{ day =>
  if(day < tau) poisson(lambdaOne)
  else poisson(lambdaTwo)
}
```

So our `lambda` parameter depends on the day. The expected number of texts on each of the first 44 days is 17 and on each of the remaining days goes up to 23. Our parameters are the two lambdas and tau. Let's see if we can infer them.

Each lambda can be any positive real number, so we model that with an exponential distribution `Exp(alpha)`.
(We could use two exponential distributions, but let's keep it simple).
We have no opinion about what `alpha` might be, so let's start with a guess of the reciprocal of the average of the data.
This is a tip from Cam Davidson-Pilon.
We could represent `tau` as an integer or a real number between 0 and 70; things work a bit better not restricting to integers so our prior will just be a continuous uniform.
```scala
import rainier.core._

val alpha = 1D / (data.sum.toDouble / data.size)
val lambdaOnePrior = Exponential(alpha).param
val lambdaTwoPrior = Exponential(alpha).param
val tauPrior = Uniform(0, numDays).param
```
We want to combine these into a prior on a single object that wraps all our parameters and can compute the likelihood for an observation. The tricky part is defining its density to be a continuous, differentiable function, even though we're assuming
a stepwise change in the value of lambda over time. We do that by using a logistic function on the difference between the day number and tau, that will quickly converge to 0 for negative numbers and 1 for positive numbers, to let us interpolate between lambdaOne and lambdaTwo.

```scala
import rainier.compute._

case class TextParams(lambdaOne: Real, lambdaTwo: Real, tau: Real) extends Predictor[Int, Int, Poisson] {
  def logistic(x: Real): Real = x.exp / (x.exp + 1)

  def apply(day: Int): Poisson = {
    val isPastTau = logistic(Real(day) - tau)
    val blendedLambda = lambdaOne * (Real.one - isPastTau) + lambdaTwo * isPastTau
    Poisson(blendedLambda)
  }
}
```

Now that we have that defined, we can create the prior for it, and condition on our observed data:

```scala
val posterior = 
  for{
    l1 <- lambdaOnePrior
    l2 <- lambdaTwoPrior
    t <- tauPrior
    _ <- TextParams(l1, l2, t).fit(days.zip(data))
  } yield (l1, l2, t)
```

Finally, to see some output, it's easiest if we give labels to the various parameters. We can then use `Report` to run the inference and print out some results:

```scala
scala> import rainier.sampler._
import rainier.sampler._

scala> import rainier.report._
import rainier.report._

scala> val model = posterior.map{case (l1, l2, t) =>
     |   Map(
     |     "Lambda1" -> l1,
     |     "Lambda2" -> l2,
     |     "tau" -> t
     |   )
     | }
model: rainier.core.RandomVariable[scala.collection.immutable.Map[String,rainier.compute.Real]] = rainier.core.RandomVariable@1f7949dc

scala> Report.printReport(model, Emcee(burnIn = 100, iterations = 1000, walkers = 1000))

Emcee
           Variables: 3
             Walkers: 1000.000
          Iterations: 1000.000
             Burn In: 100.000
            Run time: 25.610
     Acceptance rate:  0.611 ( 0.572, 0.656)

             Lambda1: 16.300 (15.182,17.469) [ 3.292]
             Lambda2: 22.791 (21.136,24.880) [ 1.783]
                 tau: 44.455 (41.563,48.236) [ 2.421]
```

