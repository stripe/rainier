---
id: roadmap
title: Roadmap
---

The items below are considered high priority for future development, and are at various stages of planning and implementation.

### Data-parallel gradient evaluation

Taking advantage of multiple CPU cores to parallelize sampling for larger datasets.

### Multivariate Normal

`MVNormal` is a very commonly used distribution that is currently not supported by Rainier.

### Discrete latent variables

Support `latent` for `Discrete` distributions, at least in some cases, with automatic Rao-Blackwellization.

### Automatic Reparameterization

Rainier currently only supports non-centered parameterizations, which is a good default, but automatic reparameterization as in
[Gorinova et al](https://arxiv.org/pdf/1906.03028.pdf) would be an improvement in some cases.

### Mass Matrix adaptation

Currently Rainier's HMC always uses the identity mass matrix. Mass matrix adaptation would improve performance on correlated parameters.

## Feedback

Feel free to file issues at [GitHub](https://github.com/stripe/rainier/issues) if something important to you is missing from this list.

You may also send email with any feedback (good or bad!) to `avi@avibryant.com`.