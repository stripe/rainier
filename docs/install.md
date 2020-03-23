---
id: install
title: Getting Rainier
---

To add Rainier to your project include the following in your `build.sbt`:

```scala
resolvers += Resolver.bintrayRepo("rainier", "maven")
libraryDependencies += "com.stripe" %% "rainier-core" % "@VERSION@"
```

Or, in [Ammonite](https://ammonite.io/), import it like this:

```scala
interp.repositories() ++= Seq(coursierapi.MavenRepository.of("https://dl.bintray.com/rainier/maven"))
import $ivy.`com.stripe::rainier-core:@VERSION@`
```

For plotting and exploratory work, we suggest [Using Jupyter](jupyter.md).