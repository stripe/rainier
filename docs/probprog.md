---
id: probprog
title: A Bayesian Computation Graph for High-Performance Gradient Evaluation on the JVM
sidebar_label: Bryant (2020)
---

_This extended abstract was accepted to the poster session for [PROBPROG](https://probprog.cc/) 2020_

## Full text

Download: [rainier.pdf](http://rainier.fit/img/rainier.pdf)

## Abstract

Rainier is a Scala library for building fixed-structure, continuous-parameter generative models, to be sampled and optimized using gradient-based methods. Its core contribution is a static computation graph targeted at Bayesian model inference in a Java Virtual Machine production environment.

In particular, Rainier is designed to be deployed to large data processing clusters running Spark, Hadoop, or similar JVM-based systems, which are very commonly found in industry. These environments often discourage the use of native (as opposed to JVM) libraries, and do not have access to GPUs. As a result, Rainier must rely on a heavily optimizing compiler, directly targeting the JVM, to achieve high performance.

We will focus on two stages of the compiler: first, a partial evaluation stage that attempts to pre-compute as much as possible of the function represented by the graph, given fixed model inputs and observations (but still allowing parameter values to vary); second, the generation of low-level JVM bytecode designed to be easily compiled to efficient machine code by the JVM's just-in-time compiler.

We will also briefly discuss two novel features of the computation graph that are particularly helpful in the Bayesian context: log-density annotations on parameter nodes, and the use of interval arithmetic for tracking the support of each node of the graph.
