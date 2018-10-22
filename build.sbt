ThisBuild / bazelScalaRulesVersion := "8359fc6781cf3102e918c84cb1638a1b1e050ce0"

lazy val root = project.
  in(file(".")).
  aggregate(rainierCore, rainierPlot, rainierCats, rainierScalacheck).
  aggregate(rainierDocs, rainierExample).
  aggregate(rainierBenchmark, rainierTests).
  aggregate(shadedAsm).
  settings(commonSettings).
  settings(unpublished).
  settings(
    crossScalaVersions := Nil,
    bazelWorkspaceGenerate := true,
    bazelBuildGenerate := false,
    bazelCustomWorkspace :=
      WorkspacePrelude +:
        MavenBindings +:
        BazelString(
          """
          |git_repository(
          |    name = "com_github_johnynek_bazel_jar_jar",
          |    commit = "258c7288db8a034e087b4d65a52546830633a4f1",
          |    remote = "git://github.com/johnynek/bazel_jar_jar.git",
          |)
          |load(
          |    "@com_github_johnynek_bazel_jar_jar//:jar_jar.bzl",
          |    "jar_jar_repositories",
          |)
          |jar_jar_repositories()
          |""".stripMargin
        )
  )

scalafmtOnCompile in ThisBuild := true

lazy val commonSettings = Seq(
  organization:= "com.stripe",
  scalaVersion := "2.12.7",
  crossScalaVersions := List("2.11.12", scalaVersion.value),
  releaseCrossBuild := true,
  releasePublishArtifactsAction := PgpKeys.publishSigned.value,
  homepage := Some(url("https://github.com/stripe/rainier")),
  licenses := Seq("Apache 2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")),
  publishMavenStyle := true,
  publishArtifact in Test := false,
  pomIncludeRepository := { _ => false },
  publishTo := {
    val nexus = "https://oss.sonatype.org/"
    if (isSnapshot.value)
      Some("snapshots" at nexus + "content/repositories/snapshots")
    else
      Some("releases"  at nexus + "service/local/staging/deploy/maven2")
  },
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

// evilplot is 2.12 only, so these settings are needed for projects
// depending on evilplot
lazy val evilPlotCrossSettings = Seq(
  crossScalaVersions ~= { _.filter(_.startsWith("2.12")) })

/* dependency versions */
lazy val V = new {
  val asm = "6.0"
  val cats = "1.1.0"
  val evilplot = "0.2.0"
  val scalacheck = "1.14.0"
  val scalatest = "3.0.5"
}

// primary modules

lazy val rainierCore = project.
  in(file("rainier-core")).
  settings(name := "rainier-core").
  dependsOn(shadedAsm).
  settings(commonSettings).
  settings(
    bazelRuleDeps := (Deps(Compile) - ScalaLib(Compile)) +
      BazelDep("//.rainier-shaded-asm", "asmTreeShaded") +
      BazelDep("//.rainier-shaded-asm", "asmShaded") -
      BazelDep("//.rainier-shaded-asm", "shadedAsm"),
  )

lazy val rainierPlot = project.
  in(file("rainier-plot")).
  settings(name := "rainier-plot").
  dependsOn(rainierCore).
  settings(commonSettings).
  settings(evilPlotCrossSettings).
  settings(
    resolvers += Resolver.bintrayRepo("cibotech", "public"),
    libraryDependencies += "com.cibo" %% "evilplot" % V.evilplot)

lazy val rainierCats = project.
  in(file("rainier-cats")).
  settings(name := "rainier-cats").
  dependsOn(rainierCore).
  dependsOn(rainierScalacheck % "test").
  settings(commonSettings).
  settings(libraryDependencies ++= Seq(
    "org.typelevel" %% "cats-core" % V.cats))

lazy val rainierScalacheck = project.
  in(file("rainier-scalacheck")).
  settings(name := "rainier-scalacheck").
  dependsOn(rainierCore).
  settings(commonSettings).
  settings(libraryDependencies ++= Seq(
    "org.typelevel" %% "cats-core" % V.cats,
    "org.scalacheck" %% "scalacheck" % V.scalacheck))

// documentation modules

lazy val rainierDocs = project.
  in(file("rainier-docs")).
  settings(name := "rainier-docs").
  enablePlugins(TutPlugin).
  dependsOn(rainierCore).
  settings(commonSettings).
  settings(
    scalacOptions in Tut ~= {
      _.filterNot(Set("-Ywarn-unused-import", "-Yno-predef", "-Ywarn-unused:imports"))
    },
    // todo: uncomment once docs generation is deterministic
    // tutTargetDirectory := (baseDirectory in LocalRootProject).value / "docs"
  ).
  settings(unpublished)

lazy val rainierExample = project.
  in(file("rainier-example")).
  settings(name := "rainier-example").
  dependsOn(
    rainierCore,
    rainierPlot,
  ).
  settings(commonSettings).
  settings(evilPlotCrossSettings).
  settings(unpublished).
  settings(bazelCustomBuild := BuildPrelude +: BuildTargets +: BazelString(
    """
      |scala_binary(
      |    name = 'logNormal',
      |    deps = [
      |        ':rainierExample',
      |    ],
      |    visibility = [
      |        '//visibility:public',
      |    ],
      |    main_class = 'com.stripe.rainier.example.FitNormal'
      |)
    """.stripMargin
  ))

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
    rainierCore,
    rainierCats,
    rainierScalacheck,
  ).
  settings(commonSettings).
  settings(libraryDependencies ++= Seq(
    "org.scalatest" %% "scalatest" % V.scalatest,
    "org.scalacheck" %% "scalacheck" % V.scalacheck,
    "org.typelevel" %% "cats-laws" % V.cats,
    "org.typelevel" %% "cats-testkit" % V.cats)).
  settings(unpublished)

// shaded asm dep trickery

/* publishable project with the shaded deps */
lazy val shadedAsm = project.
  in(file(".rainier-shaded-asm")).
  settings(name := "rainier-shaded-asm").
  settings(commonSettings).
  settings(
    // note: bump version suffix when the shaded asm jar needs to be
    // republished for a particular version of the underlying asm lib
    version := s"${V.asm}_0",
    crossPaths := false,
    autoScalaLibrary := false,
    exportJars := true,
    packageBin in Compile := (assembly in asmDeps).value,
    releaseVersion := { ver => ver },
    bazelCustomBuild := BazelString(
      """
        |load(
        |  '@io_bazel_rules_scala//scala:scala.bzl',
        |  'scala_binary',
        |  'scala_library',
        |  'scala_test'
        |)
        |
        |load(
        |    "@com_github_johnynek_bazel_jar_jar//:jar_jar.bzl",
        |    "jar_jar"
        |)
        |
        |jar_jar(
        |    name = "asmShaded",
        |    input_jar = "@org_ow2_asm_asm//jar",
        |    rules = "shade_rule",
        |    visibility = [
        |      '//visibility:public',
        |    ]
        |)
        |
        |jar_jar(
        |    name = "asmTreeShaded",
        |    input_jar = "@org_ow2_asm_asm_tree//jar",
        |    rules = "shade_rule",
        |    visibility = [
        |      '//visibility:public',
        |    ],
        |)
      """.stripMargin)
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
