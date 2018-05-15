# A Tour of Rainier's Core

Hello! This document will provide a quick tour through the most important parts of Rainier's core. If you'd like to follow along with this tour, `sbt console` will get you to a Scala REPL.

The `rainier.core` package defines some key traits like `Distribution`, `RandomVariable`, `Likelihood`, and `Predictor`. Let's import it, along with a `repl` package that gives us some useful utilities.

```scala
import rainier.core._
import rainier.repl._
```

Together, those traits let you define bayesian models in Rainier. To throw you a little bit into the deep end, here's a simple linear regression that we'll return to later. This probably won't entirely make sense yet. That's ok. Hopefully it will serve as a bit of a roadmap that put the rest of the tour in context as we build up to it. 

```scala
val data =  List(((0,8), (1,12), (2,16), (3,20), (4,21), (5,31), (6,23), (7,33), (8,31), (9,33), (10,36), (11,42), (12,39), (13,56), (14,55), (15,63), (16,52), (17,66), (18,52), (19,80), (20,71))

val model = for {
    slope <- LogNormal(0,1).param
    intercept <- LogNormal(0,1).param
    regression <- Predictor.from{x: Int => Poisson(x*slope + intercept)}.fit(data)
} yield regression
```

## Distribution

The starting point for almost any work in Rainier will be some object that implements
`rainier.core.Distribution[T]`.

