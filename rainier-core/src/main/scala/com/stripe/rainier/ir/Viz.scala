package com.stripe.rainier.ir

class Viz(methodDefs: List[MethodDef]) {
  val gv = new GraphViz

  private var counter = 0
  def nextID(): String = {
    counter += 1
    s"r$counter"
  }

  private val varTypes = VarTypes.methods(methodDefs)
  private var methods = Map.empty[Sym, String]
  methodDefs.foreach { methDef =>
    methods +=
      (methDef.sym ->
        traverseDef(methDef.sym.id, "", "black", methDef.rhs))
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

  private def label(value: String, shape: String): String = {
    val id = nextID()
    gv.statement(id, Map("label" -> value, "shape" -> shape))
    id
  }

  def double(c: Double): String =
    "%.2f".format(c)

  def outputMethod(name: String, sym: Sym): Unit = {
    val nameID = label(name, "house")
    gv.edge(nameID, methods(sym))
  }

  def traverse(r: Expr): String =
    r match {
      case Const(c)     => label(double(c), "oval")
      case _: Parameter => label("θ", "diamond")
      case VarRef(sym) =>
        val id = nextID()
        gv.statement(id,
                     Map(
                       "label" -> slot(sym),
                       "color" -> color(sym),
                       "shape" -> "square"
                     ))
        id
      case VarDef(sym, rhs) =>
        traverseVarDef(sym, rhs)
    }

  def color(sym: Sym): String =
    varTypes(sym) match {
      case Inline    => ""
      case Local(_)  => "blue"
      case Global(_) => "red"
    }

  def slot(sym: Sym): String =
    varTypes(sym) match {
      case Inline    => ""
      case Local(x)  => s"t$x"
      case Global(x) => s"g$x"
    }

  def traverseVarDef(sym: Sym, ir: IR): String =
    varTypes(sym) match {
      case Inline =>
        traverseIR(ir)
      case _ => traverseDef(sym.id, slot(sym), color(sym), ir)
    }

  def traverseDef(id: Int, slot: String, color: String, ir: IR): String =
    gv.subgraph(s"cluster_$id",
                Map("label" -> slot, "labeljust" -> "l", "color" -> color)) {
      traverseIR(ir)
    }

  def traverseIR(ir: IR): String =
    ir match {
      case BinaryIR(left, right, op) =>
        val leftID = traverse(left)
        val rightID = traverse(right)
        val id = label(opLabel(op), "oval")
        gv.edge(id, leftID)
        gv.edge(id, rightID)
        id
      case UnaryIR(original, NoOp) =>
        traverse(original)
      case UnaryIR(original, op) =>
        val origID = traverse(original)
        val id = label(opLabel(op), "oval")
        gv.edge(id, origID)
        id
      case IfIR(test, nz, z) =>
        val testID = traverse(test)
        val nzID = traverse(nz)
        val zID = traverse(z)
        val id = nextID()
        gv.record(id,
                  List(
                    ("If", None),
                    ("test", Some(testID)),
                    ("nonZero", Some(nzID)),
                    ("zero", Some(zID))
                  ))
        id
      case LookupIR(index, table) =>
        val indexID = traverse(index)
        val id = nextID()
        gv.record(id,
                  List(("Lookup", None), ("idx", Some(indexID))) ++
                    table.zipWithIndex.map {
                      case (ref, i) =>
                        (i.toString, Some(traverse(ref)))
                    })
        id
      case SeqIR(first, second) =>
        val firstID = traverse(first)
        val secondID = traverse(second)
        val id = label("", "rarrow")
        gv.edge(id, firstID)
        gv.edge(id, secondID, Map("style" -> "bold"))
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
