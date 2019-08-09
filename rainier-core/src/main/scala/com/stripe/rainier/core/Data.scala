package com.stripe.rainier.core

case class Data[T](seq: Seq[T]) {
    def update[L](l: L)(implicit toLH: ToLikelihood[L, T]): RandomVariable[L] = 
        toLH(l).fit(seq).map{_ => l}

    def update[K,V,L](fn: K => L)(implicit ev: T <:< (K,V), toLH: ToLikelihood[L, V]): RandomVariable[K => L] = {
        val rvs = seq.map {t => 
            val (k,v) = ev(t)
            toLH(fn(k)).fit(v)
        }
        RandomVariable.traverse(rvs).map{_ => fn}
    }
}