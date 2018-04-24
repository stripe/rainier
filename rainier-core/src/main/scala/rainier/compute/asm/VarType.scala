package rainier.compute.asm

sealed trait VarType
object Inline extends VarType
case class Local(id: Int) extends VarType
case class Global(id: Int) extends VarType

case class VarTypes(depStats: IR.DepStats) {
  var globals = Map.empty[Sym, Global]
  var locals = Map.empty[Sym, Map[Sym, Local]]

  def apply(sym: Sym): VarType = {
    val symStats = depStats.symStats(sym)
    if (symStats.numReferences == 1)
      Inline
    else {
      val referringMethods = symStats.referringMethods
      if (referringMethods.size == 1)
        local(sym, referringMethods.head)
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
