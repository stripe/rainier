package com.stripe.rainier.ir

object Tracer {
  def trace(inputs: Seq[Parameter], irs: Seq[IR]): Unit = {
    val packer = new Packer(200)
    val outputMeths = irs.map { ir =>
      packer.pack(ir)
    }
    val allMeths = packer.methods
    val varTypes = VarTypes.methods(allMeths.toList)
    traceApply(outputMeths.map(_.sym.id), varTypes.globals.size)
    allMeths.foreach { meth =>
      trace(meth, inputs, varTypes)
    }
  }

  private def traceApply(outputs: Seq[Int], globalSize: Int) = {
    println("def apply(params: Array[Double]): Array[Double] = {")
    println("  val globals = new Array[Double](" + globalSize + ")")
    println(
      "  Array(" + outputs
        .map { i =>
          "f" + i + "(params, globals)"
        }
        .mkString(",") + ")")
    println("}")
  }

  private def trace(method: MethodDef,
                    inputs: Seq[Parameter],
                    varTypes: VarTypes): Unit = {
    val varIndices = inputs.zipWithIndex.toMap

    println(
      "def f" + method.sym.id + "(params: Array[Double], globals: Array[Double]): Double = {")
    println("  " + traverse(method.rhs))
    println("}")

    def traverse(ir: IR, needsParens: Boolean = false): String = {
      ir match {
        case Const(value) => value.toString
        case p: Parameter =>
          val i = varIndices(p)
          s"params($i)"
        case b: BinaryIR =>
          b.op match {
            case PowOp =>
              val l = traverse(b.left)
              val r = traverse(b.right)
              s"Math.pow($l,$r)"
            case _ =>
              val l = traverse(b.left, true)
              val r = traverse(b.right, true)
              val n = name(b.op)
              if (needsParens)
                s"($l $n $r)"
              else
                s"$l $n $r"
          }
        case u: UnaryIR =>
          val n = name(u.op)
          val o = traverse(u.original)
          s"$n($o)"
        case i: IfIR =>
          val t = traverse(i.test)
          val nz = traverse(i.whenNonZero)
          val z = traverse(i.whenZero)
          if (needsParens)
            s"if($t == 0.0) $z else $nz"
          else
            s"(if($t == 0.0) $z else $nz)"
        case v: VarDef =>
          varTypes(v.sym) match {
            case Inline =>
              traverse(v.rhs, needsParens)
            case Local(i) =>
              val r = traverse(v.rhs)
              val n = "tmp" + i
              println(s"  val $n = $r")
              n
            case Global(i) =>
              val r = traverse(v.rhs)
              val n = s"globals($i)"
              println(s"  val $n = $r")
              n
          }
        case VarRef(sym, _) =>
          varTypes(sym) match {
            case Inline =>
              sys.error("Should not have references to inlined vars")
            case Local(i) =>
              s"tmp$i"
            case Global(i) =>
              s"globals($i)"
          }
        case MethodRef(sym, _) =>
          val i = sym.id
          s"f$i(params, globals)"
        case _: MethodDef =>
          sys.error("Should not have nested method defs")
      }
    }
  }

  private def name(b: BinaryOp): String =
    b match {
      case AddOp      => "+"
      case SubtractOp => "-"
      case MultiplyOp => "*"
      case DivideOp   => "/"
      case PowOp      => ???
    }

  private def name(u: UnaryOp): String =
    u match {
      case LogOp       => "Math.log"
      case ExpOp       => "Math.exp"
      case AbsOp       => "Math.abs"
      case RectifierOp => "MathOps.rectifier"
    }
}
