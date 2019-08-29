package com.stripe.rainier.core

object Events {
    def observe[T](seq: Seq[T], dist: Distribution[T]): RandomVariable[Generator[T]] =
        dist.fit(seq).map(_.generator)

    def observe[X,Y](seq: Seq[(X,Y)])(fn: X => Distribution[Y]): RandomVariable[Seq[X] => Generator[Seq[(X,Y)]]] = {
        val rvs = seq.map { case (x, z) => fn(x).fit(z)}
        RandomVariable.traverse(rvs).map { _ =>
            (seq: Seq[X]) => 
                Generator.traverse(seq.map{x =>
                    fn(x).generator.map{y => x -> y}
                })
      }
    }

    def observe[X,Y](seq: Seq[(X,Y)], fn: Fn[X,Distribution[Y]]): RandomVariable[Seq[X] => Generator[Seq[(X,Y)]]] = {
        val lh = Fn.toLikelihood[X,Distribution[Y],Y](implicitly[ToLikelihood[Distribution[Y],Y]])(fn)
        lh.fit(seq).map{_ =>
            (seq: Seq[X]) => 
                Generator.traverse(seq.zip(fn(seq)).map{case (x,dist) =>
                    dist.generator.map{y => x -> y}
                })       
        }
    }

    def observe[X,Y](xs: Seq[X], ys: Seq[Y])(fn: X => Distribution[Y]): RandomVariable[Seq[X] => Generator[Seq[(X,Y)]]] =
        observe(xs.zip(ys))(fn)

    def observe[X,Y](xs: Seq[X], ys: Seq[Y], fn: Fn[X,Distribution[Y]]): RandomVariable[Seq[X] => Generator[Seq[(X,Y)]]] =
        observe(xs.zip(ys), fn)
}