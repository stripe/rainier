import com.stripe.rainier.bench.stan._
val x = new GLMMPoisson2
x.nSites = "2"
x.nYears = "2"
x.observeUsing = "fn"
val m = x.model
m.targetGroup.graphViz{s => !s.contains("grad") && s.contains("L_")}.write("/tmp/foo.gv")
m.targetGroup.irViz{s => !s.contains("grad") && s.contains("L_")}.write("/tmp/foo2.gv")