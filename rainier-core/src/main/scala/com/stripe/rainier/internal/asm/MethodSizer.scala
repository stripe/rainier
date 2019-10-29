package com.stripe.rainier.internal.asm

import java.lang.reflect.Field

object MethodSizer {
  val firstMethodField: Field =
    classOf[ClassWriter].getDeclaredField("firstMethod")

  firstMethodField.setAccessible(true)

  def methodSizes(cw: ClassWriter): List[Int] = {
    var list = List.empty[Int]
    var methodWriter = firstMethodField.get(cw).asInstanceOf[MethodWriter]
    while (methodWriter != null) {
      list = methodWriter.getSize :: list
      methodWriter = methodWriter.mv.asInstanceOf[MethodWriter]
    }
    list
  }
}
