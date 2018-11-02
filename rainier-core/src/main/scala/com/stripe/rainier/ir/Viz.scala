package com.stripe.rainier.ir

class Viz(methodDefs: List[MethodDef]) {
  import GraphViz._
  val gv = new GraphViz

  private val varTypes = VarTypes.methods(methodDefs)
  private var methods = Map.empty[Sym, String]
  methodDefs.foreach { methDef =>
    methods +=
      (methDef.sym ->
        traverseDef("", "black", methDef.rhs))
  }

  private def opLabel(op: UnaryOp): String =
    op match {
      case ExpOp       => "exp"
      case LogOp       => "log"
      case AbsOp       => "abs"
      case RectifierOp => "relu"
      case NoOp        => "x"
    }

  private def opLabel(op: BinaryOp): String =
    op match {
      case AddOp      => "+"
      case MultiplyOp => "*"
      case SubtractOp => "-"
      case DivideOp   => "/"
      case PowOp      => "^"
    }

  def outputMethod(name: String, sym: Sym): Unit =
    gv.edge(gv.node(label(name), shape("house")), methods(sym))

  def refLabel(r: Ref): String =
    r match {
      case Const(c)     => formatDouble(c)
      case _: Parameter => "θ"
      case VarRef(sym)  => varSlot(sym)
    }

  def traverse(r: Expr): String =
    r match {
      case Const(c) =>
        gv.node(label(formatDouble(c)), shape("circle"))

      case _: Parameter =>
        gv.node(label("θ"), color("green"), shape("doublecircle"))
      case VarRef(sym) =>
        gv.node(label(varSlot(sym)), color(varColor(sym)), shape("square"))
      case VarDef(sym, rhs) =>
        traverseVarDef(sym, rhs)
    }

  def varColor(sym: Sym): String =
    varTypes(sym) match {
      case Inline    => ""
      case Local(_)  => "blue"
      case Global(_) => "red"
    }

  def varSlot(sym: Sym): String =
    varTypes(sym) match {
      case Inline    => ""
      case Local(x)  => s"t$x"
      case Global(x) => s"g$x"
    }

  def traverseVarDef(sym: Sym, ir: IR): String =
    varTypes(sym) match {
      case Inline =>
        traverseIR(ir)
      case _ => traverseDef(varSlot(sym), varColor(sym), ir)
    }

  def traverseDef(slot: String, clr: String, ir: IR): String =
    gv.cluster(label(slot), justify("l"), color(clr)) {
      traverseIR(ir)
    }

  def traverseIR(ir: IR): String =
    ir match {
      case BinaryIR(left, right, op) =>
        val leftID = traverse(left)
        val rightID = traverse(right)
        val id = gv.node(label(opLabel(op)), shape("oval"))
        gv.edge(id, leftID)
        gv.edge(id, rightID)
        id
      case UnaryIR(original, NoOp) =>
        traverse(original)
      case UnaryIR(original, op) =>
        val origID = traverse(original)
        val id = gv.node(label(opLabel(op)), shape("oval"))
        gv.edge(id, origID)
        id
      case IfIR(test, nz, z) =>
        val testID = traverse(test)
        val nzID = traverse(nz)
        val zID = traverse(z)
        val id = gv.node(label("If"), shape("diamond"))
        gv.edge(id, testID, style("bold"))
        gv.edge(id, nzID, color("green"))
        gv.edge(id, zID, color("red"))
        id
      case LookupIR(index, table) =>
        val refLabels =
          table.map(refLabel)
        val (id, slotIDs) = gv.record("Lookup" :: refLabels.toList)
        val indexID = traverse(index)
        gv.edge(slotIDs.head, indexID)
        id
      case SeqIR(first, second) =>
        val firstID = traverse(first)
        val secondID = traverse(second)
        val id = gv.node(label(""), shape("rarrow"))
        gv.edge(id, firstID)
        gv.edge(id, secondID, style("bold"))
        id

      case MethodRef(sym) => methods(sym)
    }
}

object Viz {
  def apply(exprs: Seq[(String, Expr)], methodSizeLimit: Option[Int]): Viz = {
    val methodGroups = exprs.toList.map {
      case (name, expr) =>
        methodSizeLimit match {
          case Some(l) =>
            val packer = new Packer(l)
            val outputRef = packer.pack(expr)
            (name, outputRef, packer.methods.reverse)
          case None =>
            val sym = Sym.freshSym()
            val methods = List(new MethodDef(sym, UnaryIR(expr, NoOp)))
            val outputRef = MethodRef(sym)
            (name, outputRef, methods)
        }
    }
    val allMeths = methodGroups.flatMap(_._3)
    val viz = new Viz(allMeths)
    methodGroups.foreach {
      case (name, outputRef, _) =>
        viz.outputMethod(name, outputRef.sym)
    }
    viz
  }
}
