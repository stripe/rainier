package com.stripe.rainier.core

import com.stripe.rainier.compute._

trait Fn[A,Y] extends Function1[A,Y] {
    type X
    protected def encoder: Encoder[A] { type U = X }
    protected def xy(x: X): Y  

    def apply(a: A): Y = ???
    def zip[B,Z](fn: Fn[B,Z]): Fn[(A,B),(Y,Z)] = ???
    override def andThen[Z](g: Y => Z): Fn[A,Z] = ???
}