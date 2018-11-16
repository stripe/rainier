# A Tour of Rainier's Core

Hello! This document will provide a quick tour through the most important parts of Rainier's core. If you'd like to follow along with this tour, `sbt "project rainierCore" console` will get you to a Scala REPL.

The `rainier.core` package defines some key traits like `Distribution`, `RandomVariable`, `Likelihood`, and `Predictor`. Let's import it, along with a `repl` package that gives us some useful utilities.

```scala
import com.stripe.rainier.core._
import com.stripe.rainier.repl._
import com.stripe.rainier.compute._
```

Together, those traits let you define Bayesian models in Rainier. To throw you a little bit into the deep end, here's a simple linear regression that we'll return to later. This probably won't entirely make sense yet. That's ok. Hopefully it will serve as a bit of a roadmap that put the rest of the tour in context as we build up to it.

```scala
val data =  List((0,8), (1,12), (2,16), (3,20), (4,21), (5,31), (6,23), (7,33), (8,31), (9,33), (10,36), (11,42), (12,39), (13,56), (14,55), (15,63), (16,52), (17,66), (18,52), (19,80), (20,71))

val model = for {
  slope <- LogNormal(0,1).param
  intercept <- LogNormal(0,1).param
  regression <- Predictor.fromInt{ x => Poisson(x*slope + intercept) }.fit(data)
} yield regression.predict(21)
```

## Distribution

The starting point for almost any work in Rainier will be some object that implements `rainier.core.Distribution[T]`.

