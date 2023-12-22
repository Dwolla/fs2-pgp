lazy val V = _root_.scalafix.sbt.BuildInfo

lazy val rulesCrossVersions = Seq(V.scala213)
lazy val scala3Version = "3.3.1"

inThisBuild(
  List(
    organization := "com.dwolla",
    homepage := Some(url("https://github.com/dwolla/fs2-pgp")),
    licenses := List(
      "Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")
    ),
    developers := List(
      Developer(
        "cjsmith-0141",
        "CJ Smith",
        "connor.smith1@octoenergy.com",
        url("https://kraken.tech")
      )
    ),
    semanticdbEnabled := true,
    semanticdbVersion := scalafixSemanticdb.revision
  )
)

lazy val `fs2-pgp` = (project in file("."))
  .aggregate(
    rules.projectRefs ++
      input.projectRefs ++
      output.projectRefs ++
      tests.projectRefs: _*
  )
  .settings(
    publish / skip := true
  )

lazy val rules = projectMatrix
  .settings(
    moduleName := "scalafix",
    libraryDependencies ++= Seq(
      "ch.epfl.scala" %% "scalafix-core" % V.scalafixVersion,
    )
  )
  .defaultAxes(VirtualAxis.jvm)
  .jvmPlatform(rulesCrossVersions)

lazy val input = projectMatrix
  .settings(
    publish / skip := true,
    libraryDependencies ++= Seq(
      "com.dwolla" %% "fs2-pgp" % "0.4.3",
      "org.typelevel" %% "log4cats-noop" % "2.5.0"
    )

  )
  .defaultAxes(VirtualAxis.jvm)
  .jvmPlatform(scalaVersions = rulesCrossVersions)

lazy val output = projectMatrix
  .settings(
    publish / skip := true,
    libraryDependencies ++= Seq(
      // TODO: This feels like a chicken/egg problem
      //       do we cut a release before merging this in? 
      "net.tazato" %% "fs2-pgp" % "0.5.0",
      "org.typelevel" %% "log4cats-noop" % "2.5.0"
    )
  )
  .defaultAxes(VirtualAxis.jvm)
  .jvmPlatform(scalaVersions = rulesCrossVersions :+ scala3Version)

lazy val testsAggregate = Project("tests", file("target/testsAggregate"))
  .aggregate(tests.projectRefs: _*)
  .settings(
    publish / skip := true
  )

lazy val tests = projectMatrix
  .settings(
    publish / skip := true,
    scalafixTestkitOutputSourceDirectories :=
      TargetAxis
        .resolve(output, Compile / unmanagedSourceDirectories)
        .value,
    scalafixTestkitInputSourceDirectories :=
      TargetAxis
        .resolve(input, Compile / unmanagedSourceDirectories)
        .value,
    scalafixTestkitInputClasspath :=
      TargetAxis.resolve(input, Compile / fullClasspath).value,
    scalafixTestkitInputScalacOptions :=
      TargetAxis.resolve(input, Compile / scalacOptions).value,
    scalafixTestkitInputScalaVersion :=
      TargetAxis.resolve(input, Compile / scalaVersion).value
  )
  .defaultAxes(
    rulesCrossVersions.map(VirtualAxis.scalaABIVersion) :+ VirtualAxis.jvm: _*
  )
  .jvmPlatform(
    scalaVersions = Seq(V.scala213),
    axisValues = Seq(TargetAxis(V.scala213)),
    settings = Seq()
  )
  .dependsOn(rules)
  .enablePlugins(ScalafixTestkitPlugin)
