package rainier.compute.asm

import rainier.compute._

private class Locals(outputs: Seq[Real]) {
  private var numReferences = Map.empty[Real, Int]
  private def countReferences(real: Real): Unit = {
    def incReference(real: Real): Unit = {
      val prev = numReferences.getOrElse(real, 0)
      numReferences += real -> (prev + 1)
    }

    numReferences.get(real) match {
      case Some(n) => ()
      case None =>
        real match {
          case u: UnaryReal =>
            countReferences(u.original)
            incReference(u.original)
          case b: BinaryReal =>
            countReferences(b.left)
            countReferences(b.right)
            incReference(b.left)
            incReference(b.right)
          case _ => ()
        }
    }
  }

  outputs.foreach { target =>
    countReferences(target)
  }

  val numLocals = numReferences.count { case (_, i) => i > 1 }

  private var ids = Map.empty[Real, Int]
  private var nextID = 0

  //returns Some((id,firstTimeSeen)) for a local, None otherwise
  def find(real: Real): Option[(Int, Boolean)] =
    real match {
      case Constant(_) => None
      case _ =>
        val refs = numReferences.getOrElse(real, 0)
        if (refs > 1)
          ids.get(real) match {
            case Some(id) => Some((id, false))
            case None =>
              val id = nextID
              nextID += 1
              ids += real -> id
              Some((id, true))
          } else
          None
    }
}
