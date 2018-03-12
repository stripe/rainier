# rainier

Rainier provides an idiomatic functional Scala API for bayesian inference via Markov Chain Monte Carlo.

Rainier allows you to describe a complex prior distribution by composing primitive distributions using familiar combinators like `map`, `flatMap`, and `zip`; condition that prior on your observed data; and, after an inference step, sample from the resulting posterior distribution.

Rainier currently provides two samplers: `affine-invariant MCMC`, an ensemble method popularized by the [Emcee](https://github.com/dfm/emcee) package in Python, and `Hamiltonian Monte Carlo` (along with its `NUTS` variant), a gradient-based method used in [Stan](http://mc-stan.org/) and [PyMC3](https://github.com/pymc-devs/pymc3).

## Documentation

A good starting point is the [Tour of Rainier's Core](docs/tour_tut.md).

If you're more familiar with deep learning systems like TensorFlow or PyTorch, you might also be interested in [this summary of some of the similarities and differences](docs/dl.md) between DL and MCMC.

## Building

Rainier currently uses [Bazel](https://bazel.build/) to build. If you have Bazel installed, you can build Rainier and test that it's working by executing `bazel run src/models:fitnormal`. You should see output something like this:

```
INFO: Running command line: bazel-bin/src/models/fitnormal
    2.19 |                                                                                
         |                                 ·           ··                                 
         |               ·           ·  · ·· ·  ···  ·  ·· ·  ·    · · ·                  
         |                ·   ·   ··························· ··· ·      ·  ···           
         |                   · ·············································      ··      
    2.10 |             ·····   ···············································  ···       
         |          · ·  ···················································· ····      · 
         |·   ·  ·   ···························∘··∘·····························  ·      
         |      ··························∘·∘∘∘∘∘∘∘∘∘∘∘∘∘∘∘∘∘···················· ··      
         |     ·      ··················∘∘∘∘∘∘∘∘∘○○○○∘○○∘∘∘∘∘∘···················· · ·    
    2.00 |        ·····················∘∘∘∘∘∘○○○○○○○○○○○○○∘∘∘∘∘∘······················ ·  
         |           ··················∘∘∘∘∘∘○○○○○○○○○○○○○∘∘∘∘∘∘···················       
         |         ·····················∘∘∘∘∘∘∘○○○○○∘∘○○∘∘∘∘∘∘∘∘················ ·····    
         |          ·······················∘∘∘∘∘∘∘∘∘∘∘∘∘∘∘∘······················· ··  ·  
         |       ·· ··························································   ·  ·    ·
    1.90 |          ·  ························································     ·     
         |                ············································· ···· ·    · ·     
         |                      ·   · ··· ··················· ···· ·  ·    ·              
         |                           ··· ·  · ·· ·· ·  ··  · ·· ··                        
         |                              ·        ··                                       
    1.81 |                                       ·                                        
         |--------|--------|--------|--------|--------|--------|--------|--------|--------
       2.691    2.750    2.810    2.869    2.929    2.988    3.048    3.107    3.167  
```

## Authors 

Rainier was written primarily by [Avi Bryant](http://twitter.com/avibryant), with help and guidance from [Roban Kramer](https://twitter.com/robanhk) and [Mio Alter](https://twitter.com/mioalter).
