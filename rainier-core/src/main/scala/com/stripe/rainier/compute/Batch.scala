package com.stripe.rainier.compute

class Batch[+T](val size: Int, private[compute] val value: T) {
  def flatMap[U](f: T => Batch[U]): Batch[U] = {
    val u = f(value)
    require(u.size == size)
    u
  }
}
