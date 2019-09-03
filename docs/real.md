# com.stripe.rainier.Real

At the heart of Rainier is the `Real` type, which represents a function from some vector Θ of real-valued parameters to a single real-valued output. This document will provide a brief introduction to it; if you'd like to follow along, `sbt "project rainierCore" console` will get you to a Scala REPL.

Let's start by importing `Real` and its one public subclass, `Variable`:
```scala
import com.stripe.rainier.compute.{Real,Variable}
```

## Constant

The very simplest kind of `Real` you can create is a function that always returns a constant value. You can create this by passing a double (or other numeric type) into `Real()`:

```scala
scala> val three = Real(3)
three: com.stripe.rainier.compute.Real = Constant(3.0)
```

`Real` provides the basic arithmetic functions, so for example you can sum two `Real`s to get a new one, like this:

```scala
scala> val seven = three + Real(4)
seven: com.stripe.rainier.compute.Real = Constant(7.0)
```

You can see that, because all of these values are constants, Rainier is able to immediately evaluate the addition. That won't always be true later on.

When you use a different numeric type in a position where we're expecting a Real, it will implicitly be wrapped into a constant. So you could also write this more simply:

```scala
scala> val alsoSeven = three + 4
alsoSeven: com.stripe.rainier.compute.Real = Constant(7.0)
```

This all feels a lot like working with numeric values (which is intentional), but as we go further, it'll be important to remember that a `Real` object is always a function. In this case, an object like `three` represents a function that completely ignores any parameters and just returns the value `3`. Let's introduce a notation for talking about this which we'll keep using later on:

```
three(Θ) = 3
```

## Variable

The other kind of `Real` you can create directly is a `Variable` (though if you're using the high-level API, you wouldn't normally do this - instead you'd do it indirectly by calling `param` on a continuous `Distribution` object).

A `Variable` doesn't really need anything other than its own identity (they don't need explicit names, for example). So you create them like this:

```scala
scala> val x = Real.variable()
x: com.stripe.rainier.compute.Variable = com.stripe.rainier.compute.Variable@6896d488
```

Taken by itself, `x` here represents a function that extracts a specific single parameter from the (conceptually infinite) parameter vector. We can denote that like this:

```
x(Θ) = Θ_x
```

Just like before, we can do basic arithmetic on `x` to get a new `Real`:

```scala
scala> val xTwo = x * 2
xTwo: com.stripe.rainier.compute.Real = com.stripe.rainier.compute.Line@60278bda
```

Unlike before, this can't just return a constant, because we don't know the value of `x`. Instead, we end up with a function like this:

```
xTwo(Θ) = Θ_x * 2
```

However, `Real` will still try to immediately evaluate as much as it can. One way to see this is to divide the 2 back out; if you look carefully, you can see that we recover the original `Variable` object:

```scala
scala> xTwo / 2
res0: com.stripe.rainier.compute.Real = com.stripe.rainier.compute.Variable@7ac1b880
```

We can also construct functions that depend on multiple variables, as you'd expect:

```scala
scala> val y = Real.variable()
y: com.stripe.rainier.compute.Variable = com.stripe.rainier.compute.Variable@5e3849b2

scala> val xy = x * y
xy: com.stripe.rainier.compute.Real = LogLine(Many(Map(com.stripe.rainier.compute.Variable@7ac1b880 -> 1.0, com.stripe.rainier.compute.Variable@7ed2620b -> 1.0),List(com.stripe.rainier.compute.Variable@7ac1b880, com.stripe.rainier.compute.Variable@7ed2620b)))
```

This, of course, represents the function `xy(Θ) = Θ_x * Θ_y`.

## Evaluator and Compiler

```scala
import com.stripe.rainier.compute.{Evaluator,Compiler}
```

