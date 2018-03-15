# Rainier implementation notes

## rainier.compute

The core of any rainier model is the joint density function, `P(Θ|x,y)`. This function (and any other real-valued functions of the parameters `Θ`) is expressed in rainier as a `rainier.compute.Real` object. `Real` is similar to TensorFlow's static compute graph: it's a DAG whose root node represents the output of the function and whose leaf nodes are either `Variable` (one for each parameter) or `Constant`. The other nodes are either `BinaryReal` (eg `real1 + real2` will construct a `BinaryReal` referencing both of them and the `+` operator), or `UnaryReal` (eg `real1.log` will construct a `UnaryReal` referencing `real1` and the `log` operation). There's also a special `SumReal` for the common case of adding up a list of numbers. The number of operations supported is currently very minimal and will need to be expanded somewhat going forward (eg there is no `abs`, which we'd need for `Laplace` distributions among others.)

As the `Real` graph is being constructed, the `Pruner` helper object will do some amount of partial evaluation, eg pre-computing operations on constants, or removing multiplications by 1 or subtractions of 0. 

The `Gradient` helper object uses reverse-mode autodifferentiation to construct the gradient for the `Variable` leaves with respect to the output node (in the form of a new, transformed `Real` for each variable).

The `Evaluator` will evaluate the value of any `Real`, given a `Map[Variable,Double]` for any referenced variables. It memoizes results, but is not otherwise especially efficient. If (as for the joint density function) you need to evaluate the same `Real` many times, you are better to use the `Compiler` to produce a compact, efficient bytecode-style representation of the function. This allocates a `Array[Double]` "heap" and compiles the function to a stream of operations of the form `heap(10) = heap(13) + heap(8)`. Values for variables can be fed into the heap array at the start, and output values can be read from it after evaluation.

It's worth noting that unlike TensorFlow, all operations are scalar. The rationale for this includes: Scala doesn't have reliable access to SIMD; we're deploying within Hadoop tasks which are inherently single threaded and don't have GPUs; and the `Compiler` makes things quite efficient for even large numbers of scalar operations. We've experimented with vectorization in that context but were not able to see performance gains from it. However, it's likely that in the future, we'll want to support GPUs and/or multithreaded CPU, for which adding at least some limited vectorization to the API will be valuable.

Another difference from TF is that there is no concept of a "placeholder" node, because MCMC is generally implemented as full-batch rather than online or mini-batch, and so the data can be treated as `Constant`.

## rainier.sampler

The `sampler` package depends only on the `compute` representation of a model: a `Sampler` will take a `Real` for the joint density and produce, effectively, a stream of `Evaluator`s that can be used to sample from other functions of the same parameters.

Rainier currently provides the `Emcee` affine-invariant sampler, a `Hamiltonian` Monte Carlo sampler, and an experimental and incomplete `NUTS` sampler. It also provides a vanilla gradient descent `MAP` optimizer.

`Emcee` is an ensemble-based method that requires no tuning and is suitable for small numbers of parameters (< 10). It is currently the default. The best reference for it is [Foreman-Mackey, Hogg, Lang & Goodman (2012)](https://arxiv.org/abs/1202.3665).

`Hamiltonian` MC is a gradient-based method which requires considerable tuning, but is quite efficient once tuned even for large numbers of parameters. The best reference is [Betancourt (2017)](https://arxiv.org/abs/1701.02434).

`NUTS` is a self-tuning variant of Hamiltonian MC ([Hoffman & Gelman (2011)](https://arxiv.org/abs/1111.4246)). Our implementation currently only tunes one of the two parameters to HMC - it does not yet include the dual-averaging step-size tuning - and is only the naive, less space-efficient version described in the paper. As such, it is currently experimental and not yet fully ready for use.

The `MAP` gradient descent optimizer is likely more of a demonstration than of practical use.

## rainier.core

TBD: make this section less terse.

The `core` package is the primary API actually used by end-users, and is a way to produce, simultaneously, a `Real` that represents the density function for the model, and a `Generator[A]` that represents a (possibly stochastic) function from the parameters to some output `A` you want to sample. Rainier requires the model to be defined inside a `RandomVariable[T]`, which is a writer monad for the `(Real,T)` tuple of the (density,output) functions. Ultimately, to be able to sample, we require that `T <:< Generator[A]`, but this is staged: parameters start off as `RandomVariable[Real]`, and these values are used to modify the density function; at some point they get wrapped in a `Generator`, at which point they are no longer able to affect the density but can use either deterministic or stochastic transformations to produce sample output; `Generator` is, itself, more or less the probability monad.