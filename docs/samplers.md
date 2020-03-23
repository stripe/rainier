---
id: samplers
title: Samplers
---

Samplers and related classes are found in `com.stripe.rainier.sampler`.

## SamplerConfig

Calls to `sample()` can optionally provide a custom configuration object that implements the following trait:

```scala
trait SamplerConfig {
  def iterations: Int
  def warmupIterations: Int
  def statsWindow: Int

  def stepSizeTuner(): StepSizeTuner
  def massMatrixTuner(): MassMatrixTuner
  def sampler(): Sampler
}
```

Your configuration can directly implement every method of this trait, or extend `DefaultConfiguration` and selectively override.

* `iterations` is the number of samples per chain to keep, after the warmup period (default: 1000)
* `warmupIterations` is the number of samples per chain to use for warmup (default: 1000)
* `statsWindow` is the number of training samples to keep for diagnostics like average acceptance rate (default: 100)
* `stepSizeTuner()` should create a new `StepSizeTuner` object. This can be:
   * `new DualAvgTuner(targetAcceptRate)` (the default is this with 0.8)
   * `StaticStepSize(stepSize)`
* `massMatrixTuner()` should create a new `MassMatrixTuner` object. This can be:
   * `new IdentityMassMatrix`
   * `new DiagonalMassMatrixTuner(initialWindowSize, windowExpansion, skipFirst, skipLast)` (the default is `new DiagonalMassMatrixTuner(50, 1.5, 50, 50)`)
   * `new DenseMassMatrixTuner(initialWindowSize, windowExpansion, skipFirst, skipLast)`
* `sampler()` should create a new `Sampler` object. This can be:
   * `new HMCSampler(nSteps)`
   * `new EHMCSampler(maxSteps, minSteps)` (the default is `new EHMCSampler(1024, 1)`)


For backwards compatibility with `0.3.0`, you can also build configs using the old `HMC` and `EHMC` constructors below.

## HMC
Hamiltonian Monte Carlo with dual averaging as per [Gelman & Hoffman](http://www.stat.columbia.edu/~gelman/research/published/nuts.pdf)

`HMC(warmupIterations: Int, iterations: Int, nSteps: Int)`

* `warmupIterations` are used to find the best step size
* `iterations` produce usable samples
* `nSteps` is the number of leap-frog steps used for integration

## EHMC

Empirical HMC as per [Wu et al](https://arxiv.org/pdf/1810.04449.pdf)

`EHMC(warmupIterations: Int, iterations: Int, l0: Int = 10, k: Int = 1000)`

* `warmupIterations` are used to find the best step size
* `iterations` produce usable samples
* `k` is the number of iterations used to build an empirical distribution of steps to U-turn
* `l0` is the number of leap-frog steps used during this empirical phase

