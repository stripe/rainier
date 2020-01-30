---
id: samplers
title: Samplers
---

These are found in `com.stripe.rainier.sampler`, and extend `Sampler`.

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
