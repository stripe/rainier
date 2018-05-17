# A Tour of Rainier's Core

Hello! This document will provide a quick tour through the most important parts of Rainier's core. If you'd like to follow along with this tour, `sbt console` will get you to a Scala REPL.

The `rainier.core` package defines some key traits like `Distribution`, `RandomVariable`, `Likelihood`, and `Predictor`. Let's import it, along with a `repl` package that gives us some useful utilities.

```scala
import com.stripe.rainier.core._
import com.stripe.rainier.repl._
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
the [Uniform](https://en.wikipedia.org/wiki/Uniform_distribution_(continuous)) distribution, and the [Poisson](https://en.wikipedia.org/wiki/Poisson_distribution) distribution. You will find these three in [Continuous.scala](/rainier-core/src/main/scala/com/stripe/rainier/core/Continuous.scala) and [Discrete.scala](/rainier-core/src/main/scala/com/stripe/rainier/core/Discrete.scala) - along with a few more, and we'll keep adding them as the need arises.

You construct a distributions from its parameters, usually with the `apply` method on the object representing its family. So for example, this is a normal distribution with a mean of 0 and a standard deviation of 1:

```scala
scala> val normal: Distribution[Double] = Normal(0,1)
normal: com.stripe.rainier.core.Distribution[Double] = com.stripe.rainier.core.Injection$$anon$1@4355c6b0
```

In Rainier, `Distribution` objects play three different roles. Most distributions (those that are continuous, like `Normal`), implement `param`, and all distributions implement `fit` and `generator`. Each of these methods is central to one of the three stages of building a model in Rainier: defining your parameters and their priors; fitting the parameters to some observed data; and using the fit parameters to generate samples of some posterior distribution of interest. We'll start by exploring each of these in turn.

## `param` and `RandomVariable`

Every model in Rainier has some parameters whose values are unknown (if that's not true for your model, you don't need an inference library!).  The first step in constructing a model is to set up these parameters. 

You create a parameter by calling `param` on the distribution that represents the prior for that parameter. `param` is only implemented for continuous distributions (which extend `Continuous`, which extends `Distribution[Double]`). As you might be able to tell from those types: in Rainier, all parameters are continuous real scalar values. There's no support for discrete model parameters, and there's no support (or need) for explicit vectorization.

Let's use that same `Normal(0,1)` distribution as a prior for a new parameter:

```scala
scala> val x = Normal(0,1).param
x: com.stripe.rainier.core.RandomVariable[com.stripe.rainier.compute.Real] = com.stripe.rainier.core.RandomVariable@2e280630
```

You can see that `x`s type is `RandomVariable[Real]`. `RandomVariable` pairs a type of value (in this case, a real number) with some knowledge of the relative probability density of different values of that type. We can use this knowledge to produce a sample of these possible values:

```scala
scala> x.sample()
res0: List[Double] = List(-0.5975756731873147, 1.8111092438685796, 0.8681426748122488, 1.3501806588802965, 0.31255432082616375, 1.5886239935453217, 0.7987880805194818, -0.6737345752431649, -0.9522373755643989, -2.031687617380814, 1.3258565635057415, -0.896887565713173, 1.016176817938606, 2.2084545575159438, -0.970103676833757, -0.5598679868479246, -1.745094094717304, 0.9182983776010616, -0.2960956124491556, -0.2193010383305457, -0.1935734836555021, -0.6112800113969439, -2.6919408977606065, -0.38416071924012996, 0.8651571104041254, -2.295235645692921, 1.1629502131413305, -0.9860195717841613, 1.5844062734921316, -0.06167873421895109, -0.6841499457323871, 0.1863292092599731, -2.5879948470199996, -0.4107678524210845, 0.22898769883958647, -1.625854521550316, 1.87275...
```

Each element of the list above represents a single sample from the distribution over `x`. It's easier to understand these if we plot them as a histogram:

```scala
scala> plot1D(x.sample())
     240 |                                                                                
         |                                     ·                                          
         |                                     ○                                          
         |                                 ·   ○ · · ·                                    
         |                              ∘  ○   ○·○ ○∘○·                                   
     180 |                             ○○ ∘○   ○○○·○○○○  ···                              
         |                       ∘ ·  ○○○ ○○∘ ○○○○○○○○○∘ ○○○ ·                            
         |                       ○ ○  ○○○ ○○○○○○○○○○○○○○·○○○·○   ∘                        
         |                      ∘○∘○∘○○○○·○○○○○○○○○○○○○○○○○○○○   ○                        
         |                    · ○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○ ∘ ○                        
     120 |                    ○ ○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○ ○○○ ∘·                     
         |             ·    ∘ ○∘○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○∘○○ ·                   
         |           ·∘○·∘·○○ ○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○ ○                   
         |           ○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○∘○···                
         |        ·  ○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○  ··           
      60 |        ○∘ ○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○∘·○○·          
         |   · ∘· ○○·○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○·         
         |∘  ○○○○ ○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○··○      
         |○ ·○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○∘∘· ○
         |○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○·○
       0 |○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○
         |--------|--------|--------|--------|--------|--------|--------|--------|--------
       -1.92    -1.48    -1.04    -0.61    -0.17     0.27     0.70     1.14     1.57  
```

Since the only information we have about `x` so far is its `Normal` prior, this distribution unsurprisingly looks  normal. Later we'll see how to update our beliefs about `x` based on observational data.

For now, though, let's explore what we can do just with priors. `RandomVariable` has more than just `sample`: you can use the familiar collection methods like `map`, `flatMap`, and `zip` to transform and combine the parameters you create. For example, we can create a log-normal distribution just by mapping over `e^x`:

```scala
scala> val ex = x.map(_.exp)
ex: com.stripe.rainier.core.RandomVariable[com.stripe.rainier.compute.Real] = com.stripe.rainier.core.RandomVariable@260271bb

scala> plot1D(ex.sample())
     700 |                                                                                
         |  ∘                                                                             
         |  ○                                                                             
         |  ○                                                                             
         | ○○·∘                                                                           
     520 | ○○○○                                                                           
         |○○○○○○                                                                          
         |○○○○○○·                                                                         
         |○○○○○○○                                                                         
         |○○○○○○○                                                                         
     350 |○○○○○○○∘∘·                                                                      
         |○○○○○○○○○○                                                                      
         |○○○○○○○○○○○                                                                     
         |○○○○○○○○○○○∘∘·                                                                  
         |○○○○○○○○○○○○○○  ∘                                                               
     170 |○○○○○○○○○○○○○○○∘○·                                                              
         |○○○○○○○○○○○○○○○○○○·                                                             
         |○○○○○○○○○○○○○○○○○○○∘·∘  ·                                                       
         |○○○○○○○○○○○○○○○○○○○○○○○∘○○○·∘·                                                  
         |○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○∘∘∘∘·○∘○ ·∘  ·                                    
       0 |○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○∘○○○∘○∘∘○∘○∘∘∘∘∘∘·∘∘··∘∘∘····∘············
         |--------|--------|--------|--------|--------|--------|--------|--------|--------
        0.13     0.94     1.75     2.57     3.38     4.19     5.01     5.82     6.63  
```

Or we can create another `Normal` parameter and zip the two together to produce a 2D gaussian. Since there's nothing relating these two parameters to each other, you can see in the plot that they're completely independent.

```scala
scala> val y = Normal(0,1).param
y: com.stripe.rainier.core.RandomVariable[com.stripe.rainier.compute.Real] = com.stripe.rainier.core.RandomVariable@171fb448

scala> val xy = x.zip(y)
xy: com.stripe.rainier.core.RandomVariable[(com.stripe.rainier.compute.Real, com.stripe.rainier.compute.Real)] = com.stripe.rainier.core.RandomVariable@76fc543a

scala> plot2D(xy.sample())
     2.0 |·   ·     · ·        ··   ·   ·· ··     ···· · ···  ··      ·   · · ·  ··       
         | ·  ·  ·· · ·····  · ··········∘ · ········ ·∘··  ··· ········   ·   ·   ·      
         |··   ·····  ········· ···· ∘··∘· ··········∘ ··∘·····  ·    ·∘ ···· ··· · ·· ·  
         |··  ·· ·· ····· ·  ·········∘········∘· ················· ······∘  · ····· ·····
         |·   ·   ·· ∘········ ····∘·∘∘···∘··∘·∘∘···∘·∘∘∘·∘···∘····∘····················· 
     1.0 |···········∘··········∘∘······∘∘·∘···∘··∘○∘···○·∘·∘·················∘······ ····
         |···· ·· ··· ···∘·∘∘····∘∘∘·∘∘∘∘··∘·○∘∘·∘·∘○∘∘∘∘···∘∘∘·○····∘················· ··
         |· ··· ···········∘∘······∘∘·∘∘∘∘○∘∘∘∘∘∘○∘∘∘∘∘··∘∘○∘∘○·∘·∘∘∘∘··∘··∘······· ····· 
         |······∘∘············∘·∘∘·∘∘○∘·○○∘∘∘·∘∘∘·∘○∘∘·∘∘○∘·∘∘∘∘·○∘·∘∘···∘·∘·······∘······
         |············ ·○·····∘··∘·∘·∘··∘○·∘∘·∘∘∘∘∘∘∘○○∘∘∘∘∘∘∘∘∘∘∘·∘∘··∘·∘·○··∘∘··∘·······
     0.0 |· · ·········∘··∘··∘∘∘○∘∘∘·∘··∘∘∘∘·∘∘○○∘○∘○∘∘○∘∘∘∘○∘○○○○∘∘∘∘···∘∘····∘·∘········
         |··· ·····∘∘· ·∘·∘·∘∘∘∘∘··∘○·∘·∘∘∘○∘∘○·∘∘∘·∘∘∘∘∘∘·∘○·∘○∘·∘··∘∘∘···∘∘········ ····
         |···········∘······∘··∘··∘∘··∘·∘∘∘∘·○·○○∘∘∘∘∘∘∘∘··∘·∘∘∘∘·∘∘·············∘·  ··   
         | ····· · ·············∘∘···∘··○○···∘∘∘··∘∘∘∘∘·∘∘··∘∘∘∘∘··∘∘∘∘·················· 
         |·     ······· ····∘∘··∘∘∘∘·∘···∘···∘○··∘∘∘·∘∘··∘∘·∘···∘·············∘·······  ··
    -1.0 |·    ····· ······∘····∘·········∘∘∘∘∘··∘·○∘···∘··∘···············∘········   ·· 
         |   ·· ·· · ···  ·············∘··∘∘··∘·····∘○○∘∘·∘···················· ·· ·· ····
         | ·  ······   ······· ·············∘∘·∘····∘··················· ········ · ··∘ ··
         | ·  ··      ·   ····· · ···  ·· ···∘ ·∘∘∘·∘·∘····· ······ ··∘··· ·········  ··· 
         |  ···  ·  ···    ··········· ····∘····  ····· ·∘ ················· ·····   ···  
    -2.0 |  ·      ··      ·   ···  ···  ·  ·········· ··········· ·· ······· · ·  ·   · ·
         |--------|--------|--------|--------|--------|--------|--------|--------|--------
       -1.91    -1.48    -1.06    -0.63    -0.21     0.22     0.64     1.07     1.50  
```

Finally, we can use `flatMap` to create two parameters that *are* related to each other. In particular, we'll create a new parameter `z` that we believe to be very close to `x`: its prior is a `Normal` centered on `x` with a very small standard deviation. We'll then make a 2D plot of `(x,z)` to see the relationship.

```scala
scala> val z = x.flatMap{a => Normal(a, 0.1).param}
z: com.stripe.rainier.core.RandomVariable[com.stripe.rainier.compute.Real] = com.stripe.rainier.core.RandomVariable@5cf0ddca

scala> val xz = x.zip(z)
xz: com.stripe.rainier.core.RandomVariable[(com.stripe.rainier.compute.Real, com.stripe.rainier.compute.Real)] = com.stripe.rainier.core.RandomVariable@6c14ba74

scala> plot2D(xz.sample())
     2.0 |                                                                             · ·
         |                                                                         ·······
         |                                                                      ··········
         |                                                                · ············  
         |                                                            · ····∘∘·······     
     1.0 |                                                          ····∘∘∘·····          
         |                                                    ····∘∘∘∘∘∘·····             
         |                                               · ····∘○∘∘······                 
         |                                           ·····∘∘○○∘∘∘··                       
         |                                        ····∘○○○○∘···                           
     0.0 |                                   ····∘○○○○○∘··· ·                             
         |                               ····∘∘○○○∘∘···                                   
         |                           ····∘∘○○○∘∘····                                      
         |                        ···∘∘∘∘○∘∘···                                           
         |                    ···∘∘∘○∘∘∘·····                                             
    -1.0 |                ···∘∘○∘∘∘∘···                                                   
         |            ····∘∘∘∘∘····                                                       
         |      ·······∘∘∘······                                                          
         |     ·····∘∘····                                                                
         |···········   ·                                                                 
    -2.0 |········                                                                        
         |--------|--------|--------|--------|--------|--------|--------|--------|--------
       -1.95    -1.51    -1.08    -0.64    -0.20     0.23     0.67     1.11     1.54  
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
poisson9: com.stripe.rainier.core.RandomVariable[_] = com.stripe.rainier.core.RandomVariable@6eda5255

scala> val poisson10: RandomVariable[_] = Poisson(10).fit(sales)
poisson10: com.stripe.rainier.core.RandomVariable[_] = com.stripe.rainier.core.RandomVariable@417928d7
```

Although it's almost never necessary, we can reach into any `RandomVariable` and get its current probability `density`:

```scala
scala> poisson9.density
res5: com.stripe.rainier.compute.Real = Constant(-18.03523006575617)
```

We can use this to find the likelihood ratio of these two hypotheses. Since the density is stored in log space, the best way to do this numerically is to subtract the logs first before exponentiating:

```scala
scala> val lr = (poisson9.density - poisson10.density).exp
lr: com.stripe.rainier.compute.Real = Constant(3.0035986601488043)
```

We can see here that our data is about 3x as likely to have come from a `Poisson(9)` as it is to have come from a `Poisson(10)`. We could keep doing this with a large number of values to build up a sense of the rate distribution, but it would be tedious, and we'd be ignoring any prior we had on the rate. Instead, the right way to do this in Rainier is to make the rate a parameter, and let the library do the hard work of exploring the space. Specifically, we can use `flatMap` to chain the creation of the parameter with the creation and fit of the `Poisson` distribution. Since we want our rate to be a positive number, and expect it to be more likely to be on the small side than the large side, the log-normal parameter we created earlier as `ex` seems like a reasonable choice.

```scala
scala> val poisson: RandomVariable[_] = ex.flatMap{r => Poisson(r).fit(sales)}
poisson: com.stripe.rainier.core.RandomVariable[_] = com.stripe.rainier.core.RandomVariable@4f25b141
```

By the way: before, when we looked at `poisson9.density`, the model had no parameters and so we got a constant value back. Now, since the model's density is a function of the parameter value, we get something more opaque back. This is why inspecting `density` is not normally useful.

```scala
scala> poisson.density
res6: com.stripe.rainier.compute.Real = com.stripe.rainier.compute.Line@7d2aee92
```

Instead, we can sample the quantity we're actually interested in. Let's recreate this model from the start, with the slightly friendlier `for` syntax:

```scala
scala> val rate = for {
     |     x <- Normal(0,1).param
     |     ex = x.exp
     |     _ <- Poisson(ex).fit(sales)
     | } yield ex
rate: com.stripe.rainier.core.RandomVariable[com.stripe.rainier.compute.Real] = com.stripe.rainier.core.RandomVariable@3e4570f0
```

Here we're creating our log-normal prior, using it as the rate on a Poisson distribution, fitting that distribution to the data, and then outputting the rate. Let's plot it!

```scala
scala> plot1D(rate.sample())
     250 |                                                                                
         |                                     · ·                                        
         |                                     ○ ○                                        
         |                                   ∘ ○ ○                                        
         |                                   ○○○·○ ○                                      
     190 |                                 ∘ ○○○○○ ○○                                     
         |                         ∘   · ∘○○ ○○○○○·○○                                     
         |                         ○∘∘○○ ○○○·○○○○○○○○ ○ ∘··                               
         |                      ∘  ○○○○○∘○○○○○○○○○○○○○○○○○○                               
         |                     ·○ ○○○○○○○○○○○○○○○○○○○○○○○○○∘○○   ○                        
     120 |                  · ·○○∘○○○○○○○○○○○○○○○○○○○○○○○○○○○○∘  ○                        
         |            ·○  ∘·○∘○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○∘ ○○                       
         |            ○○○·○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○·○○                       
         |          · ○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○··∘   ·                
         |          ○∘○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○ ·○○                
      60 |   · · · ·○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○ ∘  ○           
         |  ·○ ○○○·○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○ ○· ○           
         | ○○○·○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○···∘○··∘   
         |∘○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○· 
         |○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○
       0 |○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○
         |--------|--------|--------|--------|--------|--------|--------|--------|--------
        5.84     6.30     6.75     7.20     7.66     8.11     8.57     9.02     9.48  
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
prediction: com.stripe.rainier.core.RandomVariable[com.stripe.rainier.core.Generator[Int]{val requirements: Set[com.stripe.rainier.compute.Real]}] = com.stripe.rainier.core.RandomVariable@108d227d
```

This is almost the same model as `rate` above, but instead of taking the real-valued `ex` rate parameter as the output, we're producing poisson-distributed integers. The samples look like this:

```scala
scala> prediction.sample()
res8: List[Int] = List(5, 4, 6, 16, 5, 6, 6, 5, 9, 9, 8, 4, 6, 5, 8, 8, 3, 8, 16, 6, 5, 4, 5, 10, 12, 5, 15, 8, 3, 5, 7, 12, 5, 7, 7, 8, 6, 5, 7, 8, 9, 9, 7, 8, 9, 3, 8, 8, 12, 4, 5, 9, 7, 12, 3, 6, 4, 11, 5, 6, 10, 9, 10, 6, 2, 6, 12, 10, 4, 9, 9, 8, 7, 7, 6, 9, 7, 10, 9, 11, 6, 8, 13, 3, 14, 7, 16, 3, 7, 8, 5, 10, 9, 8, 6, 8, 9, 3, 10, 18, 8, 4, 5, 10, 5, 12, 2, 9, 7, 13, 9, 7, 4, 9, 6, 10, 13, 9, 3, 2, 11, 7, 11, 11, 5, 7, 13, 8, 7, 9, 9, 5, 9, 5, 5, 10, 4, 14, 8, 7, 5, 5, 9, 10, 5, 11, 11, 8, 8, 6, 6, 10, 9, 10, 2, 8, 8, 11, 10, 10, 8, 4, 7, 8, 4, 1, 5, 5, 8, 10, 3, 5, 9, 10, 10, 4, 12, 13, 8, 4, 3, 5, 4, 4, 7, 2, 10, 11, 2, 3, 6, 11, 4, 6, 12, 9, 9, 5, 7, 7, 9, 6, 4, 9, 2, 12, 3, 7, 10, 9, 14, 5, 10, 7, 13, 10, 6, 5, 5, 11, 4, 8, 9, 10, 6, 9, 9, 7, 6, 7, 9...
```

It's very common to want to generate new data that mimics the data you fit against. We've been carefully ignoring the the type of `RandomVariable` that `fit` returns, but in fact, it contains a `Generator` to do just that. That gives us another way to implement the same thing. (While we're at it, let's make use of the `LogNormal` distribution instead of rolling our own.)

```scala
scala> val prediction2 = for {
     |     ex <- LogNormal(0,1).param
     |     poisson <- Poisson(ex).fit(sales)    
     | } yield poisson.map(_.head)
prediction2: com.stripe.rainier.core.RandomVariable[com.stripe.rainier.core.Generator[Int]] = com.stripe.rainier.core.RandomVariable@1dbeafcd
```  

There's a bit of a twist here: because we `fit` against a list of 7 data points, the generator returned from there will try to produce output of the same shape, with each sample having a sequence of 7 ints. Luckily, `Generator` has the usual `map` and `flatMap` methods, so we can fix that by grabbing just the first value with `head`.

Let's plot it this time:

```scala
scala> plot1D(prediction2.sample())
    1440 |                                                                                
         |                            ○                                                   
         |                            ○       ·                                           
         |                     ∘      ○       ○                                           
         |                     ○      ○       ○                                           
    1080 |                     ○      ○       ○                                           
         |              ∘      ○      ○       ○      ○                                    
         |              ○      ○      ○       ○      ○                                    
         |              ○      ○      ○       ○      ○                                    
         |              ○      ○      ○       ○      ○      ∘                             
     720 |       ·      ○      ○      ○       ○      ○      ○                             
         |       ○      ○      ○      ○       ○      ○      ○      ·                      
         |       ○      ○      ○      ○       ○      ○      ○      ○                      
         |       ○      ○      ○      ○       ○      ○      ○      ○                      
         |       ○      ○      ○      ○       ○      ○      ○      ○                      
     360 |∘      ○      ○      ○      ○       ○      ○      ○      ○      ∘               
         |○      ○      ○      ○      ○       ○      ○      ○      ○      ○               
         |○      ○      ○      ○      ○       ○      ○      ○      ○      ○       ∘       
         |○      ○      ○      ○      ○       ○      ○      ○      ○      ○       ○      ·
         |○      ○      ○      ○      ○       ○      ○      ○      ○      ○       ○      ○
       0 |○      ○      ○      ○      ○       ○      ○      ○      ○      ○       ○      ○
         |--------|--------|--------|--------|--------|--------|--------|--------|--------
        0.0      2.6      5.2      7.8      10.4     13.0     15.6     18.2     20.8  
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
      81 |                                                                                
         |                                                                           ○    
         |                                                                                
         |                                                                               ○
         |                                                                                
      62 |                                                           ○       ○            
         |                                                                                
         |                                                   ○                            
         |                                                       ○       ○       ○        
         |                                                                                
      44 |                                                                                
         |                                           ○                                    
         |                                               ○                                
         |                                       ○                                        
         |                   ○       ○   ○   ○                                            
      26 |                                                                                
         |                       ○                                                        
         |           ○   ○                                                                
         |       ○                                                                        
         |   ○                                                                            
       8 |○                                                                               
         |--------|--------|--------|--------|--------|--------|--------|--------|--------
        0.0      2.3      4.5      6.8      9.0      11.3     13.6     15.8     18.1  
```

How should we model this? We'll need to know what the rate was on day 0 (the intercept), as well as how fast the rate increases each day (the slope). We know they're both positive and not too large, so let's keep using LogNormal priors.

```scala
scala> val prior = for {
     |     slope <- LogNormal(0,1).param
     |     intercept <- LogNormal(0,1).param
     |     } yield (slope, intercept)
prior: com.stripe.rainier.core.RandomVariable[(com.stripe.rainier.compute.Real, com.stripe.rainier.compute.Real)] = com.stripe.rainier.core.RandomVariable@6d203ea7
```

Now, for any given day `i`, we want to check the number of sales against `Poisson(intercept + slope*i)`. We could write some kind of recursive flatMap to build and fit each of these different distribution objects in turn, but this is a common enough pattern that Rainier already has something built in for it: `Predictor`. `Predictor` is not a `Distribution`, but instead wraps a function from `X => Distribution[Y]` for some `(X,Y)`; you can use this any time you have a dependent variable of type `Y` that you're modeling with some independent variables jointly represented as `X`. 

Like `Distribution`, `Predictor` implements `fit` (in fact, they both extend the `Likelihood` trait which provides that method). So we can create and use it like this:

```scala
scala> val regr = prior.flatMap {case (slope, intercept) =>
     |   Predictor.from{i: Int => Poisson(intercept + slope*i)}.fit(data) 
     | }
regr: com.stripe.rainier.core.RandomVariable[com.stripe.rainier.core.Generator[Seq[(Int, Int)]]] = com.stripe.rainier.core.RandomVariable@40b8f861
```

As before, the return value from `fit` will be a `Generator` that tries to recreate the input data. In this case, it will keep the `X` the same but generate a new `Y` for each data point. If we flatten all of the samples out, we can plot them and compare to the observed data to get a sense of whether our model is any good.

```scala
scala> plot2D(regr.sample().flatten)
      78 |                                                       ·   ·   ·   ·   ·   ·   ·
         |                                           ·       ·   ·   ·   ·   ·   ·   ·   ·
         |                                               ·   ·   ·   ·   ·   ·   ·   ·   ·
         |                                           ·   ·   ·   ·   ·   ·   ·   ·   ·   ·
         |                       ·                   ·   ·   ·   ·   ·   ·   ·   ∘   ·   ·
      60 |           ·                           ·   ·   ·   ·   ·   ·   ·   ·   ·   ·   ·
         |·  ·           ·   ·               ·   ·   ·   ·   ·   ·   ∘   ∘   ·   ·   ·   ·
         |·  ·       ·   ·   ·   ·   ·   ·   ·   ·   ·   ·   ·   ·   ·   ·   ·   ·   ·   ·
         |·  ·   ·   ·   ·   ·   ·   ·   ·   ·   ·   ·   ·   ∘   ∘   ∘   ·   ·   ·   ·   ·
         |·  ·   ·   ·   ·   ·   ·   ·   ·   ·   ·   ·   ·   ·   ·   ·   ·   ·   ·   ·   ·
      42 |·  ·   ·   ·   ·   ·   ·   ·   ·   ·   ·   ∘   ∘   ∘   ·   ·   ·   ·   ·   ·   ·
         |·  ·   ·   ·   ·   ·   ·   ·   ·   ∘   ∘   ∘   ·   ·   ·   ·   ·   ·   ·   ·   ·
         |·  ·   ·   ·   ·   ·   ·   ·   ·   ∘   ∘   ·   ·   ·   ·   ·   ·   ·   ·   ·   ·
         |·  ·   ·   ·   ·   ·   ·   ∘   ∘   ∘   ·   ·   ·   ·   ·   ·   ·   ·   ·   ·   ·
         |·  ·   ·   ·   ·   ·   ∘   ∘   ∘   ·   ·   ·   ·   ·   ·   ·   ·   ·   ·   ·   ·
      24 |·  ·   ·   ·   ·   ∘   ∘   ∘   ·   ·   ·   ·   ·   ·   ·   ·   ·   ·   ·   ·   ·
         |·  ·   ·   ·   ∘   ∘   ·   ·   ·   ·   ·   ·   ·   ·   ·   ·   ·   ·   ·   ·   ·
         |·  ·   ·   ∘   ∘   ∘   ·   ·   ·   ·   ·   ·   ·       ·               ·       ·
         |·  ·   ∘   ∘   ·   ·   ·   ·   ·   ·                                            
         |∘  ○   ∘   ∘   ·   ·   ·   ·   ·   ·                                            
       7 |○  ∘   ·   ·   ·   ·   ·                                                        
         |--------|--------|--------|--------|--------|--------|--------|--------|--------
        0.0      2.3      4.5      6.8      9.0      11.3     13.6     15.8     18.1  
```

Although there's a lot of uncertainty, you can see the dense line down the middle matches fairly closely to the observed data, so our model isn't totally off-base. We can also learn something by plotting the slope vs the intercept. (Rather than redefining everything as we did in the previous section, we'll use `zip` to sneak the parameters back in to the fully-specified model.)

```scala
scala> val regr2 = regr.zip(prior).map(_._2)
regr2: com.stripe.rainier.core.RandomVariable[(com.stripe.rainier.compute.Real, com.stripe.rainier.compute.Real)] = com.stripe.rainier.core.RandomVariable@61eff6c1

scala> plot2D(regr2.sample())
      39 |··                                                                              
         |·· ·                                                                            
         |··     ·                                                                        
         | ·  ·                                                                           
         |                                                                                
      31 |                                                                                
         |                                                                                
         |                                                                                
         |                                                                                
         |                                                                                
      22 |                                                                                
         |                                                                                
         |                                                                                
         |                       ·                                                        
         |                                                      ·                         
      13 |                                                        ··                      
         |                                       ·               ············· ···        
         |                                             ·           ················       
         |                                                      ··    ·····∘∘∘∘∘∘······   
         |                                                             ······∘∘○○○∘∘∘∘····
       5 |                                                               ···········∘∘····
         |--------|--------|--------|--------|--------|--------|--------|--------|--------
        0.02     0.42     0.83     1.23     1.63     2.03     2.43     2.83     3.24  
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
regr3: com.stripe.rainier.core.RandomVariable[com.stripe.rainier.core.Generator[Int]] = com.stripe.rainier.core.RandomVariable@3be86d6a
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
regr4: com.stripe.rainier.core.RandomVariable[(com.stripe.rainier.compute.Real, com.stripe.rainier.compute.Real)] = com.stripe.rainier.core.RandomVariable@2181d316
```

Plotting slope vs intercept now, we can see that, even though these are two separate regressions, we get tighter bounds than before by letting the new data influence the shared parameter:

```scala
scala> plot2D(regr4.sample())
      31 |                                                                                
         |·                                                                               
         |                                                                                
         |         ·   ·                                                                  
         |             ·                                                                  
      24 |                                                                                
         |               ··                   ·                                           
         |                                                                                
         |                                         ·                                      
         |                                                                                
      17 |                                                                                
         |                                                                                
         |                                                 ·                              
         |                                                ·                               
         |                                                     ·                          
      11 |                                                             · ··               
         |                                                            ·   ····   ·        
         |                                                             ···············    
         |                                                             ·······∘∘∘·········
         |                                                               ······∘∘○○○∘∘∘···
       4 |                                                                 ·······∘∘∘∘∘···
         |--------|--------|--------|--------|--------|--------|--------|--------|--------
        0.01     0.42     0.82     1.23     1.64     2.05     2.46     2.86     3.27  
```
