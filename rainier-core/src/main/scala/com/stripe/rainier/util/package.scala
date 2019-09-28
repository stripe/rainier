package com.stripe.rainier

package object util {
  def updateMap[K, V](m: Map[K, V], k: K, vDelta: V)(
      default: => V
  )(plus: (V, V) => V): Map[K, V] =
    m.updated(k, plus(m.getOrElse(k, default), vDelta))
}
