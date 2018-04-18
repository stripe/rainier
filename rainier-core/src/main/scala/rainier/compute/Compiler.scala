package rainier.compute

trait Compiler {
  def compile(inputs: Seq[Variable],
              outputs: Seq[Real]): Array[Double] => Array[Double]

  def compile(inputs: Seq[Variable], output: Real): Array[Double] => Double =
    compile(inputs, List(output)).andThen { array =>
      array(0)
    }

  def compileGradient(inputs: Seq[Variable],
                      output: Real): Array[Double] => (Double, Array[Double]) =
    compile(inputs, output :: Gradient.derive(inputs, output).toList).andThen {
      array =>
        (array.head, array.tail)
    }
}

object Compiler {
  val default: Compiler = ArrayCompiler
}

import scala.collection.mutable.{HashMap, ArrayBuffer, ListBuffer, ArrayStack}

object ArrayCompiler extends Compiler {

  def compile(inputs: Seq[Variable],
              outputs: Seq[Real]): Array[Double] => Array[Double] = {
    val infos = topologicalSort(outputs)
    findLastUses(infos)
    val heapSize = allocateAddresses(infos)
    val instructions = generateInstructions(infos)

    val addressMap = infos.map { info =>
      info.real -> info.address
    }.toMap
    val inputAddresses = inputs.map { v =>
      addressMap(v)
    }.toArray
    val outputAddresses = outputs.map { r =>
      addressMap(r)
    }.toArray

    val constantAddresses = addressMap.collect {
      case (Constant(d), i) => (d, i)
    }.toList

    CompiledFunction(outputAddresses,
                     inputAddresses,
                     constantAddresses,
                     instructions,
                     heapSize)
  }

  private case class CompiledFunction(outputAddresses: Array[Int],
                                      inputAddresses: Array[Int],
                                      constantAddresses: Seq[(Double, Int)],
                                      instructions: Array[Int],
                                      heapSize: Int)
      extends Function1[Array[Double], Array[Double]] {

    val heap = Array.fill[Double](heapSize)(0.0)
    constantAddresses.foreach {
      case (d, i) =>
        heap.update(i, d)
    }

    def apply(input: Array[Double]): Array[Double] = {
      var i = 0
      while (i < input.size) {
        heap.update(inputAddresses(i), input(i))
        i += 1
      }

      ArrayCompiler.execute(heap, instructions)

      val output = new Array[Double](outputAddresses.size)
      i = 0
      while (i < output.size) {
        output.update(i, heap(outputAddresses(i)))
        i += 1
      }
      output
    }

    def trace: Unit = {
      val constants = constantAddresses.map(_.swap).toMap
      val labels = 0
        .to(heapSize)
        .map { i =>
          val v = inputAddresses.indexOf(i)
          val t = outputAddresses.indexOf(i)
          i ->
            (if (t >= 0)
               s"output$t"
             else if (v >= 0)
               s"input$v"
             else
               constants.get(i) match {
                 case Some(c) => c.toString
                 case None    => s"tmp$i"
               })
        }
        .toMap

      ArrayCompiler.trace(instructions, labels)
    }
  }

  private class Info(val real: Real, val deps: Seq[Info]) {
    var isTarget = false
    var lastUse: Option[Info] = None
    var addressOpt: Option[Int] = None
    def address = addressOpt.get

    def canReuseAddress = real match {
      case c: Constant => false
      case v: Variable => false
      case _           => !isTarget
    }
  }

  private def deps(real: Real): Seq[Real] = real match {
    case b: BinaryReal => List(b.left, b.right)
    case u: UnaryReal  => List(u.original)
    case c: Constant   => Nil
    case v: Variable   => Nil
  }

  private def topologicalSort(targets: Seq[Real]): Seq[Info] = {
    val infoMap = HashMap.empty[Real, Info]
    var infoBuf = ListBuffer.empty[Info]

    def getInfo(real: Real): Info = infoMap.get(real) match {
      case Some(info) => info
      case None => {
        val depInfos = deps(real).map(getInfo)
        val info = new Info(real, depInfos)
        infoMap.update(real, info)
        infoBuf += info
        info
      }
    }

    targets.foreach { target =>
      getInfo(target).isTarget = true
    }

    infoBuf.toList
  }

  private def findLastUses(seq: Seq[Info]): Unit = {
    seq.foreach { info =>
      info.deps.foreach { dep =>
        dep.lastUse = Some(info)
      }
    }
  }

