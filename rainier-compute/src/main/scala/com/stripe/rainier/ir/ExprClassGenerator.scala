package com.stripe.rainier.ir

import com.stripe.rainier.internal.asm.tree.MethodNode

private[ir] case class ExprClassGenerator(name: String,
                                          methods: Seq[MethodNode])
    extends ClassGenerator {

  def superClasses = null
}