If you want to actually evaluate a function, you have to provide values for the parameters somehow. (Again, with the higher level API, you'd be unlikely to ever do this directly). One way to do that is by constructing an `Evaluator` object, and giving it a `Map[Variable,Double]` with the parameter values you want. For example:

```scala
scala> val eval = new Evaluator(Map(x -> 3.0, y -> 2.0))
eval: com.stripe.rainier.compute.Evaluator = com.stripe.rainier.compute.Evaluator@194a0b89

scala> eval.toDouble(x)
res1: Double = 3.0

scala> eval.toDouble(xTwo)
res2: Double = 6.0

scala> eval.toDouble(xy)
res3: Double = 6.0
```

Or if you want to use a different set of parameter values:

```scala
scala> val eval2 = new Evaluator(Map(x -> 1.0, y -> -1.0))
eval2: com.stripe.rainier.compute.Evaluator = com.stripe.rainier.compute.Evaluator@60ac2c07

scala> eval2.toDouble(x)
res4: Double = 1.0

scala> eval2.toDouble(xTwo)
res5: Double = 2.0

scala> eval2.toDouble(xy)
res6: Double = -1.0
```

However, this method of evaluating a `Real` is relatively slow. If you want to be more efficient, you need to use the `Compiler` to produce an `Array[Double] => Double` for a fixed set of inputs and outputs. So if we want a function from `(x,y)` to `xy`, we can make one like this:

```scala
scala> val xyFn = Compiler.default.compile(List(x,y), xy)
xyFn: Array[Double] => Double = com.stripe.rainier.compute.Compiler$$Lambda$5743/620029269@3798e377
```

And now we can use it as many times as we want:

```scala
scala> xyFn(Array(3.0,2.0))
res7: Double = 6.0

scala> xyFn(Array(1.0,-1.0))
res8: Double = -1.0
```

This, of course, isn't a very exciting function - it just multiplies two numbers! But no matter how complex a function you build up, the `Compiler` will generate and load optimized JVM bytecode to evaluate it as quickly as possible with no non-primitive objects or allocations involved.

## Tracing

```scala
import com.stripe.rainier.trace._
```

If we want to get an idea of what kind of code is generated for a more complex function, we can use `Tracer` to print out Java code that is equivalent to it. (This is *not* to imply that the bytecode is actually produced by generating Java code and then compiling that with javac, which would be much too slow; it's just a more convenient representation for humans than the bytecode itself).

For example, if we construct something like this:

```scala
scala> val f1 = (x - 3).pow(2) + (x + 3).pow(2)
f1: com.stripe.rainier.compute.Real = com.stripe.rainier.compute.Line@4bd7cd3d
```

We can see what kind of code it would produce like this:

```scala
scala> Tracer.default(f1)
public class CompiledFunction$21
implements com.stripe.rainier.ir.CompiledFunction
 {
    public CompiledFunction$21() {
    }

    public final double output(double[] arrd, double[] arrd2, int n) {
        switch (n) {
            case 0: {
                break;
            }
            default: {
                throw null;
            }
        }
        return CompiledFunction$21$density$9._941((double[])arrd, (double[])arrd2);
    }

    public int numInputs() {
        return 1;
    }

    public int numGlobals() {
        return 0;
    }

    public int numOutputs() {
        return 1;
    }
}
public class CompiledFunction$21$density$9
 {
    public static final double _941(double[] arrd, double[] arrd2) {
        double d = 9.0 + arrd[0] * arrd[0];
        return d + d;
    }
}
```

Ignore the `output` and `globals` stuff for now and skip down to the end where the action is. There are a couple of interesting things going on:

* the compiler has chosen to expand out the `pow(2)` operations to simplify their sum
* it's figured out that, after expanding, we end up with two `x^2 + 9` terms and stores that in a temp var for reuse
* it's also figured out that there's a `6x` term that cancels with a `-6x` term, and so omits both of them

These kinds of simplifications get even more interesting when you're working with a large amount of constant data (like a training set). For example, let's say we want to compute the mean squared error of `x` with respect to some set of data points:

```scala
scala> val data = 1.to(100).toList
data: List[Int] = List(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 58, 59, 60, 61, 62, 63, 64, 65, 66, 67, 68, 69, 70, 71, 72, 73, 74, 75, 76, 77, 78, 79, 80, 81, 82, 83, 84, 85, 86, 87, 88, 89, 90, 91, 92, 93, 94, 95, 96, 97, 98, 99, 100)

scala> val err = data.map{n => (x - n).pow(2)}.reduce(_ + _) / data.size
err: com.stripe.rainier.compute.Real = com.stripe.rainier.compute.Line@525614b2
```

If we trace this, we'll see that `Real` was able to evaluate this completely enough to fully collapse out the list of data points, and the final calculation just involves an `x` and `x^2` term:

```scala
scala> Tracer.default(err)
public class CompiledFunction$22
implements com.stripe.rainier.ir.CompiledFunction
 {
    public CompiledFunction$22() {
    }

    public final double output(double[] arrd, double[] arrd2, int n) {
        switch (n) {
            case 0: {
                break;
            }
            default: {
                throw null;
            }
        }
        return CompiledFunction$22$density$9._946((double[])arrd, (double[])arrd2);
    }

    public int numInputs() {
        return 1;
    }

    public int numGlobals() {
        return 0;
    }

    public int numOutputs() {
        return 1;
    }
}
public class CompiledFunction$22$density$9
 {
    public static final double _946(double[] arrd, double[] arrd2) {
        return 3383.5 + arrd[0] * -101.0 + arrd[0] * arrd[0];
    }
}
```

Of course, this won't always work. For example, if we change from L2 to L1 error by changing the `pow(2)` to `abs`, there's no way to simplify it:

```scala
scala> val err2 = data.map{n => (x - n).abs}.reduce(_ + _) / data.size
err2: com.stripe.rainier.compute.Real = com.stripe.rainier.compute.Line@2350d1c0

scala> Tracer.default(err2)
public class CompiledFunction$23
implements com.stripe.rainier.ir.CompiledFunction
 {
    public CompiledFunction$23() {
    }

    public final double output(double[] arrd, double[] arrd2, int n) {
        switch (n) {
            case 0: {
                break;
            }
            default: {
                throw null;
            }
        }
        return CompiledFunction$23$density$12._1251((double[])arrd, (double[])arrd2);
    }

    public int numInputs() {
        return 1;
    }

    public int numGlobals() {
        return 0;
    }

    public int numOutputs() {
        return 1;
    }
}
public class CompiledFunction$23$density$12
 {
    public CompiledFunction$23$density$12() {
    }

    public static final double _1251(double[] arrd, double[] arrd2) {
        return (CompiledFunction$23$density$12._1250((double[])arrd, (double[])arrd2) + java.lang.Math.abs(-24.0 + arrd[0]) + java.lang.Math.abs(-23.0 + arrd[0]) + java.lang.Math.abs(-22.0 + arrd[0]) + java.lang.Math.abs(-21.0 + arrd[0]) + java.lang.Math.abs(-20.0 + arrd[0]) + java.lang.Math.abs(-19.0 + arrd[0]) + java.lang.Math.abs(-18.0 + arrd[0]) + java.lang.Math.abs(-17.0 + arrd[0]) + java.lang.Math.abs(-16.0 + arrd[0]) + java.lang.Math.abs(-15.0 + arrd[0]) + java.lang.Math.abs(-14.0 + arrd[0]) + java.lang.Math.abs(-13.0 + arrd[0]) + java.lang.Math.abs(-12.0 + arrd[0]) + java.lang.Math.abs(-11.0 + arrd[0]) + java.lang.Math.abs(-10.0 + arrd[0]) + java.lang.Math.abs(-9.0 + arrd[0]) + java.lang.Math.abs(-8.0 + arrd[0]) + java.lang.Math.abs(-7.0 + arrd[0]) + java.lang.Math.abs(-6.0 + arrd[0]) + java.lang.Math.abs(-5.0 + arrd[0]) + java.lang.Math.abs(-4.0 + arrd[0]) + java.lang.Math.abs(-3.0 + arrd[0]) + java.lang.Math.abs(-1.0 + arrd[0]) + java.lang.Math.abs(-2.0 + arrd[0])) * 0.01;
    }

    public static final double _1250(double[] arrd, double[] arrd2) {
        return CompiledFunction$23$density$12._1249((double[])arrd, (double[])arrd2) + java.lang.Math.abs(-49.0 + arrd[0]) + java.lang.Math.abs(-48.0 + arrd[0]) + java.lang.Math.abs(-47.0 + arrd[0]) + java.lang.Math.abs(-46.0 + arrd[0]) + java.lang.Math.abs(-45.0 + arrd[0]) + java.lang.Math.abs(-44.0 + arrd[0]) + java.lang.Math.abs(-43.0 + arrd[0]) + java.lang.Math.abs(-42.0 + arrd[0]) + java.lang.Math.abs(-41.0 + arrd[0]) + java.lang.Math.abs(-40.0 + arrd[0]) + java.lang.Math.abs(-39.0 + arrd[0]) + java.lang.Math.abs(-38.0 + arrd[0]) + java.lang.Math.abs(-37.0 + arrd[0]) + java.lang.Math.abs(-36.0 + arrd[0]) + java.lang.Math.abs(-35.0 + arrd[0]) + java.lang.Math.abs(-34.0 + arrd[0]) + java.lang.Math.abs(-33.0 + arrd[0]) + java.lang.Math.abs(-32.0 + arrd[0]) + java.lang.Math.abs(-31.0 + arrd[0]) + java.lang.Math.abs(-30.0 + arrd[0]) + java.lang.Math.abs(-29.0 + arrd[0]) + java.lang.Math.abs(-28.0 + arrd[0]) + java.lang.Math.abs(-27.0 + arrd[0]) + java.lang.Math.abs(-26.0 + arrd[0]) + java.lang.Math.abs(-25.0 + arrd[0]);
    }

    public static final double _1249(double[] arrd, double[] arrd2) {
        return CompiledFunction$23$density$12._1248((double[])arrd, (double[])arrd2) + java.lang.Math.abs(-74.0 + arrd[0]) + java.lang.Math.abs(-73.0 + arrd[0]) + java.lang.Math.abs(-72.0 + arrd[0]) + java.lang.Math.abs(-71.0 + arrd[0]) + java.lang.Math.abs(-70.0 + arrd[0]) + java.lang.Math.abs(-69.0 + arrd[0]) + java.lang.Math.abs(-68.0 + arrd[0]) + java.lang.Math.abs(-67.0 + arrd[0]) + java.lang.Math.abs(-66.0 + arrd[0]) + java.lang.Math.abs(-65.0 + arrd[0]) + java.lang.Math.abs(-64.0 + arrd[0]) + java.lang.Math.abs(-63.0 + arrd[0]) + java.lang.Math.abs(-62.0 + arrd[0]) + java.lang.Math.abs(-61.0 + arrd[0]) + java.lang.Math.abs(-60.0 + arrd[0]) + java.lang.Math.abs(-59.0 + arrd[0]) + java.lang.Math.abs(-58.0 + arrd[0]) + java.lang.Math.abs(-57.0 + arrd[0]) + java.lang.Math.abs(-56.0 + arrd[0]) + java.lang.Math.abs(-55.0 + arrd[0]) + java.lang.Math.abs(-54.0 + arrd[0]) + java.lang.Math.abs(-53.0 + arrd[0]) + java.lang.Math.abs(-52.0 + arrd[0]) + java.lang.Math.abs(-51.0 + arrd[0]) + java.lang.Math.abs(-50.0 + arrd[0]);
    }

    public static final double _1248(double[] arrd, double[] arrd2) {
        return java.lang.Math.abs(-100.0 + arrd[0]) + java.lang.Math.abs(-99.0 + arrd[0]) + java.lang.Math.abs(-98.0 + arrd[0]) + java.lang.Math.abs(-97.0 + arrd[0]) + java.lang.Math.abs(-96.0 + arrd[0]) + java.lang.Math.abs(-95.0 + arrd[0]) + java.lang.Math.abs(-94.0 + arrd[0]) + java.lang.Math.abs(-93.0 + arrd[0]) + java.lang.Math.abs(-92.0 + arrd[0]) + java.lang.Math.abs(-91.0 + arrd[0]) + java.lang.Math.abs(-90.0 + arrd[0]) + java.lang.Math.abs(-89.0 + arrd[0]) + java.lang.Math.abs(-88.0 + arrd[0]) + java.lang.Math.abs(-87.0 + arrd[0]) + java.lang.Math.abs(-86.0 + arrd[0]) + java.lang.Math.abs(-85.0 + arrd[0]) + java.lang.Math.abs(-84.0 + arrd[0]) + java.lang.Math.abs(-83.0 + arrd[0]) + java.lang.Math.abs(-82.0 + arrd[0]) + java.lang.Math.abs(-81.0 + arrd[0]) + java.lang.Math.abs(-80.0 + arrd[0]) + java.lang.Math.abs(-79.0 + arrd[0]) + java.lang.Math.abs(-78.0 + arrd[0]) + java.lang.Math.abs(-77.0 + arrd[0]) + java.lang.Math.abs(-76.0 + arrd[0]) + CompiledFunction$23$density$12._1247((double[])arrd, (double[])arrd2);
    }

    public static final double _1247(double[] arrd, double[] arrd2) {
        return java.lang.Math.abs(-75.0 + arrd[0]);
    }
}
```

You can also see here that when the calculation gets big enough, it gets split into multiple private methods. This is because the JVM's JIT has a maximum method size; splitting up the calculation helps keep the whole thing under that limit and running fast. The `apply` method becomes the entry point to the whole calculation; if values need to be shared between the different methods, the `globals` array is used.

## Gradients

In optimization and sampling, you not only want to evaluate a function, but also its gradient with respect to the parameter vector. `Real` provides a `gradient` method which will return a sequence of new `Real` objects (one for each variable that it references).

As a very simple example, let's take the gradient of `xTwo`:

```scala
scala> xTwo.gradient
res12: List[com.stripe.rainier.compute.Real] = List(Constant(2.00))
```

This function only references one variable, `x`, and so there's only one element in the gradient; and because the function is just `x * 2`, the derivative with respect to `x` is just `2`. Feel free to play around with more complex functions (and remember that you can use `trace` to inspect what's actually going on inside any given `Real`).

It's also worth knowing that the `Compiler` has a `compileGradient` method which will return a single `Array[Double] => Array[Double]` function which computes the value and its gradient at the same time. You can also see a trace that includes the gradients by using `Tracer.gradient` instead of `Tracer.default`.
