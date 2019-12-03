package com.stripe.rainier.core

case class Prediction[T](model: Model, generator: Generator[T]) {
    def map[U](fn: T => U): Prediction[U] = Prediction(model, generator.map(fn))
    def flatMap[U,V](fn: T => Generator[U]): Prediction[U] =
        Prediction(model, generator.flatMap(fn))
    def zip[U](other: Prediction[U]): Prediction[(T,U)] =
        Prediction(model.merge(other.model), generator.zip(other.generator))
}