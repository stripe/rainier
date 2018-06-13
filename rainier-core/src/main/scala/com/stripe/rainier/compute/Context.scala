package com.stripe.rainier.compute

case class Context(density: Real) {
  val compiler: Compiler = IRCompiler(200, 100, false)

  val (variables, gradient) =
    Gradient.derive(density).unzip
}
