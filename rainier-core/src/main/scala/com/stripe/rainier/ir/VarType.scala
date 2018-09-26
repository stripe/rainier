package com.stripe.rainier.ir

import scala.collection.mutable

private sealed trait VarType
private object Inline extends VarType
final private case class Local(id: Int) extends VarType
final private case class Global(id: Int) extends VarType

final private case class VarTypes(numReferences: Map[Sym, Int],
                                  referringMethods: Map[Sym, Set[Sym]]) {
  var globals: Map[Sym, Global] = Map.empty
  var locals: Map[Sym, Map[Sym, Local]] = Map.empty

  def apply(sym: Sym): VarType = {
    if (numReferences(sym) == 1)
      Inline
    else {
      if (referringMethods(sym).size == 1)
        local(sym, referringMethods(sym).head)
      else
        global(sym)
    }
  }

  private def local(sym: Sym, method: Sym): Local =
    locals.get(method) match {
      case None =>
        val local = Local(0)
        locals += method -> Map(sym -> local)
        local
      case Some(map) =>
        map.get(sym) match {
          case None =>
            val local = Local(map.size)
            locals += method -> (map + (sym -> local))
            local
          case Some(local) =>
            local
        }
    }

  private def global(sym: Sym): Global =
    globals.get(sym) match {
      case None =>
        val global = Global(globals.size)
        globals += sym -> global
        global
      case Some(global) =>
        global
    }
}

private object VarTypes {
  def methods(seq: Seq[MethodDef]): VarTypes = {
    val allReferences = seq.map { md =>
      md.sym -> references(md)
    }
    val numReferences = mutable.Map.empty[Sym, Int].withDefaultValue(0)
    val referringMethods =
      mutable.Map.empty[Sym, Set[Sym]].withDefaultValue(Set.empty)

    for {
      (meth, refs) <- allReferences
      (sym, count) <- refs
    } {
      numReferences(sym) += count
      referringMethods(sym) += meth
    }

    VarTypes(numReferences.toMap, referringMethods.toMap)
  }

  private def references(meth: MethodDef): Map[Sym, Int] = {
    val map = mutable.Map.empty[Sym, Int].withDefaultValue(0)
    def traverse(ir: IR): Unit =
      ir match {
        case _: MethodDef => sys.error("Should not have nested defs")
        case v: VarDef =>
          map(v.sym) += 1
          traverse(v.rhs)
        case VarRef(sym, _) =>
          map(sym) += 1
        case b: BinaryIR =>
          traverse(b.left)
          traverse(b.right)
        case i: IfIR =>
          traverse(i.whenNonZero)
          traverse(i.whenZero)
          traverse(i.test)
        case u: UnaryIR =>
          traverse(u.original)
        case _ => ()
      }
    traverse(meth.rhs)
    map.toMap
  }
}
