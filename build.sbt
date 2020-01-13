import wartremover.Wart

lazy val root = project.
  in(file(".")).
  aggregate(rainierCore).
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
}

// primary modules

lazy val rainierCore = project.
  in(file("rainier-core")).
  settings(name := "rainier-core").
  settings(commonSettings).
  settings(
    libraryDependencies ++= Seq(
      "com.google.flogger" % "flogger" % V.flogger,
      "com.google.flogger" % "flogger-system-backend" % V.flogger,
      "com.stripe" % "rainier-shaded-asm_6.0" % V.shadedAsm)
  )

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
