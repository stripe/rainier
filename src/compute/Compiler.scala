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
    case s: SumReal    => s.seq
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
        case s: SumReal =>
          sum(info.address, info.deps.map(_.address))
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
    }
    List(encode(store, opcode), left, right)
  }

  private def unary(load: Int, store: Int, op: UnaryOp): Seq[Int] = {
    val opcode = op match {
      case ExpOp => 4
      case LogOp => 5
    }
    List(encode(store, opcode), load)
  }

  private def sum(store: Int, seq: Seq[Int]): Seq[Int] = {
    encode(store, 6) :: seq.size :: seq.toList
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
        case 4 => //Exp
          math.exp(heap(next()))
        case 5 => //Log
          math.log(heap(next()))
        case 6 => //Sum
          val size = next()
          var sum = 0.0
          var i = 0
          while (i < size) {
            sum += heap(next())
            i += 1
          }
          sum
      }
      heap.update(store, result)
    }
  }
}