Rainier implements various familiar families of probability distributions like the [Normal](https://en.wikipedia.org/wiki/Normal_distribution) distribution,
the [Uniform](https://en.wikipedia.org/wiki/Uniform_distribution_(continuous)) distribution, and the [Poisson](https://en.wikipedia.org/wiki/Poisson_distribution) distribution. You will find these three in [Continuous.scala](rainier-core/src/main/scala/core/Continuous.scala) and [Discrete.scala](rainier-core/src/main/scala/core/Discrete.scala) - along with a few more, and we'll keep adding them as the need arises.

You construct a distributions from its parameters, usually with the `apply` method on the object representing its family. So for example, this is a normal distribution with a mean of 0 and a standard deviation of 1:

```scala
scala> val normal: Distribution[Double] = Normal(0,1)
normal: rainier.core.Distribution[Double] = rainier.core.Injection$$anon$1@17e6ddde
```

In Rainier, `Distribution` objects play three different roles. Most distributions (those that are continuous, like `Normal`), implement `param`, and all distributions implement `fit` and `generator`. Each of these methods is central to one of the three stages of building a model in Rainier: defining your parameters and their priors; fitting the parameters to some observed data; and using the fit parameters to generate samples of some posterior distribution of interest. We'll start by exploring each of these in turn.

## `param` and `RandomVariable`

Every model in Rainier has some parameters whose values are unknown (if that's not true for your model, you don't need an inference library!).  The first step in constructing a model is to set up these parameters. 

You create a parameter by calling `param` on the distribution that represents the prior for that parameter. `param` is only implemented for continuous distributions (which extend `Continuous`, which extends `Distribution[Double]`). As you might be able to tell from those types: in Rainier, all parameters are continuous real scalar values. There's no support for discrete model parameters, and there's no support (or need) for explicit vectorization.

Let's use that same `Normal(0,1)` distribution as a prior for a new parameter:

```scala
scala> val x = Normal(0,1).param
x: rainier.core.RandomVariable[rainier.compute.Real] = rainier.core.RandomVariable@3d5d6de1
```

You can see that `x`s type is `RandomVariable[Real]`. `RandomVariable` pairs a type of value (in this case, a real number) with some knowledge of the relative probability density of different values of that type. We can use this knowledge to produce a sample of these possible values:

```scala
scala> x.sample()
res0: List[Double] = List(-0.9854576712879818, -1.0987929240476104, 1.2367848934068615, 0.1741437543526066, -0.147822680140413, 0.25041186311443187, 0.6854906516335244, 0.5526014396630073, -0.37935136456766727, 1.0450475634634535, -0.46989956466505167, 0.2645555737346893, 0.3149728138913791, 0.4560375613613677, 1.0646829609374024, -0.20484301527906257, -0.42109684702184313, 0.085791745502656, -0.17208687544482115, 0.7660251230224684, 0.7828776895314813, 0.19836122721832838, 1.6122055826574497, -0.23848008497909046, 0.31381508150995785, 1.5653796975167336, 0.3274973873382516, 0.07895782986167266, 0.6508216711516128, -0.05470341975161774, 2.2875363495155927, 0.7849500841053795, -0.8543996891885697, 1.7666532982915908, -0.867472712558373, 0.22451093823643176, 0.09...
```

Each element of the list above represents a single sample from the distribution over `x`. It's easier to understand these if we plot them as a histogram:

```scala
scala> plot1D(x.sample())
     198 |                                                                                
         |                                      ·   ∘  ○                                  
         |                               ·      ○  ·○  ○ ∘○ ○                             
         |                          ·    ○    ○ ○·∘○○∘∘○·○○ ○∘  ·                         
         |                          ○  ○ ○○∘○○○∘○○○○○○○○○○○ ○○∘○○                         
     148 |                        · ○∘∘○·○○○○○○○○○○○○○○○○○○∘○○○○○∘    ∘                   
         |                     ·· ○·○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○    ○                   
         |                     ○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○ · ○○                  
         |                   · ○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○∘○∘○○  ·               
         |               ○  ·○ ○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○  ○               
      99 |              ·○  ○○ ○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○∘∘○  ·            
         |              ○○∘∘○○·○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○ ∘○ ∘          
         |        ∘     ○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○ ○ ∘        
         |        ○   ∘○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○∘○○○○       
         |    ∘   ○○··○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○       
      49 |    ○○○ ○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○·     
         |  ·∘○○○·○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○∘○   
         |  ○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○∘∘ 
         |·○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○∘
         |○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○
       0 |○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○
         |--------|--------|--------|--------|--------|--------|--------|--------|--------
       -2.00    -1.56    -1.12    -0.69    -0.25     0.19     0.62     1.06     1.50  
```

Since the only information we have about `x` so far is its `Normal` prior, this distribution unsurprisingly looks  normal. Later we'll see how to update our beliefs about `x` based on observational data.

For now, though, let's explore what we can do just with priors. `RandomVariable` has more than just `sample`: you can use the familiar collection methods like `map`, `flatMap`, and `zip` to transform and combine the parameters you create. For example, we can create a log-normal distribution just by mapping over `e^x`:

```scala
scala> val ex = x.map(_.exp)
ex: rainier.core.RandomVariable[rainier.compute.Real] = rainier.core.RandomVariable@44705ecc

scala> plot1D(ex.sample())
     570 |                                                                                
         |   ∘                                                                            
         |   ○                                                                            
         |   ○○                                                                           
         |  ○○○∘                                                                          
     420 | ∘○○○○·                                                                         
         |·○○○○○○ ·                                                                       
         |○○○○○○○∘○                                                                       
         |○○○○○○○○○ ∘                                                                     
         |○○○○○○○○○∘○                                                                     
     280 |○○○○○○○○○○○∘∘                                                                   
         |○○○○○○○○○○○○○                                                                   
         |○○○○○○○○○○○○○○∘··                                                               
         |○○○○○○○○○○○○○○○○○                                                               
         |○○○○○○○○○○○○○○○○○○·                                                             
     140 |○○○○○○○○○○○○○○○○○○○∘∘·     ·                                                    
         |○○○○○○○○○○○○○○○○○○○○○○··○··○                                                    
         |○○○○○○○○○○○○○○○○○○○○○○○○○○○○·· ∘                                                
         |○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○· ·                                           
         |○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○∘○∘∘∘∘∘ ·∘  ·                              
       0 |○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○∘○○∘∘∘∘∘·∘∘······∘·∘∘∘∘··
         |--------|--------|--------|--------|--------|--------|--------|--------|--------
        0.13     0.85     1.56     2.28     2.99     3.71     4.42     5.13     5.85  
```

Or we can create another `Normal` parameter and zip the two together to produce a 2D gaussian. Since there's nothing relating these two parameters to each other, you can see in the plot that they're completely independent.

```scala
scala> val y = Normal(0,1).param
y: rainier.core.RandomVariable[rainier.compute.Real] = rainier.core.RandomVariable@1c297a72

scala> val xy = x.zip(y)
xy: rainier.core.RandomVariable[(rainier.compute.Real, rainier.compute.Real)] = rainier.core.RandomVariable@10ef3bf8

scala> plot2D(xy.sample())
     1.9 |          ·· ·  ·   ·  ·· · · ····  ··· ·· ·· ·  ····· ·· ···  ·  ·  ·          
         |··      ·     ·  ····  ········· ······ ····· ········ ····· ·· ···    ·      · 
         | ∘ ··  ·   · ···  ···  ··∘·∘∘·· ·····   ·······∘················  ·        ··· ·
         |····· ······· ············· ∘····∘···∘·····∘··∘········· ·∘····· ·····∘·· ··    
         | · · ·· ·  ····· ··∘··········∘····∘··∘·····∘··∘···················· ····· ·  ··
     1.0 |······ ·············∘·······∘··∘∘···∘··∘··∘∘∘∘·∘∘·∘······∘··············  · · ··
         |·· ···················∘·∘·∘∘∘··∘·∘·○○∘∘∘∘··∘··∘···∘·∘··∘·∘∘······∘············  
         |······· · ·· ·············∘∘∘∘··∘··∘·∘·○·∘·∘··∘···∘∘∘·∘·····∘··∘···∘········ ·· 
         |· ···∘········∘∘········∘∘∘∘∘·∘∘∘∘·∘∘∘∘∘∘··○∘·∘∘∘··∘∘·∘∘∘∘○····∘··············· 
         |  · ············∘····∘·∘···∘∘∘∘····∘∘∘·∘∘·∘·○∘∘···∘∘∘·∘∘∘·∘·∘····∘······· ······
     0.0 | ···· ····∘··∘∘·∘····∘∘∘∘·∘○∘○·∘∘∘∘·∘∘∘∘·∘∘∘○∘∘○∘∘○∘··∘··∘∘∘○··∘··········∘···· 
         |··············∘∘∘∘∘∘····∘∘·○∘∘∘∘∘∘∘∘·∘∘·∘∘○∘○∘∘··○○∘∘∘∘∘∘∘∘··∘∘∘∘···········  ·∘
         |· · ·············∘∘········∘○∘∘∘∘∘∘·∘∘∘∘∘∘∘∘∘∘∘·○∘∘·∘∘∘∘∘∘∘∘∘··∘··∘∘··∘∘∘····· ·
         |·  ·· ····· ·······∘····∘∘·∘∘·∘····∘·∘∘∘○∘·○∘·∘○∘∘∘···∘∘∘∘··∘····∘∘··∘∘ ·· ·····
         |  ···· ··· ····∘···∘···∘∘··∘∘∘·∘∘∘∘∘···∘··∘∘○∘∘○∘∘∘·∘∘·∘···∘··∘··∘∘········· ·  
    -0.9 |··∘··∘············· ·········∘··∘··∘∘∘···∘∘∘∘·∘∘∘·∘∘···∘·∘·∘······ ····· ·  ····
         |····· · · ·····  ∘·····∘·······∘··∘··∘·∘○∘∘·∘∘·∘··∘············· ········· ··· ·
         |   ·  ∘    ···  ·············∘··∘·······∘······∘················ ··········· ···
         |     ·· · ··  · ·· ·········· ··∘·∘····∘··∘···· ·············· ·········∘    · ·
         |     ·   ·  · · ··  ··· ··· ·········· ∘······ ∘········· ····· ···  ·· · ··    
    -1.9 |  ··  ·      ··· ········ · ·····  ·········· ··· ··········· ·   ···   ·   ·   
         |--------|--------|--------|--------|--------|--------|--------|--------|--------
       -1.99    -1.53    -1.08    -0.62    -0.17     0.29     0.74     1.20     1.65  
```

Finally, we can use `flatMap` to create two parameters that *are* related to each other. In particular, we'll create a new parameter `z` that we believe to be very close to `x`: its prior is a `Normal` centered on `x` with a very small standard deviation. We'll then make a 2D plot of `(x,z)` to see the relationship.

```scala
scala> val z = x.flatMap{a => Normal(a, 0.1).param}
z: rainier.core.RandomVariable[rainier.compute.Real] = rainier.core.RandomVariable@391cd974

scala> val xz = x.zip(z)
xz: rainier.core.RandomVariable[(rainier.compute.Real, rainier.compute.Real)] = rainier.core.RandomVariable@6af513e5

scala> plot2D(xz.sample())
     1.9 |                                                                             ···
         |                                                                          ······
         |                                                                     ···········
         |                                                                · ·····∘·····   
         |                                                             ····∘∘∘∘····       
     0.9 |                                                      ·· ····∘∘∘∘∘·····         
         |                                                   ·····∘∘∘∘∘∘·····             
         |                                                ····∘∘○○∘∘····                  
         |                                             ···∘∘○○∘∘····                      
         |                                        ···∘∘○○∘∘∘····                          
    -0.1 |                                   ·····∘○○○○∘····                              
         |                               ·····∘○○○○∘···                                   
         |                          ·· ···∘∘○○∘∘····                                      
         |                       ····∘∘∘∘○∘∘··                                            
         |                   ·····∘∘○∘∘·····                                              
    -1.0 |             ·······∘∘∘○∘····                                                   
         |          · ·····∘∘∘∘·····                                                      
         |       ······∘∘·∘···                                                            
         | ·   ······∘····                                                                
         |·········· ·                                                                    
    -2.0 |········                                                                        
         |--------|--------|--------|--------|--------|--------|--------|--------|--------
       -1.94    -1.52    -1.10    -0.68    -0.27     0.15     0.57     0.99     1.41  
```

You can see that although we still sample from the full range of `x` values, for any given sample, `x` and `z` are quite close to each other.

A note about the `Real` type: you may have noticed that, for example, `x` is a `RandomVariable[Real]` or that `normal.mean` was a `Real`. What's that? `Real` is a special numeric type used by Rainier to represent your model's parameter values, and any values derived from those parameters (if you do arithmetic on a `Real`, you get back another `Real`). It's also the type that all the distribution objects like `Normal` expect to be parameterized with, so that you can chain things together like we did for `z` - though you can also use regular `Int` or `Double` values here and they'll get wrapped automatically. `Real` is, by design, an extremely restricted type: it only supports the 4 basic arithmetic operations, `log`, and `exp`. Restricting it in this way helps keep Rainier models as efficient to perform inference on as possible. At sampling time, however, any `Real` outputs are converted to `Double` so that you can work with them from there.

## `fit`

Just like every Rainier model has one more more parameters with priors, every Rainier model uses some observational data to update our belief about the values of those parameters (again, if your model doesn't have this, you're probably using the wrong library).

Let's say that we have some data that represents the last week's worth of sales on a website, at (we believe) a constant daily rate, and we'd like to know how many sales we might get tomorrow. Here's our data:

```scala
scala> val sales = List(4, 4, 7, 11, 8, 12, 10)
sales: List[Int] = List(4, 4, 7, 11, 8, 12, 10)
```

We can model the number of sales we get on each day as a poisson distribution parameterized by the underlying rate. For example, looking at that data, we might guess that it came from a `Poisson(9)` or `Poisson(10)`. We can test those hypotheses by using the `fit` method available on all `Distribution` objects. Since `Poisson` is a `Distribution[Int]`, its `fit` will accept either `Int` or `Seq[Int]`, and return a `RandomVariable` which encodes the likelihood of the provided data being produced by that distribution. (We're not going to worry right now about what *kind* of `RandomVariable` it is, since we're only really interested in the likelihood).

```scala
scala> val poisson9: RandomVariable[_] = Poisson(9).fit(sales)
poisson9: rainier.core.RandomVariable[_] = rainier.core.RandomVariable@601eafae

scala> val poisson10: RandomVariable[_] = Poisson(10).fit(sales)
poisson10: rainier.core.RandomVariable[_] = rainier.core.RandomVariable@5abf758
```

Although it's almost never necessary, we can reach into any `RandomVariable` and get its current probability `density`:

```scala
scala> poisson9.density
res5: rainier.compute.Real = Constant(-18.03523006575617)
```

We can use this to find the likelihood ratio of these two hypotheses. Since the density is stored in log space, the best way to do this numerically is to subtract the logs first before exponentiating:

```scala
scala> val lr = (poisson9.density - poisson10.density).exp
lr: rainier.compute.Real = Constant(3.0035986601488043)
```

We can see here that our data is about 3x as likely to have come from a `Poisson(9)` as it is to have come from a `Poisson(10)`. We could keep doing this with a large number of values to build up a sense of the rate distribution, but it would be tedious, and we'd be ignoring any prior we had on the rate. Instead, the right way to do this in Rainier is to make the rate a parameter, and let the library do the hard work of exploring the space. Specifically, we can use `flatMap` to chain the creation of the parameter with the creation and fit of the `Poisson` distribution. Since we want our rate to be a positive number, and expect it to be more likely to be on the small side than the large side, the log-normal parameter we created earlier as `ex` seems like a reasonable choice.

```scala
scala> val poisson: RandomVariable[_] = ex.flatMap{r => Poisson(r).fit(sales)}
poisson: rainier.core.RandomVariable[_] = rainier.core.RandomVariable@649e898
```

By the way: before, when we looked at `poisson9.density`, the model had no parameters and so we got a constant value back. Now, since the model's density is a function of the parameter value, we get something more opaque back. This is why inspecting `density` is not normally useful.

```scala
scala> poisson.density
res6: rainier.compute.Real = rainier.compute.Line@348ec145
```

Instead, we can sample the quantity we're actually interested in. Let's recreate this model from the start, with the slightly friendlier `for` syntax:

```scala
scala> val rate = for {
     |     x <- Normal(0,1).param
     |     ex = x.exp
     |     _ <- Poisson(ex).fit(sales)
     | } yield ex
rate: rainier.core.RandomVariable[rainier.compute.Real] = rainier.core.RandomVariable@2b79ad17
```

Here we're creating our log-normal prior, using it as the rate on a Poisson distribution, fitting that distribution to the data, and then outputting the rate. Let's plot it!

```scala
scala> plot1D(rate.sample())
     220 |                                                                                
         |                                       ·                                        
         |                                      ·○ ○                                      
         |                            ·   ○· ∘  ○○ ○  ∘                                   
         |                            ○   ○○○○ ∘○○∘○·○○∘                                  
     170 |                       ∘  ○·○○  ○○○○○○○○○○○○○○·                                 
         |                       ○ ·○○○○ ·○○○○○○○○○○○○○○○○·○                              
         |                   ∘ ○∘○·○○○○○ ○○○○○○○○○○○○○○○○○○○∘                             
         |                  ·○∘○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○· ∘                          
         |                ∘ ○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○∘○ ○                        
     110 |              ∘ ○ ○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○·○  ○  ·                  
         |              ○ ○∘○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○ ·○ ∘○                  
         |          ·  ·○ ○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○·○○○○○· ··              
         |      ·  ∘○ ○○○∘○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○ ○○·             
         |      ○ ·○○·○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○ ○○○ ·∘          
      50 |   ·○ ○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○∘○○          
         |·  ○○∘○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○  ·      
         |○·○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○··○··    
         |○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○ ∘· 
         |○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○∘○○∘
       0 |○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○
         |--------|--------|--------|--------|--------|--------|--------|--------|--------
        5.76     6.23     6.69     7.15     7.61     8.08     8.54     9.00     9.46  
```

## `generator` and `Generator`

The rate parameter is nice to have, but what we originally said is that we wanted to predict how many sales to expect tomorrow. For that we need to plug the rate back into a Poisson distribution, but in this case, instead of fitting data *to* that distribution, we want to generate data *from* that distribution. You might think that we could do this with `param`, that is, with something like this:

```scala
for {
    x <- Normal(0,1).param
    ex = x.exp
    _ <- Poisson(ex).fit(sales)
    prediction <- Poisson(ex).param
} yield prediction
```

This has a couple of problems. First, it seems conceptually wrong to think of a prediction as a parameter: there is no true value of "how many sales will we have tomorrow" for us to infer, and we have no observational data that can influence our belief about its value; once we've derived the rate parameter, the prediction is purely generative. Second, as a practical matter, Rainier only supports continuous parameters, and here we need to generate discrete values, so `Poisson(ex).param` won't, in fact, compile.

Luckily, all distributions, continious or discrete, implement `generator`, which gives us what we need: a way to randomly generate new values from a distribution as part of the sampling process. Every `Distribution[T]` can give us a `Generator[T]`, and if we sample from a `RandomVariable[Generator[T]]`, we will get values of type `T`. (You can think of `Real` as being a special case that acts in this sense like a `Generator[Double]`).

Here's one way we could implement what we're looking for:

```scala
scala> val prediction = for {
     |     x <- Normal(0,1).param
     |     ex = x.exp
     |     _ <- Poisson(ex).fit(sales)    
     | } yield Poisson(ex).generator
prediction: rainier.core.RandomVariable[rainier.core.Generator[Int]{val requirements: Set[rainier.compute.Real]}] = rainier.core.RandomVariable@9803845
```

This is almost the same model as `rate` above, but instead of taking the real-valued `ex` rate parameter as the output, we're producing poisson-distributed integers. The samples look like this:

```scala
scala> prediction.sample()
res8: List[Int] = List(8, 5, 6, 7, 15, 6, 6, 5, 5, 10, 3, 8, 8, 8, 6, 6, 9, 9, 7, 9, 4, 3, 11, 7, 7, 4, 4, 11, 5, 8, 9, 10, 10, 5, 5, 7, 12, 6, 6, 8, 9, 5, 2, 4, 10, 11, 1, 6, 10, 13, 13, 2, 7, 4, 11, 8, 11, 16, 9, 7, 10, 4, 7, 12, 10, 8, 8, 6, 9, 10, 7, 9, 11, 8, 6, 13, 9, 13, 10, 8, 7, 13, 7, 5, 4, 9, 5, 8, 7, 4, 6, 8, 4, 8, 7, 11, 8, 9, 5, 5, 12, 15, 8, 9, 5, 9, 8, 11, 4, 5, 9, 5, 6, 6, 5, 3, 6, 5, 8, 8, 1, 10, 15, 7, 7, 8, 7, 3, 9, 11, 7, 10, 8, 11, 9, 4, 11, 10, 2, 14, 5, 7, 8, 13, 7, 9, 8, 9, 4, 5, 10, 9, 3, 4, 7, 10, 10, 10, 11, 7, 6, 6, 10, 7, 5, 5, 7, 7, 8, 9, 1, 10, 7, 10, 8, 5, 6, 7, 10, 8, 9, 5, 7, 6, 9, 12, 10, 10, 5, 10, 5, 6, 6, 8, 9, 7, 5, 9, 7, 8, 10, 7, 8, 5, 8, 12, 5, 11, 6, 11, 8, 6, 9, 7, 4, 8, 14, 6, 7, 8, 3, 4, 7, 2, 8, 9, 8, 7, 5, 11, 8,...
```

It's very common to want to generate new data that mimics the data you fit against. We've been carefully ignoring the the type of `RandomVariable` that `fit` returns, but in fact, it contains a `Generator` to do just that. That gives us another way to implement the same thing. (While we're at it, let's make use of the `LogNormal` distribution instead of rolling our own.)

```scala
scala> val prediction2 = for {
     |     ex <- LogNormal(0,1).param
     |     poisson <- Poisson(ex).fit(sales)    
     | } yield poisson.map(_.head)
prediction2: rainier.core.RandomVariable[rainier.core.Generator[Int]] = rainier.core.RandomVariable@2615f730
```  

There's a bit of a twist here: because we `fit` against a list of 7 data points, the generator returned from there will try to produce output of the same shape, with each sample having a sequence of 7 ints. Luckily, `Generator` has the usual `map` and `flatMap` methods, so we can fix that by grabbing just the first value with `head`.

Let's plot it this time:

```scala
scala> plot1D(prediction2.sample())
    1340 |                                                                                
         |                            ○                                                   
         |                     ∘      ○       ∘                                           
         |                     ○      ○       ○                                           
         |                     ○      ○       ○      ·                                    
    1000 |              ∘      ○      ○       ○      ○                                    
         |              ○      ○      ○       ○      ○                                    
         |              ○      ○      ○       ○      ○      ∘                             
         |              ○      ○      ○       ○      ○      ○                             
         |       ∘      ○      ○      ○       ○      ○      ○                             
     670 |       ○      ○      ○      ○       ○      ○      ○                             
         |       ○      ○      ○      ○       ○      ○      ○      ○                      
         |       ○      ○      ○      ○       ○      ○      ○      ○                      
         |       ○      ○      ○      ○       ○      ○      ○      ○                      
         |       ○      ○      ○      ○       ○      ○      ○      ○      ∘               
     330 |∘      ○      ○      ○      ○       ○      ○      ○      ○      ○               
         |○      ○      ○      ○      ○       ○      ○      ○      ○      ○       ·       
         |○      ○      ○      ○      ○       ○      ○      ○      ○      ○       ○       
         |○      ○      ○      ○      ○       ○      ○      ○      ○      ○       ○      ∘
         |○      ○      ○      ○      ○       ○      ○      ○      ○      ○       ○      ○
       0 |○      ○      ○      ○      ○       ○      ○      ○      ○      ○       ○      ○
         |--------|--------|--------|--------|--------|--------|--------|--------|--------
        3.0      4.2      5.5      6.7      8.0      9.2      10.5     11.7     13.0  
```

## `Likelihood` and `Predictor`

What if, instead of assuming that our website's sales are constant over time, we assume that they're growing at a constant rate? We might have data like the following, organized in (day, sales) pairs. (You may recognize this data from the start of the tour.)

```scala
scala> val data =  List((0,8), (1,12), (2,16), (3,20), (4,21), (5,31), (6,23), (7,33), (8,31), (9,33), (10,36), (11,42), (12,39), (13,56), (14,55), (15,63), (16,52), (17,66), (18,52), (19,80), (20,71))
data: List[(Int, Int)] = List((0,8), (1,12), (2,16), (3,20), (4,21), (5,31), (6,23), (7,33), (8,31), (9,33), (10,36), (11,42), (12,39), (13,56), (14,55), (15,63), (16,52), (17,66), (18,52), (19,80), (20,71))
```

Plotting the data gives a clear sense of the trend:

```scala
scala> plot2D(data)
      72 |                                                                                
         |                                                                                
         |                                                                      ○         
         |                                                             ○                  
         |                                                                                
      57 |                                                                                
         |                                                     ○   ○                      
         |                                                                  ○        ○    
         |                                                                                
         |                                                                                
      42 |                                            ○                                   
         |                                                ○                               
         |                                       ○                                        
         |                          ○        ○                                            
         |                 ○            ○                                                 
      27 |                                                                                
         |                                                                                
         |             ○        ○                                                         
         |        ○                                                                       
         |    ○                                                                           
      12 |○                                                                               
         |--------|--------|--------|--------|--------|--------|--------|--------|--------
        1.0      3.0      5.1      7.1      9.1      11.2     13.2     15.3     17.3  
```

How should we model this? We'll need to know what the rate was on day 0 (the intercept), as well as how fast the rate increases each day (the slope). We know they're both positive and not too large, so let's keep using LogNormal priors.

```scala
scala> val prior = for {
     |     slope <- LogNormal(0,1).param
     |     intercept <- LogNormal(0,1).param
     |     } yield (slope, intercept)
prior: rainier.core.RandomVariable[(rainier.compute.Real, rainier.compute.Real)] = rainier.core.RandomVariable@283e4f8
```

Now, for any given day `i`, we want to check the number of sales against `Poisson(intercept + slope*i)`. We could write some kind of recursive flatMap to build and fit each of these different distribution objects in turn, but this is a common enough pattern that Rainier already has something built in for it: `Predictor`. `Predictor` is not a `Distribution`, but instead wraps a function from `X => Distribution[Y]` for some `(X,Y)`; you can use this any time you have a dependent variable of type `Y` that you're modeling with some independent variables jointly represented as `X`. 

Like `Distribution`, `Predictor` implements `fit` (in fact, they both extend the `Likelihood` trait which provides that method). So we can create and use it like this:

```scala
scala> val regr = prior.flatMap {case (slope, intercept) =>
     |   Predictor.from{i: Int => Poisson(intercept + slope*i)}.fit(data) 
     | }
regr: rainier.core.RandomVariable[rainier.core.Generator[Seq[(Int, Int)]]] = rainier.core.RandomVariable@5a3764d9
```

As before, the return value from `fit` will be a `Generator` that tries to recreate the input data. In this case, it will keep the `X` the same but generate a new `Y` for each data point. If we flatten all of the samples out, we can plot them and compare to the observed data to get a sense of whether our model is any good.

```scala
scala> plot2D(regr.sample().flatten)
      78 |                                                       ·   ·   ·   ·   ·   ·   ·
         |                                           ·       ·   ·   ·   ·   ·   ·   ·   ·
         |                                           ·   ·   ·   ·   ·   ·   ·   ·   ·   ·
         |                                           ·   ·   ·   ·   ·   ·   ·   ·   ·   ·
         |                               ·   ·   ·   ·   ·   ·   ·   ·   ·   ·   ∘   ·   ·
      60 |   ·       ·   ·               ·   ·   ·   ·   ·   ·   ·   ·   ·   ·   ·   ·   ·
         |·  ·   ·   ·       ·           ·   ·   ·   ·   ·   ·   ·   ∘   ∘   ·   ·   ·   ·
         |·  ·   ·   ·   ·   ·   ·   ·   ·   ·   ·   ·   ·   ·   ·   ·   ·   ·   ·   ·   ·
         |·  ·   ·   ·   ·   ·   ·   ·   ·   ·   ·   ·   ·   ∘   ∘   ∘   ·   ·   ·   ·   ·
         |·  ·   ·   ·   ·   ·   ·   ·   ·   ·   ·   ·   ·   ·   ·   ·   ·   ·   ·   ·   ·
      42 |·  ·   ·   ·   ·   ·   ·   ·   ·   ·   ∘   ∘   ∘   ∘   ·   ·   ·   ·   ·   ·   ·
         |·  ·   ·   ·   ·   ·   ·   ·   ·   ∘   ∘   ∘   ∘   ·   ·   ·   ·   ·   ·   ·   ·
         |·  ·   ·   ·   ·   ·   ·   ·   ·   ∘   ∘   ·   ·   ·   ·   ·   ·   ·   ·   ·   ·
         |·  ·   ·   ·   ·   ·   ·   ∘   ∘   ∘   ·   ·   ·   ·   ·   ·   ·   ·   ·   ·   ·
         |·  ·   ·   ·   ·   ·   ∘   ∘   ∘   ·   ·   ·   ·   ·   ·   ·   ·   ·   ·   ·   ·
      24 |·  ·   ·   ·   ·   ∘   ∘   ∘   ·   ·   ·   ·   ·   ·   ·   ·   ·   ·   ·   ·   ·
         |·  ·   ·   ·   ∘   ∘   ·   ·   ·   ·   ·   ·   ·   ·   ·   ·   ·   ·   ·   ·   ·
         |·  ·   ·   ∘   ∘   ∘   ·   ·   ·   ·   ·   ·   ·   ·               ·   ·   ·   ·
         |·  ·   ∘   ∘   ·   ·   ·   ·   ·   ·                                            
         |∘  ○   ∘   ∘   ·   ·   ·   ·   ·                                                
       7 |○  ∘   ·   ·   ·   ·   ·                                                        
         |--------|--------|--------|--------|--------|--------|--------|--------|--------
        0.0      2.3      4.5      6.8      9.0      11.3     13.6     15.8     18.1  
```

Although there's a lot of uncertainty, you can see the dense line down the middle matches fairly closely to the observed data, so our model isn't totally off-base. We can also learn something by plotting the slope vs the intercept. (Rather than redefining everything as we did in the previous section, we'll use `zip` to sneak the parameters back in to the fully-specified model.)

```scala
scala> val regr2 = regr.zip(prior).map(_._2)
regr2: rainier.core.RandomVariable[(rainier.compute.Real, rainier.compute.Real)] = rainier.core.RandomVariable@5e01c7e5

scala> plot2D(regr2.sample())
      36 |··                                                                              
         |·                                                                               
         |·  ·                                                                            
         | ···                                                                            
         |    ·     ·                                                                     
      28 |                                                                                
         |                                                                                
         |                  ·                                                             
         |                   ·                                                            
         |                                                                                
      20 |                       ·                                                        
         |                                                                                
         |                                                                                
         |                              ·                                                 
         |                                    ·                                           
      12 |                                                      ············              
         |                                                ···     ············ ··  ·  ·   
         |                                                      · ·········∘∘∘········    
         |                                                          ·······∘∘∘○○○∘∘·······
         |                                                          · ········∘∘○○○○∘∘∘···
       4 |                                                               ·········∘∘∘∘····
         |--------|--------|--------|--------|--------|--------|--------|--------|--------
        0.15     0.54     0.92     1.31     1.69     2.08     2.47     2.85     3.24  
```

There are two things to see here. First, the tails are very wide: although there's the greatest density with a small slope and intercept, the model can't rule out that one of them is quite large. Second, as you'd hope, the two parameters are anti-correlated: a large slope necessarily implies a small intercept, and vice-versa.

Alternatively, as before, we could try to predict what will happen tomorrow. Let's recreate the whole model again this time for maximum clarity, and ask the predictor to generate a value for day 21 (having given it days 0 through 20).

```scala
scala> val regr3 = for {
     |     slope <- LogNormal(0,1).param
     |     intercept <- LogNormal(0,1).param
     |     predictor = Predictor.from{i: Int => Poisson(intercept + slope*i)}
     |     _ <- predictor.fit(data)
     | } yield predictor.predict(21)
regr3: rainier.core.RandomVariable[rainier.core.Generator[Int]] = rainier.core.RandomVariable@141ccfa8
```

Plotting this is a bit ugly because of binning artifacts, but it's interesting to see the secondary mode down around 38, which represents the small chance that there was a very high intercept but basically no slope, and all the variation in the original data was just noise.

Finally, let's close with a small and somewhat contrived example of a hierarchical model, where we have two separate regressions that share a parameter. For the sake of the example, let's assume that we have a second sales dataset, much like the first, where we know they had the same rate at the start of the observations, but may have grown at different rates - so their intercepts will be equal but their slopes will not. Here's the data:

```scala
scala> val data2 = List((0,9), (1,2), (2,3), (3,17), (4,21), (5,12), (6,21), (7,19), (8,21), (9,18), (10,25), (11,27), (12,33), (13,23), (14,28), (15,45), (16,35), (17,45), (18,47), (19,54), (20,40))
data2: List[(Int, Int)] = List((0,9), (1,2), (2,3), (3,17), (4,21), (5,12), (6,21), (7,19), (8,21), (9,18), (10,25), (11,27), (12,33), (13,23), (14,28), (15,45), (16,35), (17,45), (18,47), (19,54), (20,40))
```

It's straightforward to set up two separate slopes, two separate predictor, but the same interecept. We'll still just sample the first slope so we can compare to the previous model.

```scala
scala> val regr4 = for {
     |     slope1 <- LogNormal(0,1).param
     |     slope2 <- LogNormal(0,1).param
     |     intercept <- LogNormal(0,1).param
     |     _ <- Predictor.from{i: Int => Poisson(intercept + slope1*i)}.fit(data)
     |     _ <- Predictor.from{i: Int => Poisson(intercept + slope2*i)}.fit(data2)
     | } yield (slope1, intercept)
regr4: rainier.core.RandomVariable[(rainier.compute.Real, rainier.compute.Real)] = rainier.core.RandomVariable@67c88d45
```

Plotting slope vs intercept now, we can see that, even though these are two separate regressions, we get much tighter bounds than before by letting the new data influence the shared parameter:

```scala
scala> plot2D(regr4.sample())
      25 |                                                                                
         |·                                                                               
         |·        ·         ·                                                            
         | ·                  ··     ·       ·  ·            ·                            
         |                                  ····              ·                           
      20 |                                  · ····            ··     · ·                  
         |                                      ··             ·       ·                  
         |                                       ·     ·        ·     ·                   
         |                                                                                
         |                                                                                
      15 |                                                ·                               
         |                                                 ·                              
         |                                                                                
         |                                                     ·                          
         |                                                       ·   ·  ·                 
       9 |                                                      ·  ····· ·   ·            
         |                                                       ·············· ··        
         |                                                     ·   ····················· ·
         |                                                       ··········∘∘∘∘∘∘∘∘·······
         |                                                           · ·····∘∘∘○○∘○∘∘∘····
       4 |                                                              ·········∘·∘∘·····
         |--------|--------|--------|--------|--------|--------|--------|--------|--------
        0.84     1.15     1.47     1.78     2.10     2.41     2.73     3.04     3.36  
```
