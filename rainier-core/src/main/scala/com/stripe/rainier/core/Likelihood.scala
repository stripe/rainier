package com.stripe.rainier.core

trait Likelihood[T] {
  def fit(t: T): RandomVariable[Generator[T]]
  def fit(seq: Seq[T]): RandomVariable[Generator[Seq[T]]] = {
    val rvs: Seq[RandomVariable[Generator[T]]] = seq.map(fit)
    RandomVariable.traverse(rvs).map { gens: Seq[Generator[T]] =>
      Generator.traverse(gens)
    }
  }
}