Rainier implements various familiar families of probability distributions like the [Normal](https://en.wikipedia.org/wiki/Normal_distribution) distribution, the [Uniform][UC] distribution, and the [Poisson](https://en.wikipedia.org/wiki/Poisson_distribution) distribution. You will find these three in [Continuous.scala](/rainier-core/src/main/scala/com/stripe/rainier/core/Continuous.scala) and [Discrete.scala](/rainier-core/src/main/scala/com/stripe/rainier/core/Discrete.scala) - along with a few more, and we'll keep adding them as the need arises. You construct a distribution from its parameters, usually with the `apply` method on the object representing its family. So for example, this is a normal distribution with a mean of 0 and a standard deviation of 1:

```scala
scala> val normal: Distribution[Double] = Normal(0,1)
normal: com.stripe.rainier.core.Distribution[Double] = com.stripe.rainier.core.Injection$$anon$1@5c99a14b
```

In Rainier, `Distribution` objects play three different roles. `Continuous` distributions (like `Normal`), implement `param`, and all distributions implement `fit` and `generator`. Each of these methods is central to one of the three stages of building a model in Rainier:

* defining your parameters and their priors
* fitting the parameters to some observed data
* using the fitted parameters to generate samples of some posterior distribution of interest

We'll start by exploring each of these in turn.

## `param` and `RandomVariable`

Every model in Rainier has some parameters whose values are unknown (if that's not true for your model, you don't need an inference library!). The first step in constructing a model is to set up these parameters.

You create a parameter by calling `param` on the distribution that represents the prior for that parameter. `param` is only implemented for continuous distributions(which extend `Continuous`, which extends `Distribution[Double]`). As you might be able to tell from those types: in Rainier, all parameters are continuous real scalar values. There's no support for discrete model parameters, and there's no support (or need) for explicit vectorization.

Let's use that same `Normal(0,1)` distribution as a prior for a new parameter:

```scala
scala> val x = Normal(0,1).param
x: com.stripe.rainier.core.RandomVariable[com.stripe.rainier.compute.Real] = com.stripe.rainier.core.RandomVariable@7230afcb
```

You can see that the type of `x` is `RandomVariable[Real]`. `RandomVariable` pairs a type of value (in this case, a real number) with some knowledge of the relative probability density of different values of that type. We can use this knowledge to produce a sample of these possible values:

```scala
scala> x.sample()
res0: List[Double] = List(-0.5350756732175771, 0.4095879847940215, -0.48103750803591966, 0.6195772581111134, -0.1881177537347165, 0.5153578542054698, -0.40835882409772284, 0.31110274452483294, 0.48174662765081644, -0.5408499092051309, 0.4172522334231373, -0.14313607661092242, 0.36536408757761807, -0.4915431077636774, 0.39268870034628256, -0.25104249119668204, 0.42524503968352634, -0.7774682430044022, 1.0916362983757233, -0.79307761222504, 0.17955091468343598, 0.2116520724912656, -0.2544159474840291, -0.2544159474840291, 0.6933882180446678, -0.18042154050586623, 0.12743839889299519, -0.5422614153952039, 0.3626523108500808, -0.06231261510660291, 0.05715475723870011, -0.027061013200473716, -0.4245638566089802, -0.15596907820181993, -0.3961564095037544, 0.111219079...
```

Each element of the list above represents a single sample from the distribution over `x`. It's easier to understand these if we plot them as a histogram:

```scala
scala> plot1D(x.sample())
     400 |                                                                                
         |                                     ∘ ·                                        
         |                                     ○ ○                                        
         |                                    ·○ ○ ∘○∘                                    
         |                                   ○○○∘○·○○○·                                   
     300 |                                 ∘·○○○○○○○○○○○∘                                 
         |                                 ○○○○○○○○○○○○○○○∘                               
         |                                 ○○○○○○○○○○○○○○○○                               
         |                             ·∘ ○○○○○○○○○○○○○○○○○○                              
         |                             ○○∘○○○○○○○○○○○○○○○○○○○ ○                           
     200 |                             ○○○○○○○○○○○○○○○○○○○○○○ ○                           
         |                            ○○○○○○○○○○○○○○○○○○○○○○○○○                           
         |                           ·○○○○○○○○○○○○○○○○○○○○○○○○○∘○                         
         |                        · ∘○○○○○○○○○○○○○○○○○○○○○○○○○○○○·                        
         |                        ○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○                       
     100 |                       ·○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○·○                     
         |                    ○ ∘○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○·                    
         |                  · ○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○∘                   
         |                 ·○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○  ·               
         |             ··∘∘○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○∘·             
       0 |···· ··∘··∘∘∘○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○·∘···· ····
         |--------|--------|--------|--------|--------|--------|--------|--------|--------
       -3.61    -2.81    -2.01    -1.21    -0.41     0.40     1.20     2.00     2.80  
```

Since the only information we have about `x` so far is its `Normal` prior, this distribution unsurprisingly looks normal. Later we'll see how to update our beliefs about `x` based on observational data.

For now, though, let's explore what we can do just with priors. `RandomVariable` has more than just `sample`: you can use the familiar collection methods like `map`, `flatMap`, and `zip` to transform and combine the parameters you create. For example, we can create a log-normal distribution just by mapping over `e^x`:

```scala
scala> val e_x = x.map(_.exp)
e_x: com.stripe.rainier.core.RandomVariable[com.stripe.rainier.compute.Real] = com.stripe.rainier.core.RandomVariable@7ce65611

scala> plot1D(e_x.sample())
    2400 |                                                                                
         | ·                                                                              
         | ○                                                                              
         | ○                                                                              
         | ○                                                                              
    1800 |∘○                                                                              
         |○○                                                                              
         |○○·                                                                             
         |○○○                                                                             
         |○○○                                                                             
    1200 |○○○                                                                             
         |○○○                                                                             
         |○○○○                                                                            
         |○○○○                                                                            
         |○○○○∘                                                                           
     600 |○○○○○                                                                           
         |○○○○○·                                                                          
         |○○○○○○·                                                                         
         |○○○○○○○∘                                                                        
         |○○○○○○○○○∘·                                                                     
       0 |○○○○○○○○○○○○∘∘∘··························  ···     · ··    ·       ·    ·      ·
         |--------|--------|--------|--------|--------|--------|--------|--------|--------
        0.0      3.5      7.1      10.6     14.1     17.6     21.1     24.6     28.1  
```

Or we can create another `Normal` parameter and zip the two together to produce a 2D gaussian. Since there's nothing relating these two parameters to each other, you can see in the plot that they're completely independent.

```scala
scala> val y = Normal(0,1).param
y: com.stripe.rainier.core.RandomVariable[com.stripe.rainier.compute.Real] = com.stripe.rainier.core.RandomVariable@c23a6c4

scala> val xy = x.zip(y)
xy: com.stripe.rainier.core.RandomVariable[(com.stripe.rainier.compute.Real, com.stripe.rainier.compute.Real)] = com.stripe.rainier.core.RandomVariable@1477366

scala> plot2D(xy.sample())
     3.8 |                                                                                
         |                              ·                 ·                               
         |                         ·   ·             ··  ··      ·                        
         |                                ···· · ·  ······      ·  ·                      
         |                  · · ·  ·· ··  · ········ ·  ···· ·· ·         ·               
     2.0 |               ·   ·   ·· ················· ···············   ·     ·   ·       
         |             · ··· · ·· ······························· · · ·····   ·    ·   ·  
         |     ·  · · · ··········· ··································· ·      ·  ·       
         |   ·    ·····  · · ··············∘··∘·∘∘···∘····∘····················· ··      ·
         |   ·    ···  ··············∘·∘··∘·∘·∘∘∘∘∘∘∘∘∘·∘∘·····∘··············· ·· · ·    
     0.2 |·      · ··············∘·····∘∘·∘∘∘∘∘∘∘∘∘∘○○∘○∘∘·∘∘∘··············· ···      ·  
         |      ··  ···················∘∘○∘∘∘○∘∘∘∘○∘∘·∘○∘∘∘∘∘················ ······   ·  
         |···   ···  ·················∘·∘∘∘∘○∘∘∘∘∘∘∘∘○∘∘∘∘∘∘∘∘·∘·∘·········· ·········    
         |   ·   · ·  ·····················∘∘·∘∘∘∘∘∘∘∘∘∘∘∘···················· ···  ·    ·
         |     ···· ·· ·· ·················∘·∘···∘∘∘∘·∘····∘···················  ·  ··    
    -1.6 |      ·   · · · ······················∘·····∘∘················ ····· ·   ··    ·
         |            ·· ··········· ··································· ·· ·             
         |           ·        · ·· · ·· ···················  ····· · ···      ·     ·     
         |                      ·· · ····· ·  ······ ·· ·  ··· ··· ··           ·         
         |   ·          ·             ···· ··   ···   ··         · ·                      
    -3.4 |                       ·           ·    · ·  ··       ·                         
         |--------|--------|--------|--------|--------|--------|--------|--------|--------
       -3.57    -2.76    -1.96    -1.16    -0.36     0.44     1.24     2.04     2.84  
```

Finally, we can use `flatMap` to create two parameters that *are* related to each other. In particular, we'll create a new parameter `z` that we believe to be very close to `x`: its prior is a `Normal` centered on `x` with a very small standard deviation. We'll then make a 2D plot of `(x,z)` to see the relationship.

```scala
scala> val z = x.flatMap{a => Normal(a, 0.1).param}
z: com.stripe.rainier.core.RandomVariable[com.stripe.rainier.compute.Real] = com.stripe.rainier.core.RandomVariable@46cf30d1

scala> val xz = x.zip(z)
xz: com.stripe.rainier.core.RandomVariable[(com.stripe.rainier.compute.Real, com.stripe.rainier.compute.Real)] = com.stripe.rainier.core.RandomVariable@7db1d351

scala> plot2D(xz.sample())
     3.9 |                                                                                
         |                                                                           ·  ··
         |                                                                      ······    
         |                                                                  ······        
         |                                                             ········           
     1.8 |                                                          ········              
         |                                                     ··········                 
         |                                                 ···∘∘∘····                     
         |                                             ···∘∘∘∘·· ·                        
         |                                         ···∘○∘∘··                              
    -0.3 |                                     ···∘○○∘···                                 
         |                                  ··∘○○∘···                                     
         |                              ···∘∘∘···                                         
         |                          ····∘∘···                                             
         |                       ········                                                 
    -2.3 |                  ·········                                                     
         |               ········                                                         
         |            ·······                                                             
         |         ····                                                                   
         |                                                                                
    -4.4 |·                                                                               
         |--------|--------|--------|--------|--------|--------|--------|--------|--------
        -4.5     -3.6     -2.6     -1.7     -0.7     0.3      1.2      2.2      3.1   
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
val poisson9: RandomVariable[_] = Poisson(9).fit(sales)
val poisson10: RandomVariable[_] = Poisson(10).fit(sales)
```

A `RandomVariable`'s density is a rather opaque object and, since it is almost never necessary to reach into a `RandomVariable` and get its current probability `density`, Rainier makes this hard to do. Taking on faith momentarily that

```scala
val poisson9density = -18.03523006575617
val poisson10density = -19.135041188917896
```

we can find the likelihood ratio of these two hypotheses. Since the density is stored in log space, the best way to do this numerically is to subtract the logs first before exponentiating:

```scala
scala> val lr = math.exp(poisson9density - poisson10density)
lr: Double = 3.0035986601488043
```

We can see here that our data is about 3x as likely to have come from a `Poisson(9)` as it is to have come from a `Poisson(10)`. We could keep doing this with a large number of values to build up a sense of the rate distribution, but it would be tedious, and we'd be ignoring any prior we had on the rate. Instead, the right way to do this in Rainier is to make the rate a parameter, and let the library do the hard work of exploring the space. Specifically, we can use `flatMap` to chain the creation of the parameter with the creation and fit of the `Poisson` distribution. Since we want our rate to be a positive number, and expect it to be more likely to be on the small side than the large side, the log-normal parameter we created earlier as `e_x` seems like a reasonable choice.

```scala
scala> val poisson: RandomVariable[_] = e_x.flatMap{r => Poisson(r).fit(sales)}
poisson: com.stripe.rainier.core.RandomVariable[_] = com.stripe.rainier.core.RandomVariable@12c14902
```

Reaching under the hood for a second: our `poisson9`'s `density` had no parameters whereas now, our model's density is a function of the parameter value

```scala
scala> poisson9.density().nVars
res5: Int = 0

scala> poisson.density().nVars
res6: Int = 1
```

Rather than manipulating the `density` directly, we can sample the quantity we're actually interested in. To start with, let's try to sample the rate parameter of the Poisson, conditioned on our observed data. Here's almost the same thing we had above, recreated with the slightly friendlier `for` syntax, and yielding the `r` parameter at the end:

```scala
scala> val rate = for {
     |     r <- e_x
     |     poisson <- Poisson(r).fit(sales)
     | } yield r
rate: com.stripe.rainier.core.RandomVariable[com.stripe.rainier.compute.Real] = com.stripe.rainier.core.RandomVariable@50f2331
```

This is our first "full" model: we have a parameter with a log-normal prior, bundled into the `RandomVariable` named `e_x`; we use that parameter to initialize a `Poisson` noise distribution which we fit to our observations; and at the end, we output the same parameter (referenced by `r`) as the quantity we're interested in sampling from the posterior.

Let's plot the results!

```scala
scala> plot1D(rate.sample())
     370 |                                                                                
         |                                ∘                                               
         |                             ○  ○○·                                             
         |                             ○ ∘○○○○∘                                           
         |                            ○○○○○○○○○                                           
     280 |                            ○○○○○○○○○  ∘                                        
         |                         ○∘∘○○○○○○○○○○○○                                        
         |                       ○·○○○○○○○○○○○○○○○∘·∘                                     
         |                     ∘ ○○○○○○○○○○○○○○○○○○○○                                     
         |                     ○○○○○○○○○○○○○○○○○○○○○○∘                                    
     180 |                     ○○○○○○○○○○○○○○○○○○○○○○○ ∘·                                 
         |                   ○○○○○○○○○○○○○○○○○○○○○○○○○○○○                                 
         |                 ··○○○○○○○○○○○○○○○○○○○○○○○○○○○○                                 
         |                 ○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○                                
         |               · ○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○·∘                              
      90 |              ·○∘○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○∘○∘                           
         |           ··∘○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○ ·                         
         |          ·○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○ ·                      
         |          ○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○∘                     
         |     ·· ∘∘○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○∘ ∘··              
       0 |··∘·∘○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○∘○∘∘··········
         |--------|--------|--------|--------|--------|--------|--------|--------|--------
        4.84     5.61     6.37     7.14     7.90     8.67     9.44    10.20    10.97  
```

Looks like our daily rate is probably somewhere between 6 and 9, which corresponds well to the steep 3x dropoff we saw before between 9 and 10.
## A mathematical digression

Please feel free to skip this section if it's not helping you.

One thing that sometimes confuses people at this stage is, what is the line with `fit` actually doing? It seems to return a `poisson` object that we just ignore. (That object, by the way, is just the `Poisson(r)` distribution object that we used to do the `fit`.) And yet the line is clearly doing something, because sampling from `rate` gives quite different results from just sampling directly from `e_x`.

The key is to remember that a `RandomVariable` has two parts: a `value` and a `density`. Mathematically, you should think of these as two different *functions* of the same latent parameter space `Q`. That is, you should think of `value` as being some deterministic function `f = F(q)`, and `density` as being the (unnormalized) probability function `P(Q=q)`.

When we `map` or `flatMap` a `RandomVariable` (whether explicitly or inside a `for` construct), the object we see and work with (like, the `r` above) is the `value` object. And we can see in the definition of `rate` that it's just passing that value object through to the end; it should end up with the same `value` function as the prior, `e_x`. And indeed, it does:

```scala
scala> e_x.value == rate.value
res8: Boolean = true
```

So, for the same input in the latent parameter space, `e_x` and `rate` will produce the same output.

However, when we `flatMap`, behind the scenes we're also working with the `density` object. Specifically, when we start with one `RandomVariable` (like `e_x`), and use `flatMap` to flatten another `RandomVariable` into it (like the result of `fit`), the resulting `RandomVariable` (like `rate`) will have a `density` that *combines* the densities of the other two `RandomVariable`s. Put another way, `flatMap` is the Bayesian update operation: you're putting in a prior, coming up with a likelihood, and getting back the posterior. That's what the `fit` line is doing: constructing a likelihood function that we can incorporate into our posterior density.

So although `F(q)` has not changed, `P(Q=q)` *has* changed; which means that `P(F(q)=f)` has also changed, and it's that change which we're observing when we see that sampling values of `F` produces different results in the two cases.

## `generator` and `Generator`

Sampling from the rate parameter is nice, but what we originally said is that we wanted to predict how many sales to expect tomorrow. As we just discussed in the digression, we got a `poisson` value back from `fit` but didn't do anything with it. This value is just the distribution we used for the `fit`, that is, `Poisson(r)`. Now, instead of fitting data *to* that distribution, we want to generate data *from* that distribution. One idea might be to do that with `param`, that is, with something like this:

```scala
//This code won't compile
for {
    r <- e_x
    poisson <- Poisson(r).fit(sales)
    prediction <- poisson.param
} yield prediction
```

This has a couple of problems. First, it seems conceptually wrong to think of a prediction as a parameter: there is no true value of "how many sales will we have tomorrow" for us to infer, and we have no observational data that can influence our belief about its value; once we've derived the rate parameter, the prediction is purely generative.
Second, as a practical matter, Rainier only supports continuous parameters,and here we need to generate discrete values, so `poisson.param` won't, in fact, compile.

Luckily, all distributions, continious or discrete, implement `generator`, which gives us what we need: a way to randomly generate new values from a distribution as part of the sampling process. Every `Distribution[T]` can give us a `Generator[T]`, and if we sample from a `RandomVariable[Generator[T]]`, we will get values of type `T`.
(You can think of `Real` as being a special case that acts in this sense like a `Generator[Double]`).

Here's one way we could implement what we're looking for:

```scala
scala> val prediction = for {
     |     r <- e_x
     |     poisson <- Poisson(r).fit(sales)
     | } yield poisson.generator
prediction: com.stripe.rainier.core.RandomVariable[com.stripe.rainier.core.Generator[Int]] = com.stripe.rainier.core.RandomVariable@439eee6c
```

This is almost the same model as `rate` above, but instead of taking the real-valued `r` rate parameter as the output, we're producing poisson-distributed integers. The samples look like this:

```scala
scala> prediction.sample()
res9: List[Int] = List(7, 13, 7, 6, 13, 7, 7, 5, 10, 5, 8, 9, 7, 9, 6, 11, 12, 3, 11, 9, 9, 4, 7, 9, 6, 15, 8, 5, 10, 13, 12, 7, 7, 9, 6, 12, 8, 9, 12, 5, 12, 6, 12, 13, 7, 10, 6, 4, 3, 9, 5, 6, 9, 4, 9, 7, 11, 7, 9, 7, 8, 14, 4, 6, 10, 6, 4, 7, 13, 7, 7, 11, 10, 13, 7, 10, 8, 5, 10, 6, 8, 13, 10, 6, 9, 4, 6, 11, 11, 6, 10, 4, 11, 6, 7, 3, 8, 6, 5, 6, 9, 13, 12, 6, 10, 3, 8, 9, 7, 7, 11, 10, 14, 8, 4, 5, 11, 12, 12, 8, 7, 11, 6, 8, 11, 6, 8, 8, 13, 3, 10, 8, 12, 12, 7, 5, 8, 8, 8, 14, 9, 16, 7, 14, 13, 5, 9, 4, 8, 5, 2, 4, 7, 6, 8, 6, 8, 10, 7, 4, 8, 8, 7, 6, 5, 7, 8, 11, 10, 7, 11, 10, 11, 7, 7, 11, 6, 5, 12, 8, 11, 4, 10, 8, 10, 3, 4, 6, 4, 9, 7, 5, 9, 5, 5, 8, 7, 8, 5, 8, 8, 9, 8, 8, 11, 5, 10, 11, 10, 5, 8, 5, 3, 13, 9, 13, 8, 14, 3, 5, 2, 9, 5, 2, 5, 6, 7,...
```
Or, if we plot them, like this:

```scala
scala> plot1D(prediction.sample())
    1430 |                                                                                
         |                          ○                                                     
         |                          ○                                                     
         |                      ∘   ○   ∘                                                 
         |                      ○   ○   ○                                                 
    1070 |                      ○   ○   ○   ∘                                             
         |                  ○   ○   ○   ○   ○                                             
         |                  ○   ○   ○   ○   ○                                             
         |                  ○   ○   ○   ○   ○                                             
         |                  ○   ○   ○   ○   ○  ○                                          
     710 |               ·  ○   ○   ○   ○   ○  ○                                          
         |               ○  ○   ○   ○   ○   ○  ○                                          
         |               ○  ○   ○   ○   ○   ○  ○   ∘                                      
         |               ○  ○   ○   ○   ○   ○  ○   ○                                      
         |               ○  ○   ○   ○   ○   ○  ○   ○                                      
     350 |           ∘   ○  ○   ○   ○   ○   ○  ○   ○   ∘                                  
         |           ○   ○  ○   ○   ○   ○   ○  ○   ○   ○                                  
         |           ○   ○  ○   ○   ○   ○   ○  ○   ○   ○   ○                              
         |       ·   ○   ○  ○   ○   ○   ○   ○  ○   ○   ○   ○   ·                          
         |       ○   ○   ○  ○   ○   ○   ○   ○  ○   ○   ○   ○   ○  ·                       
       0 |·  ∘   ○   ○   ○  ○   ○   ○   ○   ○  ○   ○   ○   ○   ○  ○   ∘   ∘   ·   ·  ·   ·
         |--------|--------|--------|--------|--------|--------|--------|--------|--------
        0.0      2.4      4.7      7.1      9.5      11.9     14.2     16.6     19.0  
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

How should we model this? We'll need to know what the rate was on day 0 (the intercept), as well as how fast the rate increases each day (the slope). We know they're both positive and not too large, so let's keep using log-normal priors. (This time we can just use the builtin `LogNormal` instead of deriving it ourselves).

```scala
scala> val prior = for {
     |     slope <- LogNormal(0,1).param
     |     intercept <- LogNormal(0,1).param
     |     } yield (slope, intercept)
prior: com.stripe.rainier.core.RandomVariable[(com.stripe.rainier.compute.Real, com.stripe.rainier.compute.Real)] = com.stripe.rainier.core.RandomVariable@50992ee0
```

Now, for any given day `i`, we want to check the number of sales against `Poisson(intercept + slope*i)`. We could write some kind of recursive flatMap to build and fit each of these different distribution objects in turn, but this is a common enough pattern that Rainier already has something built in for it: `Predictor`. `Predictor` is not a `Distribution`, but instead wraps a function from `X => Distribution[Y]` for some `(X,Y)`; you can use this any time you have a dependent variable of type `Y` that you're modeling with some independent variables jointly represented as `X`.

Like `Distribution`, `Predictor` implements `fit` (in fact, they both extend the `Likelihood` trait which provides that method). So we can extend the previous model to create and use a `Predictor` like this:

```scala
scala> val regr = for {
     |     (slope, intercept) <- prior
     |     predictor <- Predictor.fromInt{i =>
     |                     Poisson(intercept + slope*i)
     |                 }.fit(data)
     | } yield (slope, intercept)
regr: com.stripe.rainier.core.RandomVariable[(com.stripe.rainier.compute.Real, com.stripe.rainier.compute.Real)] = com.stripe.rainier.core.RandomVariable@56dd6b7e
```

As before, we're starting out by just sampling the parameters. By plotting them, we can see that the model's confidence in the intercept, in particular, is pretty low: although it's most likely to be somewhere around 8, it could be anywhere from 3 to 13 or even higher. Second, as you'd hope, the two parameters are anti-correlated:
a smaller intercept would imply a higher slope, and vice versa.

```scala
scala> plot2D(regr.sample())
    15.1 |                                                                                
         |              ·                                                                 
         |   ·       ·    ·    · · ·                                                      
         |    · ······ · · · ····     · ··                                                
         |·  ·  ·  · ················ ··                                                  
    12.1 |· ·     ·· ··················· ··· · · ·                                        
         |     ·  ························· ······  ·                                     
         |      ····································· ··                                  
         |  ·      ···············∘························  ··                           
         |         ·   ··············∘∘∘·∘∘∘∘··∘············ ··   ·                       
     9.2 |    ·        · ·········∘∘··∘∘∘∘∘∘∘∘∘∘∘∘············ · ·             ·          
         |        ·    ··· ··········∘∘∘∘∘∘∘∘○○∘∘∘∘○∘···∘·············· · ·               
         |              · ···········∘∘∘∘∘∘∘∘○∘∘∘○∘∘∘∘∘∘∘∘··∘∘·············   ·           
         |                 ·   ···········∘∘∘∘∘∘∘○∘○○○○○∘∘∘∘∘∘·∘········· ·· · ·  ·       
         |                        ·· ·········∘∘∘∘∘∘∘○∘∘∘∘∘∘∘·∘∘············· ·           
     6.3 |                      ··  ··· ·········∘∘·∘∘∘∘∘∘∘·∘∘∘···∘········  ·· ·   ·     
         |                            ··· · ·············∘··∘∘∘··∘············  ··   ·    
         |                                ·   ···················∘············· ·· ·  ·   
         |                                       ·  ··· ························· ·      ·
         |                                       ·  ···  ········· ·········· · ··        
     3.3 |                                                 · ·     · ·   ··            ·  
         |--------|--------|--------|--------|--------|--------|--------|--------|--------
        2.45     2.61     2.78     2.94     3.10     3.26     3.43     3.59     3.75  
```

Alternatively, as in the earlier example, we could try to predict what will happen tomorrow. This is similar to calling `poisson.generator` before, but this time, we use the `predict` method on `Predictor`, which takes the covariate (the day, in this case), and returns a `Generator` for the dependent variable (the number of sales). Putting it all together, it looks like this:

```scala
scala> val regr2 = for {
     |     slope <- LogNormal(0,1).param
     |     intercept <- LogNormal(0,1).param
     |     predictor <- Predictor.fromInt{i =>
     |                     Poisson(intercept + slope*i)
     |                 }.fit(data)
     | } yield predictor.predict(21)
regr2: com.stripe.rainier.core.RandomVariable[com.stripe.rainier.core.Generator[Int]] = com.stripe.rainier.core.RandomVariable@e5f3ce1
```

Plotting this gives us a prediction which incorporates both the natural noise of a Poisson distribution and our uncertainty about its underlying parameterization.

```scala
scala> plot1D(regr2.sample())
     470 |                                                                                
         |                                 · ∘ ∘                                          
         |                                 ○ ○ ○·                                         
         |                                 ○ ○○○○· ∘                                      
         |                              ∘ ○○ ○○○○○·○                                      
     350 |                              ○·○○ ○○○○○○○                                      
         |                              ○○○○ ○○○○○○○ ∘                                    
         |                            · ○○○○ ○○○○○○○∘○∘                                   
         |                           ·○○○○○○ ○○○○○○○○○○                                   
         |                           ○○○○○○○ ○○○○○○○○○○  ·                                
     230 |                         ·○○○○○○○○ ○○○○○○○○○○○ ○                                
         |                         ○○○○○○○○○ ○○○○○○○○○○○ ○·                               
         |                        ∘○○○○○○○○○ ○○○○○○○○○○○ ○○                               
         |                      ○ ○○○○○○○○○○ ○○○○○○○○○○○ ○○○ ·                            
         |                     ·○ ○○○○○○○○○○ ○○○○○○○○○○○ ○○○ ○                            
     110 |                     ○○ ○○○○○○○○○○ ○○○○○○○○○○○ ○○○○○                            
         |                  ∘∘·○○ ○○○○○○○○○○ ○○○○○○○○○○○ ○○○○○∘∘                          
         |                  ○○○○○ ○○○○○○○○○○ ○○○○○○○○○○○ ○○○○○○○∘○                        
         |                ·∘○○○○○ ○○○○○○○○○○ ○○○○○○○○○○○ ○○○○○○○○○○ ∘                     
         |             ··○○○○○○○○ ○○○○○○○○○○ ○○○○○○○○○○○ ○○○○○○○○○○ ○∘○· ·                
       0 |··· ······∘ ○○○○○○○○○○○ ○○○○○○○○○○ ○○○○○○○○○○○ ○○○○○○○○○○ ○○○○○○·∘··· · ·   ····
         |--------|--------|--------|--------|--------|--------|--------|--------|--------
        41.0     49.2     57.4     65.7     73.9     82.1     90.3     98.6    106.8  
```

Finally, let's close with a small and somewhat contrived example of a hierarchical model, where we have two separate regressions that share a parameter. For the sake of the example, let's assume that we have a second sales dataset, much like the first, where we know they had the same rate at the start of the observations, but may have grown at different rates - so their intercepts will be equal but their slopes will not. Here's the data:

```scala
scala> val data2 = List((0,9), (1,2), (2,3), (3,17), (4,21), (5,12), (6,21), (7,19), (8,21), (9,18), (10,25), (11,27), (12,33), (13,23), (14,28), (15,45), (16,35), (17,45), (18,47), (19,54), (20,40))
data2: List[(Int, Int)] = List((0,9), (1,2), (2,3), (3,17), (4,21), (5,12), (6,21), (7,19), (8,21), (9,18), (10,25), (11,27), (12,33), (13,23), (14,28), (15,45), (16,35), (17,45), (18,47), (19,54), (20,40))
```

It's straightforward to set up two separate slopes, two separate predictors, but the same intercept. We'll still just sample the first slope so we can compare to the previous model. We can write out the two slopes and predictors manually

```scala
scala> val regr3 =  for {
     |     slope0 <- LogNormal(0,1).param
     |     slope1 <- LogNormal(0,1).param
     |     intercept <- LogNormal(0,1).param
     |     _ <- Predictor.fromInt{ i => Poisson(intercept + slope0 * i) }.fit(data)
     |     _ <- Predictor.fromInt{ i => Poisson(intercept + slope1 * i) }.fit(data2)
     | } yield (slope0, intercept)
regr3: com.stripe.rainier.core.RandomVariable[(com.stripe.rainier.compute.Real, com.stripe.rainier.compute.Real)] = com.stripe.rainier.core.RandomVariable@289ee645
```

or we can use Rainier's `Lookup` to package the two slopes into a single `RandomVariable` from which we can lookup the one we want. This means that our predictor now has to take both the index of the model to which the data belongs as well as the actual observation as input.

```scala
scala> val allData = data.map{ case (x,y) => ((0, x), y) } ++ data2.map{ case (x, y) => ((1, x), y) }
allData: List[((Int, Int), Int)] = List(((0,0),8), ((0,1),12), ((0,2),16), ((0,3),20), ((0,4),21), ((0,5),31), ((0,6),23), ((0,7),33), ((0,8),31), ((0,9),33), ((0,10),36), ((0,11),42), ((0,12),39), ((0,13),56), ((0,14),55), ((0,15),63), ((0,16),52), ((0,17),66), ((0,18),52), ((0,19),80), ((0,20),71), ((1,0),9), ((1,1),2), ((1,2),3), ((1,3),17), ((1,4),21), ((1,5),12), ((1,6),21), ((1,7),19), ((1,8),21), ((1,9),18), ((1,10),25), ((1,11),27), ((1,12),33), ((1,13),23), ((1,14),28), ((1,15),45), ((1,16),35), ((1,17),45), ((1,18),47), ((1,19),54), ((1,20),40))

scala> val regr4 = for {
     |   slopes <- RandomVariable.fill(2)(LogNormal(0,1).param).map(Lookup(_))
     |   intercept <- LogNormal(0,1).param
     |   _ <- Predictor.fromIntPair{ case (index, x) => Poisson(intercept + slopes(index) * x) }.fit(allData)
     | } yield (slopes(0), intercept)
regr4: com.stripe.rainier.core.RandomVariable[(com.stripe.rainier.compute.Real, com.stripe.rainier.compute.Real)] = com.stripe.rainier.core.RandomVariable@32e1f26b
```

Plotting slope vs. intercept now, we see that, even though these are two separate regressions, we get tighter bounds than before by letting the new data influence the shared parameter. It makes sense that we'd get more confidence on the intercept, since we have two different time series to learn from now; but because of the anti-correlation, that also leads to somewhat more confidence than before on the `slopes(0)` parameter as well.

```scala
scala> plot2D(regr4.sample())
    11.9 |                                                                                
         |                       ·                                                        
         |                                                                                
         |           ·                                                                    
         |   ·        ·  · · ·  ·                                                         
     9.7 |         ·      · ·· ·     ··                                                   
         |      ··   · ···· ·· ·  · ····  ··  ··                                          
         |· ·     ··········· ················· · ·   ·                                   
         |    ·   ···· ······························· ··  ·  ··                          
         |      ··  ······································· ··                            
     7.6 |·     ··················∘∘·∘∘··∘·∘······∘·············· ·                       
         |        ·················∘·∘∘∘∘∘∘∘∘∘∘∘∘∘∘···················  · ·               
         |     ·   ···· ············∘∘∘∘∘∘○∘∘○○∘○∘○∘∘∘∘·∘∘··············· ··    ·         
         |               ···· ······∘∘∘∘∘∘∘○∘○∘○○∘∘∘∘○○∘∘∘∘∘···············    ··· ·      
         |                 ·· ··········∘∘∘∘∘∘∘∘∘∘∘∘○∘○○∘∘∘∘∘·∘············ ·····   ·     
     5.4 |                  ···················∘∘∘∘∘∘∘∘∘∘∘∘∘∘·∘∘················ ·· ·     
         |                        ·  ···············∘····∘···∘··················· ·       
         |                   · ·· ··    ·································· ·····   ···   ·
         |                              ·  ··· ···················· ·········· ·    ·     
         |                                   · ··  ··· ·   · ····· ·······   · ·     ·    
     3.2 |                                              · ···   ····   ··                 
         |--------|--------|--------|--------|--------|--------|--------|--------|--------
        2.73     2.87     3.00     3.13     3.27     3.40     3.54     3.67     3.80  
```

## Learning More

This tour has focused on the high-level API. If you want to understand more about what's going on under the hood, you might enjoy reading about [Real](real.md) or checking out some [implementation notes](impl.md). If you want to learn more about Bayesian modeling in general, [Cam Davidson Pilon's book][CDP] is an excellent resource - the examples are in Python, but porting them to Rainier is a good learning exercise. Over time, we hope to add more Rainier-specific documentation and community resources; for now, feel free to file a [GitHub issue][GISSUES] with any questions or problems.

[CDP]: http://camdavidsonpilon.github.io/Probabilistic-Programming-and-Bayesian-Methods-for-Hackers/
[GISSUES]: https://github.com/stripe/rainier/issues
[UC]: https://en.wikipedia.org/wiki/Uniform_distribution_(continuous)
