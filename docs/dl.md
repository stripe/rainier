# Bayesian Inference by analogy with Deep Learning

This is an idiosyncratic comparison of deep learning, as embodied by systems like TensorFlow and PyTorch, with Markov Chain Monte Carlo bayesian inference ("MCMC"), as embodied by systems like Stan and Rainier. It is not meant to be a perfect description of any of these, but rather to help people who are familiar with DL gain some intuitions about MCMC.

## Ways in which they are similar

Both are concerned with a function from inputs `x` to outputs `y`, parameterized by a set of real-valued parameters `Θ`: `y = f(x;Θ)`. Let's call this the "output function".

Both are also concerned with a related function `L(x;y;Θ)` that assesses how good a choice of parameters `Θ` is for inputs `x` and known outputs `y`. Let's call this the "loss function" (though in MCMC you'd more likely call it a "likelihood" or "density" function).

Both make use of auto-differentiation to find the gradient of `Θ` with respect to the loss function, and use this gradient to efficiently explore the parameter space. Both spend most of their cycles evaluating the loss function and this gradient in a tight loop.

Both let you use flexible, turing-complete programming languages (eg Python, Scala, or Stan) to construct the output function and the loss function, subject to some restrictions to ensure differentiability and efficient evaluation.

Both often use multiple layers or levels of parameters, and encourage the reuse of individual parameters at multiple places in the output and loss functions (via weight sharing in RNNs or CNNs for DL, or hyperparameters in hierarchical bayesian models for MCMC).

## Ways in which they are different

In MCMC, the loss function is understood to always be proportional to `P(Θ|x,y)` (or really, the negative log of that). Per Bayes' law, this is decomposed into a likelihood, `P(x,y|Θ)`, and a prior, `P(Θ)`. This means you are expected to provide a prior distribution for every parameter.

During what DL would call "training", the goal is not to come up with a single value for `Θ`, but to *sample* from the space of possible `Θ` proportionally to the probability defined by that loss function.

During what DL would call "forwards mode" or "inference", the goal is not to produce a single value for `y`, but to generate a large number of possible values of `y`, one for each of the sampled `Θ` values; that is, again, we're sampling from a distribution of possible `y` values.

To this end, rather than using SGD as in DL, where the goal is to smoothly descend to the bottom of the loss-valley and stay there, in MCMC the gradients are used in something closer to a physics simulation of a frictionless puck sliding around that same metaphorical valley - it spends more time at the bottom than at any other single point, but it still spends lots of time sliding around on the walls, sampling as it goes.

Although stochastic or distributed versions of MCMC algorithms are a research topic, the common practice is to use all of your data on each iteration, on a single node. This means data sizes tend to be small for MCMC compared to DL.

Similarly, MCMC models tend to have fewer parameters than DL models - 100,000 might be considered a reasonable upper bound, and there are (non-gradient-based) algorithms that only work reliably for fewer than 10 parameters and nonetheless see plenty of use.

Because of the smaller data and parameter sizes, not all MCMC systems support GPUs, and they are not considered essential.

Architectures tend to be quite different in MCMC vs DL; in particular, although NNs obviously dominate DL, they are much less common in MCMC models.