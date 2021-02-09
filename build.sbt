lazy val V = new {
  val SCALA_2_12 = "2.12.12"
  val SCALA_2_13 = "2.13.4"
  val Scalas = Seq(SCALA_2_13, SCALA_2_12)
  val cats = "2.3.1"
  val catsEffect = "2.3.1"
  val bouncyCastle = "1.66"
  val scalaTest = "3.2.3"
  val catsEffectTestingScalatestScalacheck = "0.5.0"
  val refined = "0.9.20"
  val shapeless = "2.3.3"
  val scodec = "1.1.23"
}

inThisBuild(List(
  scalaVersion := V.Scalas.head,
  crossScalaVersions := V.Scalas,
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
  githubWorkflowBuild := Seq(WorkflowStep.Sbt(List("undeclaredCompileDependenciesTest", "unusedCompileDependenciesTest", "test"), name = Some("Build and test project"))),
  githubWorkflowPublish := Seq(WorkflowStep.Sbt(List("ci-release"))),
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
  addCompilerPlugin("org.typelevel" %% "kind-projector" % "0.11.2" cross CrossVersion.full),
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
      val fs2V = "2.5.0"
      val log4catsV = "1.1.1"

      Seq(
        "org.typelevel" %% "cats-core" % V.cats,
        "org.typelevel" %% "cats-effect" % V.catsEffect,
        "org.bouncycastle" % "bcpg-jdk15on" % V.bouncyCastle,
        "org.bouncycastle" % "bcprov-jdk15on" % V.bouncyCastle,
        "co.fs2" %% "fs2-core" % fs2V,
        "co.fs2" %% "fs2-io" % fs2V,
        "com.chuusai" %% "shapeless" % V.shapeless,
        "org.scala-lang.modules" %% "scala-collection-compat" % "2.2.0",
        "io.chrisdavenport" %% "log4cats-core" % log4catsV,
        "eu.timepit" %% "refined" % V.refined,
        "org.scodec" %% "scodec-bits" % "1.1.23",
      )
    },
    unusedCompileDependenciesFilter -= moduleFilter("org.scala-lang.modules", "scala-collection-compat"),
  ) ++ commonSettings: _*)

lazy val tests = (project in file("tests"))
  .settings(Seq(
    description := "Tests broken out into a separate project to break a circular dependency",
    libraryDependencies ++= {
      Seq(
        "org.scalatest" %% "scalatest" % V.scalaTest % Test,
        "com.codecommit" %% "cats-effect-testing-scalatest-scalacheck" % V.catsEffectTestingScalatestScalacheck % Test,
        "eu.timepit" %% "refined-scalacheck" % V.refined % Test,
      )
    },
    skip in publish := true,
  ) ++ commonSettings: _*)
  .dependsOn(`fs2-pgp`, `pgp-testkit`)

lazy val `pgp-testkit`: Project = (project in file("testkit"))
  .settings(Seq(
    description := "Scalacheck Arbitraries for PGP resources",
    libraryDependencies ++= {
      Seq(
        "org.bouncycastle" % "bcpg-jdk15on" % V.bouncyCastle,
        "org.bouncycastle" % "bcprov-jdk15on" % V.bouncyCastle % Runtime,
        "com.codecommit" %% "cats-effect-testing-scalatest-scalacheck" % V.catsEffectTestingScalatestScalacheck,
        "io.chrisdavenport" %% "log4cats-core" % "1.1.1",
        "org.scalacheck" %% "scalacheck" % "1.15.2",
        "org.scalactic" %% "scalactic" % "3.2.3",
        "org.scalatest" %% "scalatest-core" % "3.2.3",
        "org.scalatest" %% "scalatest-matchers-core" % "3.2.3",
        "org.scalatestplus" %% "scalacheck-1-15" % "3.2.3.0",
        "org.typelevel" %% "cats-core" % V.cats,
        "org.typelevel" %% "cats-effect" % V.catsEffect,
        "eu.timepit" %% "refined" % V.refined,
        "com.chuusai" %% "shapeless" % V.shapeless,
      )
    },
  ) ++ commonSettings: _*)
  .dependsOn(`fs2-pgp`)

lazy val `fs2-pgp-root` = (project in file("."))
  .settings(skip in publish := true)
  .aggregate(`fs2-pgp`, tests, `pgp-testkit`)
