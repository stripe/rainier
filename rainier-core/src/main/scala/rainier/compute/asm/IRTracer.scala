package rainier.compute.asm

import rainier.compute._

object IRTracer {
  def trace(output: Real): Unit = trace(output.variables, List(output))

  def trace(inputs: Seq[Variable], outputs: Seq[Real]): Unit = {
    val translator = new Translator
    val irs = outputs.map { real =>
      translator.toIR(real)
    }
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
    println(" val globals = new Array[Double](" + globalSize + ")")
    println(
      " Array(" + outputs
        .map { i =>
          "f" + i + "(params, globals)"
        }
        .mkString(",") + ")")
    println("}")
  }

  private def trace(method: MethodDef,
                    inputs: Seq[Variable],
                    varTypes: VarTypes): Unit = {
    val varIndices = inputs.zipWithIndex.toMap

    println(
      "def f" + method.sym.id + "(params: Array[Double], globals: Array[Double]): Double = {")
    traverse(method.rhs)
    println("")
    println("}")

    def traverse(ir: IR): Unit = {
      ir match {
        case Const(value) =>
          print(value)
        case Parameter(variable) =>
          print("params(" + varIndices(variable) + ")")
        case b: BinaryIR =>
          b.op match {
            case PowOp =>
              print("Math.pow(")
              traverse(b.left)
              print(",")
              traverse(b.right)
            case _ =>
              print("(")
              traverse(b.left)
              print(name(b.op))
              traverse(b.right)
              print(")")
          }
        case u: UnaryIR =>
          print(name(u.op))
          print("(")
          traverse(u.original)
          print(")")
        case v: VarDef =>
          varTypes(v.sym) match {
            case Inline =>
              traverse(v.rhs)
            case Local(i) =>
              print("  val tmp" + i + " = ")
              traverse(v.rhs)
              println("")
            case Global(i) =>
              print("  globals(" + i + ") = ")
              traverse(v.rhs)
              println("")
          }
        case VarRef(sym) =>
          varTypes(sym) match {
            case Inline =>
              sys.error("Should not have references to inlined vars")
            case Local(i) =>
              print("tmp" + i)
            case Global(i) =>
              print("globals(" + i + ")")
          }
        case MethodRef(sym) =>
          print("f" + sym.id + "(params, globals)")
        case m: MethodDef =>
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
      case LogOp => "Math.log"
      case ExpOp => "Math.exp"
      case AbsOp => "Math.abs"
    }
}
