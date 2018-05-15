# Rainier implementation notes

## rainier.compute

The core of any Rainier model is the joint density function, `P(Θ|x,y)`. This function (and any other real-valued functions of the parameters `Θ`) is expressed in Rainier as a `rainier.compute.Real` object. `Real` is similar to TensorFlow's static compute graph: it's a DAG whose root node represents the output of the function and whose leaf nodes are either `Variable` (one for each parameter) or constant `Double`s, and whose interior nodes represent mathematical operations on these. The number of operations supported is deliberately minimal: the 4 arithmetic operators, along with `log`, `exp`, and `abs`. `Real` also includes the standard comparison operators and a simple `If` expression, though since these introduces discontinuities their use should be limited.

Unlike TensorFlow, all operations are scalar. Although your models might well involve vectors or other collections of parameters and observations, the underlying compute graph will be expressed purely in terms of scalar values and operations. This choice simplifies the API and the DAG implementation, and is well-suited to targeting CPU-based execution, especially on the JVM where there isn't reliable access to SIMD. In the future, if we want to support execution on GPUs, adding at least some limited vectorization to the API will be valuable.

Also unlike TensorFlow, there is no concept of a "placeholder" for observations, or other support for mini-batch execution. Instead, the assumption is that all of your data will fit in memory, and data points are treated just like any other constants in your model. This choice is informed by the typical use of full-batch iterations in MCMC algorithms, as well as the generally smaller data sizes for Bayesian inference compared to Deep Learning applications.

One huge benefit that Rainier gets from this last choice is the ability to partially evaluate the model based on the known observations even when the parameters are still unknown. As the `Real` DAG is being constructed, it will take every opportunity to aggressively pre-compute operations on constants, expand or simplify expressions, and combine common terms, with the goal of minimizing the number of operations that will be needed for each evaluation later on. (For more info on the representation we use to achieve this, see the comments in [Real.scala](rainier-core/src/main/scala/compute/Real.scala)).

In the extreme, this can reduce what would normally be `O(n)` of work on each iteration into `O(n)` work once, during the partial evaluation, followed by `O(1)` work on each iteration. As a simple example, if you consider computing the L2 loss of a series of known data points vs an unknown estimate `y`, `data.map{x => Math.pow(x - y,2)}.sum`, this can be reduced to an expression of the form `y*a + y*y*b + c`, with known `a`, `b`, `c`, no matter how big `data` is.

Since Rainier supports gradient-based sampling and optimization methods, `Real` provides `gradient`, which uses reverse-mode autodifferentiation to construct the gradient for each `Variable` leaf in the DAG with respect to the output node (in the form of a new, transformed `Real` for each variable).

To evaluate a `Real` given parameters `Θ`, you can construct an `Evaluator` containing a `Map[Variable,Double]` with concrete values for each `Variable` leaf. You can reuse this same `Evaluator` to evaluate multiple `Real`s in sequence (for example, a joint density and also the various components of its gradient), and it will memoize and reuse any shared intermediate results. However, if you know you need to evaluate the same set of `Real`s many times, you are much better to use `Compiler`, which will produce an extremely efficient `Array[Double] => Array[Double]` for computing the values of multiple `Real`s given values for the union of their input `Variable`s. This makes use of the `rainier.ir` package to dynamically generate and load a custom `.class` with optimized JVM bytecode for running the computation.

## rainier.compute.asm

The `asm` package provides an IR

## rainier.sampler

The `sampler` package depends only on the `compute` representation of a model: a `Sampler` will take a `Real` for the joint density and produce, effectively, a stream of `Evaluator`s that encapsulate parameter values sampled from the posterior. These `Evaluator`s can be used to sample from other functions of the same parameters.

Rainier currently provides the `Walkers` affine-invariant sampler and the `HMC` Hamiltonian sampler. It also provides a vanilla gradient descent `MAP` optimizer. All of these share the common `Sampler` interface.

`Walkers` is an ensemble-based method that is fast and robust for small numbers of parameters (< 10). It is currently the default. The best reference for it is [Foreman-Mackey, Hogg, Lang & Goodman (2012)](https://arxiv.org/abs/1202.3665).

`HMC` is a gradient-based method which requires some tuning, but is quite efficient once tuned even for large numbers of parameters. The best reference is [Betancourt (2017)](https://arxiv.org/abs/1701.02434). Our implementation includes the dual-averaging automatic tuning of step-size from the [NUTS paper](http://www.stat.columbia.edu/~gelman/research/published/nuts.pdf), but requires you to manually specify a number of leap-frog steps. In the future, we plan to implement the full NUTS algorithm to also dynamically select the number of steps.

The `MAP` gradient descent optimizer is likely more of a demonstration than of practical use, but it does have the virtue of simplicity. In the future, we will hopefully add more sophisticated optimizers such as `L-BFGS'.

## rainier.core

The `core` package is the primary API actually used by end-users, and is a way to produce, simultaneously, a `Real` that represents the density function for the model, and a `Generator[A]` that represents a (possibly stochastic) function from the parameters to some output `A` you want to sample. Rainier requires the model to be defined inside a `RandomVariable[T]`, which is a writer monad for the `(Real,T)` tuple of the (density,output) functions. Ultimately, to be able to sample, we require that `T <:< Generator[A]`, but this is staged: parameters start off as `RandomVariable[Real]`, and these values are used to modify the density function; at some point they get wrapped in a `Generator`, at which point they are no longer able to affect the density but can use either deterministic or stochastic transformations to produce sample output; `Generator` is, itself, more or less the probability monad.

Along with `RandomVariable`, the `core` package contains implementations of many common distributions like `Normal` and `Gamma` which can be used to produce `RandomVariable`s for parameters or from conditioning on observed data, and to produce `Generator`s for synthetic or post-posterior data generation.