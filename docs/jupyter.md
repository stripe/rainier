---
id: jupyter
title: Using Jupyter
---

[Jupyter](https://jupyter.org/) is a great way to work with Rainier. 

## Using a customized kernel install

The `install-kernel.sh` script will install a pre-configured [Almond](http://almond.sh) kernel suitable for Rainier. If you have Jupyter installed (eg with `brew install jupyter`), and run that script, you should then be able to use the `Rainier (Scala 2.12)` kernel for all your Bayesian Scala needs.

Specifically, this script installs a kernel that:

* Uses Scala 2.12, which is currently the only version `rainier-notebook` is available for
* Uses Almond 0.9.0, which seems to currently be more reliable than the latest release
* Includes the `https://dl.bintray.com/rainier/maven` repository
* Includes the `https://dl.bintray.com/cibotech/public` repository, which is needed for the [EvilPlot](https://cibotech.github.io/evilplot/) dependency
* Configures the kernel with a custom id, name, and logo

At the top of your notebook, you need to import the `core` and `notebook` modules using Ammonite:

```scala
import $ivy.`com.stripe::rainier-core:@VERSION@`
import $ivy.`com.stripe::rainier-notebook:@VERSION@`
```

Then in a separate cell, you can import the Rainier packages:

```scala
import com.stripe.rainier.compute._
import com.stripe.rainier.core._
import com.stripe.rainier.notebook._
```

Finally, you can register a custom pretty-printer for better notebook output of Rainier objects:

```scala
PrettyPrint.register(repl)
```

## Using a standard Almond kernel

If you are not using the custom kernel installer, make sure to use the `Scala 2.12` kernel, and add the following to the top of your notebook in its own cell:

```scala
interp.repositories() ++= Seq(
  coursierapi.MavenRepository.of("https://dl.bintray.com/rainier/maven"),
  coursierapi.MavenRepository.of("https://dl.bintray.com/cibotech/public"),
  coursierapi.MavenRepository.of("https://jitpack.io/")
)
```