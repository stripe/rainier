import wartremover.Wart

lazy val root = project.
  in(file(".")).
  aggregate(rainierCompute, rainierSampler, rainierCore, rainierNotebook).
  aggregate(rainierBenchmark, rainierTest).
  aggregate(shadedAsm).
  settings(commonSettings).
  settings(unpublished).
  settings(
    // crossScalaVersions must be set to Nil on the aggregating project
    crossScalaVersions := Nil
  )

scalafmtOnCompile in ThisBuild := true

// see https://www.wartremover.org/doc/warts.html for the full list.
wartremoverErrors in (Compile, compile) ++= Seq(
  Wart.EitherProjectionPartial,
  Wart.OptionPartial,
  Wart.Product,
  Wart.PublicInference,
  Wart.Return,
  Wart.Serializable,
  Wart.StringPlusAny,
  Wart.Throw,
  Wart.TryPartial
)

def getPublishTo(snapshot: Boolean) = {
  val nexus = "https://oss.sonatype.org/"
  if (snapshot) {
    val url = sys.props.get("publish.snapshots.url").getOrElse(nexus + "content/repositories/snapshots")
    Some("snapshots" at url)
  } else {
    val url = sys.props.get("publish.releases.url").getOrElse(nexus + "service/local/staging/deploy/maven2")
    Some("releases" at url)
  }
}

lazy val commonSettings = Seq(
  organization:= "com.stripe",
  scalaVersion := "2.12.10",
  crossScalaVersions := List("2.13.1", "2.12.10", "2.11.12"),
  releaseCrossBuild := true,
  releasePublishArtifactsAction := PgpKeys.publishSigned.value,
  homepage := Some(url("https://github.com/stripe/rainier")),
  licenses := Seq("Apache 2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")),
  publishMavenStyle := true,
  publishArtifact in Test := false,
  pomIncludeRepository := { _ => false },
  publishTo := getPublishTo(isSnapshot.value),
  autoAPIMappings := true,
  apiURL := Some(url("https://stripe.github.io/rainier/api/")),
  scmInfo := Some(
    ScmInfo(
      url("https://github.com/stripe/rainier"),
      "scm:git:git@github.com:stripe/rainier.git"
    )
  ),
  developers := List(
    Developer("avibryant", "Avi Bryant", "", url("https://twitter.com/avibryant"))
  ),
)

lazy val unpublished = Seq(publish := {}, publishLocal := {}, publishArtifact := false)

/* dependency versions */
lazy val V = new {
  val asm = "6.0"
  val evilplot = "0.6.0"
  val scalatest = "3.0.8"
  val flogger = "0.3.1"
  val almond = "0.9.1"
  val shadedAsm = "0.2.1"
  val scalameta = "4.2.3"
  val mdoc = "2.1.1"
}

// primary modules


lazy val rainierBase = project.
  in(file("rainier-base")).
  settings(name := "rainier-base").
  settings(commonSettings).
  settings(
    libraryDependencies ++= Seq(
      "com.google.flogger" % "flogger" % V.flogger,
      "com.google.flogger" % "flogger-system-backend" % V.flogger))

lazy val rainierCompute = project.
  in(file("rainier-compute")).
  dependsOn(rainierBase).
  settings(name := "rainier-compute").
  settings(commonSettings).
  settings(
    libraryDependencies ++= Seq(
      "com.stripe" % "rainier-shaded-asm_6.0" % V.shadedAsm)
  )

lazy val rainierSampler = project.
  in(file("rainier-sampler")).
  dependsOn(rainierBase).
  settings(name := "rainier-sampler").
  settings(commonSettings)

lazy val rainierCore = project.
  in(file("rainier-core")).
  settings(name := "rainier-core").
  dependsOn(rainierCompute, rainierSampler).
  settings(commonSettings)

lazy val rainierNotebook = project.
  in(file("rainier-notebook")).
  settings(name := "rainier-notebook").
  settings(commonSettings).
  settings(
    // Evilplot not yet available on 2.13.x
    crossScalaVersions := List("2.12.10"),
    resolvers ++=
      Seq(
        Resolver.bintrayRepo("cibotech", "public"),
        "jitpack" at "https://jitpack.io"),
    libraryDependencies ++=
      Seq(
        "com.cibo" %% "evilplot" % V.evilplot,
        "org.scalameta" %% "scalameta" % V.scalameta,
        "org.scalameta" %% "mdoc" % V.mdoc,
        "sh.almond" %% "jupyter-api" % V.almond)
  )

// test modules

lazy val rainierBenchmark = project.
  in(file("rainier-benchmark")).
  settings(name := "rainier-benchmark").
  enablePlugins(JmhPlugin).
  settings(
    scalacOptions ~= (_.filterNot(Set(
      "-Ywarn-value-discard")))).
  dependsOn(rainierCore).
  settings(commonSettings).
  settings(unpublished)

lazy val rainierTest = project.
  in(file("rainier-test")).
  settings(name := "rainier-test").
  dependsOn(
    rainierCore
  ).
  settings(commonSettings).
  settings(libraryDependencies ++= Seq(
    "org.scalatest" %% "scalatest" % V.scalatest)).
  settings(unpublished)

lazy val rainierDecompile = project.
  in(file("rainier-decompile")).
  settings(name := "rainier-decompile").
  dependsOn(rainierCore).
  settings(commonSettings).
  settings(unpublished)

// documentation modules

lazy val docs = project.
  in(file("rainier-docs")).
  settings(moduleName := "rainier-docs").
  enablePlugins(MdocPlugin, DocusaurusPlugin).
  dependsOn(
    rainierCore,
    rainierNotebook,
  ).
  settings(commonSettings).
  settings(
   mdocVariables := Map(
     "VERSION" -> version.value
   )
  ).
  settings(unpublished)

// shaded asm dep trickery

/* publishable project with the shaded deps */
lazy val shadedAsm = project.
  in(file(".rainier-shaded-asm")).
  // note: bump version suffix when the shaded asm jar needs to be
  // republished for a particular version of the underlying asm lib
  settings(name := "rainier-shaded-asm_6.0").
  settings(moduleName := "rainier-shaded-asm_6.0").
  settings(commonSettings).
  settings(
    crossPaths := false,
    autoScalaLibrary := false,
    exportJars := true,
    packageBin in Compile := (assembly in asmDeps).value,
    releaseVersion := { ver => ver }
  )

/* phantom project to bundle deps for shading */
lazy val asmDeps = project.
  in(file(".asm-deps")).
  settings(libraryDependencies ++= Seq(
    "org.ow2.asm" % "asm" % V.asm,
    "org.ow2.asm" % "asm-tree" % V.asm)).
  settings(commonSettings).
  settings(
    crossPaths := false,
    autoScalaLibrary := false,
    assemblyOption in assembly := (assemblyOption in assembly).value.copy(
      includeBin = false,
      includeDependency = true,
      includeScala = false),
    assemblyShadeRules in assembly := Seq(
      ShadeRule.rename("org.objectweb.asm.**" -> "com.stripe.rainier.internal.asm.@1").inAll),
    assemblyMergeStrategy in assembly := {
      case "module-info.class" => MergeStrategy.discard
      case other =>
        val oldStrategy = (assemblyMergeStrategy in assembly).value
        oldStrategy(other)
    },
  ).
  settings(unpublished)
