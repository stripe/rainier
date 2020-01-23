import wartremover.Wart

lazy val root = project.
  in(file(".")).
  aggregate(rainierCompute, rainierCore, rainierPlot).
  aggregate(rainierBenchmark, rainierTests).
  aggregate(shadedAsm).
  settings(commonSettings).
  settings(unpublished)

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
  crossScalaVersions := List(scalaVersion.value, "2.11.12"),
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
  val scalatest = "3.0.5"
  val flogger = "0.3.1"
  val almond = "0.3.0"
  val scala = "2.12.8"
  val shadedAsm = "0.2.1"
  val scalameta = "4.2.3"
  val mdoc = "2.1.1"
}

// primary modules


lazy val rainierCompute = project.
  in(file("rainier-compute")).
  settings(name := "rainier-compute").
  settings(commonSettings).
  settings(
    libraryDependencies ++= Seq(
      "com.google.flogger" % "flogger" % V.flogger,
      "com.google.flogger" % "flogger-system-backend" % V.flogger,
      "com.stripe" % "rainier-shaded-asm_6.0" % V.shadedAsm)
  )

lazy val rainierCore = project.
  in(file("rainier-core")).
  settings(name := "rainier-core").
  dependsOn(rainierCompute).
  settings(commonSettings)

lazy val rainierPlot = project.
  in(file("rainier-plot")).
  settings(name := "rainier-plot").
  dependsOn(rainierCore).
  settings(commonSettings).
  settings(
    resolvers ++=
      Seq(
        Resolver.bintrayRepo("cibotech", "public"),
        "jitpack" at "https://jitpack.io"),
    libraryDependencies ++=
      Seq(
        "com.cibo" %% "evilplot" % V.evilplot,
        "org.scalameta" %% "scalameta" % V.scalameta,
        "org.scalameta" %% "mdoc" % V.mdoc,
        "sh.almond" %% "interpreter-api" % V.almond)
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

lazy val rainierTests = project.
  in(file("rainier-tests")).
  settings(name := "rainier-tests").
  dependsOn(
    rainierCore
  ).
  settings(commonSettings).
  settings(libraryDependencies ++= Seq(
    "org.scalatest" %% "scalatest" % V.scalatest)).
  settings(unpublished)

lazy val rainierTrace = project.
  in(file("rainier-trace")).
  settings(name := "rainier-trace").
  dependsOn(rainierCore).
  settings(commonSettings).
  settings(unpublished)

// documentation modules

lazy val rainierDocs = project.
  in(file("docs")).
  settings(name := "rainier-docs").
  enablePlugins(MdocPlugin).
  dependsOn(
    rainierCore,
    rainierPlot,
  ).
  settings(commonSettings).
  settings(
   mdocIn := new java.io.File("rainier-docs"),
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
