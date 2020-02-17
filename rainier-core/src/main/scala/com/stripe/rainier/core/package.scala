package com.stripe.rainier

import com.stripe.rainier.sampler.RNG

package object core {
  implicit val rng = RNG.default
}
