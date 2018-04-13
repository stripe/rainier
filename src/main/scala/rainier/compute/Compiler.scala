package rainier.compute

import scala.collection.mutable.{HashMap, ArrayBuffer, ListBuffer, ArrayStack}

object Compiler {
  case class CompiledFunction(targetAddresses: Map[Real, Int],
                              variableAddresses: Map[Variable, Int],
                              constantAddresses: Map[Constant, Int],
                              instructions: Array[Int],
                              heapSize: Int) {

    val heap = Array.fill[Double](heapSize)(0.0)
    constantAddresses.foreach {
      case (Constant(d), i) =>
        heap.update(i, d)
    }

    def apply(input: Map[Variable, Double]): Map[Real, Double] = {
      input.foreach {
        case (v, d) =>
          heap.update(variableAddresses(v), d)
      }
      Compiler.execute(heap, instructions)
      targetAddresses.map { case (t, i) => t -> heap(i) }.toMap
    }

    def trace: Unit = {
      val targetIndices = targetAddresses.values.toList
      val variableIndices = variableAddresses.values.toList
      val constants = constantAddresses.map { case (Constant(v), i) => i -> v }
      val labels = 0
        .to(heapSize)
        .map { i =>
          val t = targetIndices.indexOf(i)
          val v = variableIndices.indexOf(i)
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

      Compiler.trace(instructions, labels)
    }
  }

  def apply(targets: Seq[Real]): CompiledFunction = {
    val infos = topologicalSort(targets)
    findLastUses(infos)
    val heapSize = allocateAddresses(infos)

    CompiledFunction(
      targetAddresses = collectAddresses(infos.filter(_.isTarget)) {
        case r => r
      },
      variableAddresses = collectAddresses(infos) { case v: Variable => v },
      constantAddresses = collectAddresses(infos) { case c: Constant => c },
      instructions = generateInstructions(infos),
      heapSize = heapSize
    )
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

  private def collectAddresses[T](seq: Seq[Info])(
      fn: PartialFunction[Real, T]): Map[T, Int] =
    seq.foldLeft(Map.empty[T, Int]) {
      case (map, info) =>
        if (fn.isDefinedAt(info.real))
          map + (fn(info.real) -> info.address)
        else
          map
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
