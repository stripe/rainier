# com.stripe.rainier.Real

At the heart of Rainier is the `Real` type, which represents a function from some vector Θ of real-valued parameters to a single real-valued output. This document will provide a brief introduction to it; if you'd like to follow along, `sbt "project rainierCore" console` will get you to a Scala REPL.

Let's start by importing `Real` and its one public subclass, `Variable`:
```tut:silent
import com.stripe.rainier.compute.{Real,Variable}
```

## Constant

The very simplest kind of `Real` you can create is a function that always returns a constant value. You can create this by passing a double (or other numeric type) into `Real()`:

```tut
val three = Real(3)
```

`Real` provides the basic arithmetic functions, so for example you can sum two `Real`s to get a new one, like this:

```tut
val seven = three + Real(4)
```

You can see that, because all of these values are constants, Rainier is able to immediately evaluate the addition. That won't always be true later on.

When you use a different numeric type in a position where we're expecting a Real, it will implicitly be wrapped into a constant. So you could also write this more simply:

```tut
val alsoSeven = three + 4
```

This all feels a lot like working with numeric values (which is intentional), but as we go further, it'll be important to remember that a `Real` object is always a function. In this case, an object like `three` represents a function that completely ignores any parameters and just returns the value `3`. Let's introduce a notation for talking about this which we'll keep using later on:

```
three(Θ) = 3
```

## Variable

The other kind of `Real` you can create directly is a `Variable` (though if you're using the high-level API, you wouldn't normally do this - instead you'd do it indirectly by calling `param` on a continuous `Distribution` object).

A `Variable` doesn't really need anything other than its own identity (they don't need explicit names, for example). So you create them like this:

```tut
val x = new Variable
```

Taken by itself, `x` here represents a function that extracts a specific single parameter from the (conceptually infinite) parameter vector. We can denote that like this:

```
x(Θ) = Θ_x
```

Just like before, we can do basic arithmetic on `x` to get a new `Real`:

```tut
val xTwo = x * 2
```

Unlike before, this can't just return a constant, because we don't know the value of `x`. Instead, we end up with a function like this:

```
xTwo(Θ) = Θ_x * 2
```

However, `Real` will still try to immediately evaluate as much as it can. One way to see this is to divide the 2 back out; if you look carefully, you can see that we recover the original `Variable` object:

```tut
xTwo / 2
```

We can also construct functions that depend on multiple variables, as you'd expect:

```tut
val y = new Variable
val xy = x * y
```

This, of course, represents the function `xy(Θ) = Θ_x * Θ_y`.

## Evaluator and Compiler

```tut:silent
import com.stripe.rainier.compute.{Evaluator,Compiler}
```

If you want to actually evaluate a function, you have to provide values for the parameters somehow. (Again, with the higher level API, you'd be unlikely to ever do this directly). One way to do that is by constructing an `Evaluator` object, and giving it a `Map[Variable,Double]` with the parameter values you want. For example:

```tut
val eval = new Evaluator(Map(x -> 3.0, y -> 2.0))
eval.toDouble(x)
eval.toDouble(xTwo)
eval.toDouble(xy)
```

Or if you want to use a different set of parameter values:

```tut
val eval2 = new Evaluator(Map(x -> 1.0, y -> -1.0))
eval2.toDouble(x)
eval2.toDouble(xTwo)
eval2.toDouble(xy)
```

However, this method of evaluating a `Real` is relatively slow. If you want to be more efficient, you need to use the `Compiler` to produce an `Array[Double] => Double` for a fixed set of inputs and outputs. So if we want a function from `(x,y)` to `xy`, we can make one like this:

```tut
val xyFn = Compiler.default.compile(List(x,y), xy)
```

And now we can use it as many times as we want:

```tut
xyFn(Array(3.0,2.0))
xyFn(Array(1.0,-1.0))
```

This, of course, isn't a very exciting function - it just multiplies two numbers! But no matter how complex a function you build up, the `Compiler` will generate and load optimized JVM bytecode to evaluate it as quickly as possible with no non-primitive objects or allocations involved.

## Tracing

```tut
import com.stripe.rainier.trace._
```

If we want to get an idea of what kind of code is generated for a more complex function, we can use `Tracer` to print out Java code that is equivalent to it. (This is *not* to imply that the bytecode is actually produced by generating Java code and then compiling that with javac, which would be much too slow; it's just a more convenient representation for humans than the bytecode itself).

For example, if we construct something like this:

```tut
val f1 = (x - 3).pow(2) + (x + 3).pow(2)
```

We can see what kind of code it would produce like this:

```tut
Tracer.default(f1)
```

Ignore the `output` and `globals` stuff for now and skip down to the end where the action is. There are a couple of interesting things going on:

* the compiler has chosen to expand out the `pow(2)` operations to simplify their sum
* it's figured out that, after expanding, we end up with two `x^2 + 9` terms and stores that in a temp var for reuse
* it's also figured out that there's a `6x` term that cancels with a `-6x` term, and so omits both of them

These kinds of simplifications get even more interesting when you're working with a large amount of constant data (like a training set). For example, let's say we want to compute the mean squared error of `x` with respect to some set of data points:

```tut
val data = 1.to(100).toList
val err = data.map{n => (x - n).pow(2)}.reduce(_ + _) / data.size
```

If we trace this, we'll see that `Real` was able to evaluate this completely enough to fully collapse out the list of data points, and the final calculation just involves an `x` and `x^2` term:

```tut
Tracer.default(err)
```

Of course, this won't always work. For example, if we change from L2 to L1 error by changing the `pow(2)` to `abs`, there's no way to simplify it:

```tut
val err2 = data.map{n => (x - n).abs}.reduce(_ + _) / data.size
Tracer.default(err2)
```

You can also see here that when the calculation gets big enough, it gets split into multiple private methods. This is because the JVM's JIT has a maximum method size; splitting up the calculation helps keep the whole thing under that limit and running fast. The `apply` method becomes the entry point to the whole calculation; if values need to be shared between the different methods, the `globals` array is used.

## Gradients

In optimization and sampling, you not only want to evaluate a function, but also its gradient with respect to the parameter vector. `Real` provides a `gradient` method which will return a sequence of new `Real` objects (one for each variable that it references).

As a very simple example, let's take the gradient of `xTwo`:

```tut
xTwo.gradient
```

This function only references one variable, `x`, and so there's only one element in the gradient; and because the function is just `x * 2`, the derivative with respect to `x` is just `2`. Feel free to play around with more complex functions (and remember that you can use `trace` to inspect what's actually going on inside any given `Real`).

It's also worth knowing that the `Compiler` has a `compileGradient` method which will return a single `Array[Double] => Array[Double]` function which computes the value and its gradient at the same time. You can also see a trace that includes the gradients by using `Tracer.gradient` instead of `Tracer.default`.
