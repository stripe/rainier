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
normal: com.stripe.rainier.core.Distribution[Double] = com.stripe.rainier.core.Injection$$anon$1@793f05aa
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
x: com.stripe.rainier.core.RandomVariable[com.stripe.rainier.compute.Real] = com.stripe.rainier.core.RandomVariable@64674f1f
```

You can see that the type of `x` is `RandomVariable[Real]`. `RandomVariable` pairs a type of value (in this case, a real number) with some knowledge of the relative probability density of different values of that type. We can use this knowledge to produce a sample of these possible values:

```scala
scala> x.sample()
res0: List[Double] = List(-0.5451549207293471, 0.5421277783120733, -0.5386759590971426, 0.5425977102747024, -0.5432380062242386, 0.5428031270781379, -0.540758488908677, 0.5423203553611833, -0.541275250038389, 0.5419649332970861, -0.5400985831640157, 0.5424864004108134, -0.5426177965822958, 0.5427531873136975, -0.543682215089409, 0.5426446811127528, -0.5430239985865657, 0.5425667388895744, -0.5436428591205954, 0.5421322049718278, -0.5445119908153138, 0.5475045451457676, -0.5477853371358438, 0.5436557495535577, -0.5450719541877482, 0.5436895190545374, -0.5435295588241975, 0.5416791254200879, -0.5422073480199067, 0.5398119449584851, -0.537620094615634, 0.5402375168482485, -0.5424008370245201, 0.5414903293044941, -0.541839959202767, 0.5427210585246793, -0.544094567...
```

Each element of the list above represents a single sample from the distribution over `x`. It's easier to understand these if we plot them as a histogram:

```scala
scala> plot1D(x.sample())
     340 |                                                                                
         |                                     · ·  ∘                                     
         |                                     ○∘○  ○                                     
         |                                     ○○○∘○○ ∘                                   
         |                                   ∘∘○○○○○○○○∘                                  
     250 |                                ···○○○○○○○○○○○ ○∘                               
         |                                ○○○○○○○○○○○○○○○○○                               
         |                             ·  ○○○○○○○○○○○○○○○○○ ∘                             
         |                             ○∘∘○○○○○○○○○○○○○○○○○∘○                             
         |                            ∘○○○○○○○○○○○○○○○○○○○○○○∘∘                           
     170 |                            ○○○○○○○○○○○○○○○○○○○○○○○○○·                          
         |                          ·○○○○○○○○○○○○○○○○○○○○○○○○○○○∘                         
         |                       ∘·○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○ ○                       
         |                    ·  ○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○∘○ ·                     
         |                    ○∘∘○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○∘○                   
      80 |                    ○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○ ○                 
         |                  ○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○·○·                
         |               ··○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○               
         |             ∘○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○··            
         |       ··∘·∘∘○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○∘··· ∘      
       0 |·····∘○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○∘····
         |--------|--------|--------|--------|--------|--------|--------|--------|--------
       -3.03    -2.35    -1.68    -1.00    -0.33     0.34     1.02     1.69     2.37  
```

Since the only information we have about `x` so far is its `Normal` prior, this distribution unsurprisingly looks normal. Later we'll see how to update our beliefs about `x` based on observational data.

For now, though, let's explore what we can do just with priors. `RandomVariable` has more than just `sample`: you can use the familiar collection methods like `map`, `flatMap`, and `zip` to transform and combine the parameters you create. For example, we can create a log-normal distribution just by mapping over `e^x`:

```scala
scala> val e_x = x.map(_.exp)
e_x: com.stripe.rainier.core.RandomVariable[com.stripe.rainier.compute.Real] = com.stripe.rainier.core.RandomVariable@caf283f

scala> plot1D(e_x.sample())
     730 |                                                                                
         | ∘                                                                              
         | ○·                                                                             
         | ○○                                                                             
         | ○○                                                                             
     550 | ○○∘   ·∘                                                                       
         | ○○○  ·○○·                                                                      
         | ○○○○·○○○○                                                                      
         | ○○○○○○○○○∘                                                                     
         | ○○○○○○○○○○                                                                     
     360 | ○○○○○○○○○○·                                                                    
         | ○○○○○○○○○○○                                                                    
         | ○○○○○○○○○○○∘                                                                   
         | ○○○○○○○○○○○○·                                                                  
         |·○○○○○○○○○○○○○·                                                                 
     180 |○○○○○○○○○○○○○○○                                                                 
         |○○○○○○○○○○○○○○○∘∘∘ ·                                                            
         |○○○○○○○○○○○○○○○○○○∘○∘∘·                                                         
         |○○○○○○○○○○○○○○○○○○○○○○○○∘·∘·····                                                
         |○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○∘∘∘∘∘∘··∘····                                   
       0 |○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○∘∘∘∘·∘···············    ·· ·   ·
         |--------|--------|--------|--------|--------|--------|--------|--------|--------
        0.13     0.98     1.82     2.67     3.51     4.36     5.21     6.05     6.90  
```

Or we can create another `Normal` parameter and zip the two together to produce a 2D gaussian. Since there's nothing relating these two parameters to each other, you can see in the plot that they're completely independent.

```scala
scala> val y = Normal(0,1).param
y: com.stripe.rainier.core.RandomVariable[com.stripe.rainier.compute.Real] = com.stripe.rainier.core.RandomVariable@254800f7

scala> val xy = x.zip(y)
xy: com.stripe.rainier.core.RandomVariable[(com.stripe.rainier.compute.Real, com.stripe.rainier.compute.Real)] = com.stripe.rainier.core.RandomVariable@7bef595

scala> plot2D(xy.sample())
     4.0 |                                                                                
         |                                    ·   ·                                       
         |               ·     ·  ·                          ·      ·                     
         |                              ·    · ··   · ··          ·               ·      ·
         |       ·           ·   ·   ·····  ············ ······      ·                    
     2.0 |               ··    · · ··········· ···· ···················· · · ·  · ·       
         |        ·  · · ·······  ····································· ·· · ·   ·        
         |       · · ·  ···· ··············∘···∘·∘·······∘················ ·· ·  ·        
         |      · ···· ·· ···········∘··∘·∘∘·∘∘∘··∘∘∘·∘∘∘∘∘∘······················        
         |  ··· ··  ··· ·············∘·∘∘·∘∘∘∘∘∘·∘∘∘∘∘∘∘∘∘·∘∘∘·∘·∘···················  ·· 
    -0.0 |      ······················∘∘∘∘∘∘∘∘∘∘○○∘○○∘○∘∘∘∘∘∘∘∘·················· ·· ·    
         |·       ···· ··············∘∘∘∘∘∘∘○∘∘∘∘∘∘∘∘○∘∘∘○∘∘∘∘∘∘··············· ·  ···· ··
         |      · ···················∘∘∘∘∘∘∘∘∘∘∘∘∘○∘∘∘○○·····∘·∘·∘···············      ·  
         |      · ······  ················∘·∘·∘∘∘∘∘·∘∘∘··∘·∘··············· ·· · ··   ·   
         | ·    ··  ·  ·· ·····················∘·····························    ·  ·     
    -2.1 |             ··· ··  ··································· · ··· ·  · ·  ·        
         |     ·      ··   · · ·· ·· ·········· ····  ········ ··  ···    ·  ·            
         |                   ··  ·   ··  · · ··········  ····   ·             ·   ·       
         |                                ·   · ·     ·             ·                     
         |                              ·                      ·                          
    -4.1 |             ·                               ·                                  
         |--------|--------|--------|--------|--------|--------|--------|--------|--------
       -3.44    -2.68    -1.92    -1.17    -0.41     0.35     1.10     1.86     2.62  
```

Finally, we can use `flatMap` to create two parameters that *are* related to each other. In particular, we'll create a new parameter `z` that we believe to be very close to `x`: its prior is a `Normal` centered on `x` with a very small standard deviation. We'll then make a 2D plot of `(x,z)` to see the relationship.

```scala
scala> val z = x.flatMap{a => Normal(a, 0.1).param}
z: com.stripe.rainier.core.RandomVariable[com.stripe.rainier.compute.Real] = com.stripe.rainier.core.RandomVariable@217c6f5c

scala> val xz = x.zip(z)
xz: com.stripe.rainier.core.RandomVariable[(com.stripe.rainier.compute.Real, com.stripe.rainier.compute.Real)] = com.stripe.rainier.core.RandomVariable@545be675

scala> plot2D(xz.sample())
     3.8 |                                                                                
         |                                                                             ···
         |                                                                      ·······   
         |                                                                   ·······      
         |                                                              ········          
     1.8 |                                                           ········             
         |                                                      ·········                 
         |                                                 ····∘∘····                     
         |                                              ···∘∘∘···                         
         |                                          ··∘∘○∘∘···                            
    -0.2 |                                      ···∘○○∘···                                
         |                                  ···∘○∘∘···                                    
         |                             ····∘∘∘∘···                                        
         |                           ···∘∘····                                            
         |                      ··········                                                
    -2.1 |                   ·······                                                      
         |               ········                                                         
         |            ·······                                                             
         |        ······                                                                  
         |      ·                                                                         
    -4.1 |·  ·                                                                            
         |--------|--------|--------|--------|--------|--------|--------|--------|--------
       -4.17    -3.28    -2.39    -1.50    -0.61     0.28     1.17     2.06     2.95  
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
poisson: com.stripe.rainier.core.RandomVariable[_] = com.stripe.rainier.core.RandomVariable@74e8f87
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
rate: com.stripe.rainier.core.RandomVariable[com.stripe.rainier.compute.Real] = com.stripe.rainier.core.RandomVariable@4bf3121d
```

This is our first "full" model: we have a parameter with a log-normal prior, bundled into the `RandomVariable` named `e_x`; we use that parameter to initialize a `Poisson` noise distribution which we fit to our observations; and at the end, we output the same parameter (referenced by `r`) as the quantity we're interested in sampling from the posterior.

Let's plot the results!

```scala
scala> plot1D(rate.sample())
     430 |                                                                                
         |                                ∘                                               
         |                               ·○                                               
         |                              ○○○                                               
         |                             ○○○○ · ○·                                          
     320 |                             ○○○○∘○○○○                                          
         |                         · ○○○○○○○○○○○·∘                                        
         |                        ·○·○○○○○○○○○○○○○                                        
         |                        ○○○○○○○○○○○○○○○○∘ ○                                     
         |                        ○○○○○○○○○○○○○○○○○∘○                                     
     210 |                       ○○○○○○○○○○○○○○○○○○○○··                                   
         |                     ○·○○○○○○○○○○○○○○○○○○○○○○                                   
         |                   ∘·○○○○○○○○○○○○○○○○○○○○○○○○  ∘                                
         |                   ○○○○○○○○○○○○○○○○○○○○○○○○○○ ∘○                                
         |                   ○○○○○○○○○○○○○○○○○○○○○○○○○○○○○   ∘                            
     100 |                · ○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○ ∘○                            
         |               ·○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○·○○                            
         |             ∘∘○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○∘○·                         
         |∘            ○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○ ·                       
         |○      ∘·· ∘ ○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○∘○····                   
       0 |○  ·  ·○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○○∘○○∘∘·· ·····  ··
         |--------|--------|--------|--------|--------|--------|--------|--------|--------
        4.41     5.28     6.15     7.02     7.90     8.77     9.64    10.51    11.38  
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
prediction: com.stripe.rainier.core.RandomVariable[com.stripe.rainier.core.Generator[Int]] = com.stripe.rainier.core.RandomVariable@238bfb48
```

This is almost the same model as `rate` above, but instead of taking the real-valued `r` rate parameter as the output, we're producing poisson-distributed integers. The samples look like this:

```scala
scala> prediction.sample()
res9: List[Int] = List(9, 14, 7, 11, 8, 5, 13, 8, 10, 8, 9, 6, 9, 4, 9, 10, 6, 7, 7, 11, 4, 5, 7, 11, 11, 6, 12, 5, 6, 9, 10, 7, 3, 13, 7, 8, 4, 10, 11, 11, 9, 6, 5, 5, 9, 5, 10, 9, 13, 7, 4, 3, 7, 3, 12, 0, 10, 7, 11, 7, 10, 5, 6, 11, 8, 10, 7, 10, 8, 7, 8, 9, 14, 10, 5, 4, 6, 13, 15, 7, 5, 10, 7, 3, 4, 7, 9, 9, 4, 11, 7, 3, 6, 8, 8, 7, 8, 9, 8, 8, 7, 11, 7, 7, 6, 7, 4, 11, 2, 4, 9, 6, 11, 4, 7, 14, 5, 9, 6, 10, 3, 9, 8, 9, 13, 13, 3, 6, 7, 8, 5, 8, 4, 3, 17, 9, 10, 11, 6, 2, 3, 5, 6, 6, 5, 8, 9, 10, 5, 11, 6, 8, 10, 8, 7, 6, 15, 8, 5, 5, 8, 5, 14, 9, 4, 9, 12, 6, 10, 1, 7, 5, 13, 7, 7, 11, 11, 10, 14, 9, 7, 10, 6, 3, 11, 9, 4, 2, 7, 10, 9, 6, 16, 4, 9, 10, 12, 8, 5, 3, 6, 15, 7, 10, 7, 9, 7, 10, 10, 11, 11, 8, 9, 7, 5, 8, 6, 2, 4, 7, 8, 7, 10, 3, 10, 6, 10, 9...
```
Or, if we plot them, like this:

```scala
scala> plot1D(prediction.sample())
    1390 |                                                                                
         |                         ○                                                      
         |                         ○  ∘                                                   
         |                     ∘   ○  ○                                                   
         |                     ○   ○  ○                                                   
    1040 |                     ○   ○  ○   ∘                                               
         |                  ∘  ○   ○  ○   ○                                               
         |                  ○  ○   ○  ○   ○                                               
         |                  ○  ○   ○  ○   ○   ∘                                           
         |                  ○  ○   ○  ○   ○   ○                                           
     690 |              ∘   ○  ○   ○  ○   ○   ○                                           
         |              ○   ○  ○   ○  ○   ○   ○                                           
         |              ○   ○  ○   ○  ○   ○   ○  ○                                        
         |              ○   ○  ○   ○  ○   ○   ○  ○                                        
         |          ·   ○   ○  ○   ○  ○   ○   ○  ○   ∘                                    
     340 |          ○   ○   ○  ○   ○  ○   ○   ○  ○   ○                                    
         |          ○   ○   ○  ○   ○  ○   ○   ○  ○   ○                                    
         |          ○   ○   ○  ○   ○  ○   ○   ○  ○   ○   ○                                
         |       ∘  ○   ○   ○  ○   ○  ○   ○   ○  ○   ○   ○  ·                             
         |       ○  ○   ○   ○  ○   ○  ○   ○   ○  ○   ○   ○  ○   ·                         
       0 |·  ∘   ○  ○   ○   ○  ○   ○  ○   ○   ○  ○   ○   ○  ○   ○  ∘   ·   ·  ·       ·  ·
         |--------|--------|--------|--------|--------|--------|--------|--------|--------
        0.0      2.5      5.0      7.5      9.9      12.4     14.9     17.4     19.9  
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
prior: com.stripe.rainier.core.RandomVariable[(com.stripe.rainier.compute.Real, com.stripe.rainier.compute.Real)] = com.stripe.rainier.core.RandomVariable@5657d3c3
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
regr: com.stripe.rainier.core.RandomVariable[(com.stripe.rainier.compute.Real, com.stripe.rainier.compute.Real)] = com.stripe.rainier.core.RandomVariable@55dc5bef
```

As before, we're starting out by just sampling the parameters. By plotting them, we can see that the model's confidence in the intercept, in particular, is pretty low: although it's most likely to be somewhere around 8, it could be anywhere from 3 to 13 or even higher. Second, as you'd hope, the two parameters are anti-correlated:
a smaller intercept would imply a higher slope, and vice versa.

```scala
scala> plot2D(regr.sample())
    15.2 |                                                                                
         |·            ·                                                                  
         |    ·       ·      ·        ·                                                   
         |    ·   ·   ··  ··  · ·    ·  ·                                                 
         |·  ·  · ··········· ····· ·      ·                                              
    12.0 |·    ····················· ··  ·  ·· ·                                          
         |     ·· · · ························  ··                                        
         |        ·· ································ ·    ·                              
         |     · ·  · ·············∘··∘··············  ··  ·                              
         |         ···············∘·∘∘∘∘∘∘∘∘∘·∘·············· ·                           
     8.8 |              · ·········∘∘∘∘∘○∘∘∘∘∘∘∘∘·············· · ·                       
         |           · ·············∘∘∘∘○○○○○○∘○○∘∘∘∘···············                      
         |                 ···········∘·∘∘∘∘○∘∘○○○○○∘○∘∘·∘·········  ·  ·                 
         |                    · ···········∘∘∘∘∘∘○○○○∘∘∘∘∘∘········ ·· ····    ·          
         |                    ·   · ··········∘∘∘∘∘∘∘∘∘∘∘∘∘∘··········· · · ·             
     5.6 |                          · ···············∘∘················· ···· ·           
         |                           ·  ·  ·· ·····························     ·         
         |                                 ·· ·  · · ··················· ·· ·  ····       
         |                                       ·· · ··  · ·· ·····  ·······   ·         
         |                                                        ··   ··· ·             ·
     2.4 |                                                      ·   ·   ·                 
         |--------|--------|--------|--------|--------|--------|--------|--------|--------
        2.44     2.62     2.80     2.98     3.16     3.33     3.51     3.69     3.87  
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
regr2: com.stripe.rainier.core.RandomVariable[com.stripe.rainier.core.Generator[Int]] = com.stripe.rainier.core.RandomVariable@4ecf3c5c
```

Plotting this gives us a prediction which incorporates both the natural noise of a Poisson distribution and our uncertainty about its underlying parameterization.

```scala
scala> plot1D(regr2.sample())
     450 |                                                                                
         |                              ·∘                                                
         |                            ·○○○· ·∘                                            
         |                            ○○○○○○○○                                            
         |                            ○○○○○○○○∘ ○                                         
     340 |                         ·· ○○○○○○○○○∘○                                         
         |                         ○○ ○○○○○○○○○○○                                         
         |                        ∘○○ ○○○○○○○○○○○○                                        
         |                       ·○○○ ○○○○○○○○○○○○ ∘                                      
         |                       ○○○○ ○○○○○○○○○○○○ ○○∘                                    
     220 |                     ··○○○○ ○○○○○○○○○○○○ ○○○                                    
         |                    ○○○○○○○ ○○○○○○○○○○○○ ○○○                                    
         |                   ○○○○○○○○ ○○○○○○○○○○○○ ○○○○○·                                 
         |                   ○○○○○○○○ ○○○○○○○○○○○○ ○○○○○○                                 
         |                  ○○○○○○○○○ ○○○○○○○○○○○○ ○○○○○○·                                
     110 |                 ·○○○○○○○○○ ○○○○○○○○○○○○ ○○○○○○○··                              
         |               ·○○○○○○○○○○○ ○○○○○○○○○○○○ ○○○○○○○○○ ·                            
         |               ○○○○○○○○○○○○ ○○○○○○○○○○○○ ○○○○○○○○○∘○                            
         |              ∘○○○○○○○○○○○○ ○○○○○○○○○○○○ ○○○○○○○○○○○·∘                          
         |          ·∘∘ ○○○○○○○○○○○○○ ○○○○○○○○○○○○ ○○○○○○○○○○○○○ ○○○                      
       0 |······∘∘○○○○○ ○○○○○○○○○○○○○ ○○○○○○○○○○○○ ○○○○○○○○○○○○○ ○○○○∘······· · ·· ·· ·  ·
         |--------|--------|--------|--------|--------|--------|--------|--------|--------
        44.0     52.3     60.7     69.0     77.3     85.7     94.0    102.4    110.7  
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
regr3: com.stripe.rainier.core.RandomVariable[(com.stripe.rainier.compute.Real, com.stripe.rainier.compute.Real)] = com.stripe.rainier.core.RandomVariable@1ceb19c1
```

or we can use Rainier's `Lookup` to package the two slopes into a single `RandomVariable` from which we can lookup the one we want. This means that our predictor now has to take both the index of the model to which the data belongs as well as the actual observation as input.

```scala
scala> val allData = data.map{ case (x,y) => ((0, x), y) } ++ data2.map{ case (x, y) => ((1, x), y) }
allData: List[((Int, Int), Int)] = List(((0,0),8), ((0,1),12), ((0,2),16), ((0,3),20), ((0,4),21), ((0,5),31), ((0,6),23), ((0,7),33), ((0,8),31), ((0,9),33), ((0,10),36), ((0,11),42), ((0,12),39), ((0,13),56), ((0,14),55), ((0,15),63), ((0,16),52), ((0,17),66), ((0,18),52), ((0,19),80), ((0,20),71), ((1,0),9), ((1,1),2), ((1,2),3), ((1,3),17), ((1,4),21), ((1,5),12), ((1,6),21), ((1,7),19), ((1,8),21), ((1,9),18), ((1,10),25), ((1,11),27), ((1,12),33), ((1,13),23), ((1,14),28), ((1,15),45), ((1,16),35), ((1,17),45), ((1,18),47), ((1,19),54), ((1,20),40))

scala> val regLookup = for {
     |   slopes <- RandomVariable.fill(2)(LogNormal(0,1).param).map(Lookup(_))
     |   intercept <- LogNormal(0,1).param
     |   _ <- Predictor.fromIntPair{ case (index, x) => Poisson(intercept + slopes(index) * x) }.fit(allData)
     | } yield (slopes(0), intercept)
regLookup: com.stripe.rainier.core.RandomVariable[(com.stripe.rainier.compute.Real, com.stripe.rainier.compute.Real)] = com.stripe.rainier.core.RandomVariable@14160c76
```

Plotting slope vs. intercept now, we see that, even though these are two separate regressions, we get tighter bounds than before by letting the new data influence the shared parameter. It makes sense that we'd get more confidence on the intercept, since we have two different time series to learn from now; but because of the anti-correlation, that also leads to somewhat more confidence than before on the `slopes(0)` parameter as well.

```scala
scala> plot2D(regLookup.sample())
    11.7 |                                                                                
         |                    ·                                                           
         |                                                                                
         | ·                                                                              
         |       ·· ··  ·         ·   ·     ··                                            
     9.5 |           · · ···  · · · ··   ··   ·                                           
         |· ·   ·· ··· · ········  ·········  ····  ···                                   
         |      ·· · ······························   ··                                  
         | ·  ······································· ·   ·· ·                            
         |·    · ·· ·········································· ·                          
     7.4 |      ·   ················∘∘∘∘∘∘∘·∘························                     
         |    ·     · ············∘∘∘∘∘∘∘∘∘∘∘∘∘∘∘∘∘∘···∘··········· ·  ·                  
         |          ··  ···········∘∘∘∘∘∘○∘∘○∘○∘∘∘∘∘∘∘∘∘∘∘·············· ·                
         |     ·    · ·· ·············∘∘∘∘∘∘∘○∘○○○∘∘∘○∘∘∘∘·∘············ ····· ·  ·       
         |             · ··············∘·∘∘∘∘∘∘∘○○∘○○∘○∘∘∘∘∘∘···············   ··   ·  ·  
     5.3 |                   · ···············∘·∘∘·∘∘∘∘∘∘∘∘·····∘············  ·          
         |                    · ····· ·················∘···················· ·  ·     ·   
         |                        ·   ····· ······························ ···  ·    ·    
         |                     ·       ·   · ··· ··· ·········· ············  ····  ·    ·
         |                                ·    ·  ·   ··· · · ······· ··   · ·     ·      
     3.1 |                                                        ···          ·   ·      
         |--------|--------|--------|--------|--------|--------|--------|--------|--------
        2.73     2.87     3.01     3.15     3.28     3.42     3.56     3.70     3.84  
```

## Learning More

This tour has focused on the high-level API. If you want to understand more about what's going on under the hood, you might enjoy reading about [Real](real.md) or checking out some [implementation notes](impl.md). If you want to learn more about Bayesian modeling in general, [Cam Davidson Pilon's book][CDP] is an excellent resource - the examples are in Python, but porting them to Rainier is a good learning exercise. Over time, we hope to add more Rainier-specific documentation and community resources; for now, feel free to file a [GitHub issue][GISSUES] with any questions or problems.

[CDP]: http://camdavidsonpilon.github.io/Probabilistic-Programming-and-Bayesian-Methods-for-Hackers/
[GISSUES]: https://github.com/stripe/rainier/issues
[UC]: https://en.wikipedia.org/wiki/Uniform_distribution_(continuous)