  private def allocateAddresses(seq: Seq[Info]): Int = {
    val free = ArrayStack.empty[Int]
    var heapSize = 0

    seq.zipWithIndex.foreach {
      case (info, i) =>
        info.deps.toSet.foreach { dep: Info =>
          if (dep.canReuseAddress && dep.lastUse.get == info) {
            free.push(dep.address)
          }
        }

        if (free.isEmpty || !info.canReuseAddress) {
          info.addressOpt = Some(heapSize)
          heapSize += 1
        } else {
          info.addressOpt = Some(free.pop)
        }
    }
    heapSize
  }

  private def generateInstructions(seq: Seq[Info]): Array[Int] = {
    val instructionsBuf = ArrayBuffer.empty[Int]
    seq.foreach { info =>
      instructionsBuf ++= (info.real match {
        case b: BinaryReal =>
          binary(info.deps(0).address, info.deps(1).address, info.address, b.op)
        case u: UnaryReal =>
          unary(info.deps.head.address, info.address, u.op)
        case _ => Nil
      })
    }
    instructionsBuf.toArray
  }

  private val addressBits = 28
  private def encode(store: Int, opcode: Int): Int =
    (opcode << addressBits) | store

  private def binary(left: Int,
                     right: Int,
                     store: Int,
                     op: BinaryOp): Seq[Int] = {
    val opcode = op match {
      case AddOp      => 0
      case DivideOp   => 1
      case MultiplyOp => 2
      case SubtractOp => 3
      case OrOp       => 4
      case AndOp      => 5
      case AndNotOp   => 6
    }
    List(encode(store, opcode), left, right)
  }

  private def unary(load: Int, store: Int, op: UnaryOp): Seq[Int] = {
    val opcode = op match {
      case ExpOp => 7
      case LogOp => 8
      case AbsOp => 9
    }
    List(encode(store, opcode), load)
  }

  private val bitmask = ((1 << addressBits) - 1)
  def execute(heap: Array[Double], instructions: Array[Int]): Unit = {
    var pc = 0
    def next(): Int = {
      val inst = instructions(pc)
      pc += 1
      inst
    }
    val n = instructions.size
    while (pc < n) {
      val inst = next()
      val opcode = inst >>> addressBits
      val store = inst & bitmask
      val result = opcode match {
        case 0 => //Add
          heap(next()) + heap(next())
        case 1 => //Divide
          heap(next()) / heap(next())
        case 2 => //Multiply
          heap(next()) * heap(next())
        case 3 => //Subtract
          heap(next()) - heap(next())
        case 4 => //Or
          val left = heap(next())
          val right = heap(next())
          if (left == 0) right else left
        case 5 => //And
          val left = heap(next())
          val right = heap(next())
          if (right == 0) 0 else left
        case 6 => //AndNot
          val left = heap(next())
          val right = heap(next())
          if (right == 0) left else 0
        case 7 => //Exp
          math.exp(heap(next()))
        case 8 => //Log
          math.log(heap(next()))
        case 9 => //Abs
          heap(next()).abs
      }
      heap.update(store, result)
    }
  }

  def trace(instructions: Array[Int], labels: Map[Int, String]): Unit = {
    var pc = 0
    def next(): Int = {
      val inst = instructions(pc)
      pc += 1
      inst
    }

    def nextLabel: String = labels(next())

    val n = instructions.size
    while (pc < n) {
      val inst = next()
      val opcode = inst >>> addressBits
      val store = labels(inst & bitmask)
      val result = opcode match {
        case 0 => //Add
          s"$nextLabel + $nextLabel"
        case 1 => //Divide
          s"$nextLabel / $nextLabel"
        case 2 => //Multiply
          s"$nextLabel * $nextLabel"
        case 3 => //Subtract
          s"$nextLabel - $nextLabel"
        case 4 => //Or
          s"$nextLabel || $nextLabel"
        case 5 => //And
          s"$nextLabel && $nextLabel"
        case 6 => //AndNot
          s"$nextLabel &&! $nextLabel"
        case 7 => //Exp
          s"exp($nextLabel)"
        case 8 => //Log
          s"log($nextLabel)"
        case 9 => //Abs
          s"abs($nextLabel)"
      }
      println(s"$store = $result")
    }
  }
}
