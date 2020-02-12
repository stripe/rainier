package com.stripe.rainier.notebook

import com.stripe.rainier.compute._
//import com.stripe.rainier.core._
import pprint.Tree
import ammonite.repl.FullReplAPI

object PrettyPrint {
  def register(repl: FullReplAPI): Unit = {
    val p = repl.pprinter()

    def treeify(x: Any): Tree =
      handlers(treeify).lift(x).getOrElse(p.treeify(x))

    repl.pprinter.bind(
      p.copy(
        additionalHandlers = p.additionalHandlers.orElse(handlers(treeify)) 
      ))
    ()
  }

  def bounds(b: Bounds): String =
    if (b.lower == b.upper)
      f"${b.lower}%.3g"
    else
      f"${b.lower}%.3g, ${b.upper}%.3g"

  def handlers(treeify: Any => Tree): PartialFunction[Any, Tree] = {
    case r: Real            => Tree.Literal("Real(" + bounds(r.bounds) + ")")
    case v: Vec[_] =>
      Tree.Apply("Vec", v.toList.iterator.map { el =>
        treeify(el)
      })
  }
}
