---
id: install
title: Getting Rainier
---

To add Rainier to your project include the following in your `build.sbt`:

```scala
libraryDependencies += "com.stripe" % "rainier-core" % "@VERSION@"
```

Or, in [Ammonite](https://ammonite.io/), import it like this:

```scala
import $ivy.`com.stripe::rainier-core:@VERSION@`
```

For plotting and exploratory work, we suggest [Using Jupyter](jupyter.md).