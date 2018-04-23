package rainier.compute

trait ToReal[-N] {
  def apply(n: N): Real
}

object ToReal {
  implicit def numeric[N: Numeric] = new ToReal[N] {
    def apply(n: N) = Real(n)
  }

  implicit val identity = new ToReal[Real] {
    def apply(n: Real) = n
  }
}

trait ToReal_+[-N] extends ToReal[N] {
  def apply(n: N): Real_+
}

object ToReal_+ {
  implicit def numeric[N: Numeric] = new ToReal_+[N] {
    def apply(n: N) = Real_+(n)
  }

  implicit val identity = new ToReal_+[Real_+] {
    def apply(n: Real_+) = n
  }
}
