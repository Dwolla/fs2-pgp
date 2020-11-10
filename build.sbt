lazy val SCALA_2_12 = "2.12.12"
lazy val SCALA_2_13 = "2.13.3"
lazy val bouncyCastleV = "1.66"

inThisBuild(List(
  scalaVersion := SCALA_2_13,
  crossScalaVersions := Seq(SCALA_2_13, SCALA_2_12),
  organization := "com.dwolla",
  homepage := Option(url("https://github.com/Dwolla/fs2-pgp")),
  licenses += ("MIT", url("http://opensource.org/licenses/MIT")),
  developers := List(
    Developer(
      "bpholt",
      "Brian Holt",
      "bholt@dwolla.com",
      url("https://dwolla.com")
    )
  ),
  githubWorkflowTargetTags ++= Seq("v*"),
  githubWorkflowPublishTargetBranches :=
    Seq(RefPredicate.StartsWith(Ref.Tag("v"))),
  githubWorkflowPublish := Seq(WorkflowStep.Sbt(List("ci-release"))),
  githubWorkflowPublishPreamble += WorkflowStep.Use("olafurpg", "setup-gpg", "v2"),
  githubWorkflowPublish := Seq(
    WorkflowStep.Sbt(
      List("ci-release"),
      env = Map(
        "PGP_PASSPHRASE" -> "${{ secrets.PGP_PASSPHRASE }}",
        "PGP_SECRET" -> "${{ secrets.PGP_SECRET }}",
        "SONATYPE_PASSWORD" -> "${{ secrets.SONATYPE_PASSWORD }}",
        "SONATYPE_USERNAME" -> "${{ secrets.SONATYPE_USERNAME }}"
      )
    )
  ),
))

lazy val commonSettings = Seq(
  startYear := Option(2020),
  resolvers ++= Seq(
    Resolver.bintrayRepo("dwolla", "maven")
  ),
  resolvers += Resolver.sonatypeRepo("releases"),
  addCompilerPlugin("org.typelevel" %% "kind-projector" % "0.11.0" cross CrossVersion.full),
  addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1"),
  Compile / scalacOptions ++= {
    CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, n)) if n >= 13 => "-Ymacro-annotations" :: Nil
      case _ => Nil
    }
  },
  libraryDependencies ++= {
    CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, n)) if n >= 13 => Nil
      case _ => compilerPlugin("org.scalamacros" % "paradise" % "2.1.1" cross CrossVersion.full) :: Nil
    }
  },
)

lazy val `fs2-pgp`: Project = (project in file("core"))
  .settings(Seq(
    description := "fs2 pipes for encrypting and decrypting streams with BouncyCastle PGP",
    libraryDependencies ++= {
      val fs2V = "2.4.4"
      val log4catsV = "1.0.1"

      Seq(
        "org.typelevel" %% "cats-core" % "2.2.0",
        "org.typelevel" %% "cats-effect" % "2.2.0",
        "org.bouncycastle" % "bcpg-jdk15on" % bouncyCastleV,
        "org.bouncycastle" % "bcprov-jdk15on" % bouncyCastleV,
        "co.fs2" %% "fs2-core" % fs2V,
        "co.fs2" %% "fs2-io" % fs2V,
        "com.chuusai" %% "shapeless" % "2.3.3",
        "org.scala-lang.modules" %% "scala-collection-compat" % "2.2.0",
        "io.chrisdavenport" %% "log4cats-core" % log4catsV,
      )
    },
  ) ++ commonSettings: _*)

lazy val tests = (project in file("tests"))
  .settings(Seq(
    description := "Tests broken out into a separate project to break a circular dependency",
    libraryDependencies ++= {
      Seq(
        "org.scalatest" %% "scalatest" % "3.2.2" % Test,
        "com.codecommit" %% "cats-effect-testing-scalatest-scalacheck" % "0.4.1" % Test,
      )
    },
  ) ++ commonSettings ++ noPublishSettings: _*)
  .dependsOn(`fs2-pgp`, `pgp-testkit`)

lazy val `pgp-testkit`: Project = (project in file("testkit"))
  .settings(Seq(
    description := "Scalacheck Arbitraries for PGP resources",
    libraryDependencies ++= {
      Seq(
        "org.bouncycastle" % "bcpg-jdk15on" % bouncyCastleV,
        "org.bouncycastle" % "bcprov-jdk15on" % bouncyCastleV,
        "org.scalacheck" %% "scalacheck" % "1.15.0",
        "org.scalatest" %% "scalatest" % "3.2.2",
        "com.codecommit" %% "cats-effect-testing-scalatest-scalacheck" % "0.4.1",
      )
    },
  ) ++ commonSettings: _*)
  .dependsOn(`fs2-pgp`)

lazy val `fs2-pgp-root` = (project in file("."))
  .settings(noPublishSettings: _*)
  .aggregate(`fs2-pgp`, tests, `pgp-testkit`)

lazy val noPublishSettings = Seq(
  publish := {},
  publishLocal := {},
  publishArtifact := false,
  Keys.`package` := file(""),
)
