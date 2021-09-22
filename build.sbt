lazy val V = new {
  val SCALA_2_12 = "2.12.15"
  val SCALA_2_13 = "2.13.6"
  val Scalas = Seq(SCALA_2_13, SCALA_2_12)
  val cats = "2.6.1"
  val catsEffect = "2.5.1"
  val fs2 = "2.5.9"
  val bouncyCastle = "1.69"
  val scalaTest = "3.2.3"
  val catsEffectTestingScalatestScalacheck = "0.5.4"
  val refined = "0.9.27"
  val shapeless = "2.3.7"
  val log4cats = "1.3.1"
  val catsScalacheck = "0.3.1"
  val scalaCollectionCompat = "2.5.0"
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
  githubWorkflowJavaVersions := Seq("adopt@1.8", "adopt@1.11"),
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
  resolvers += Resolver.sonatypeRepo("releases"),
  addCompilerPlugin("org.typelevel" %% "kind-projector" % "0.13.2" cross CrossVersion.full),
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
      Seq(
        "org.typelevel" %% "cats-core" % V.cats,
        "org.typelevel" %% "cats-effect" % V.catsEffect,
        "org.bouncycastle" % "bcpg-jdk15on" % V.bouncyCastle,
        "org.bouncycastle" % "bcprov-jdk15on" % V.bouncyCastle,
        "co.fs2" %% "fs2-core" % V.fs2,
        "co.fs2" %% "fs2-io" % V.fs2,
        "com.chuusai" %% "shapeless" % V.shapeless,
        "org.scala-lang.modules" %% "scala-collection-compat" % V.scalaCollectionCompat,
        "org.typelevel" %% "log4cats-core" % V.log4cats,
        "eu.timepit" %% "refined" % V.refined,
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
        "io.chrisdavenport" %% "cats-scalacheck" % V.catsScalacheck % Test,
      )
    },
    publish / skip := true,
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
        "org.typelevel" %% "log4cats-core" % V.log4cats,
        "org.scalacheck" %% "scalacheck" % "1.15.4",
        "org.scalactic" %% "scalactic" % "3.2.9",
        "org.scalatest" %% "scalatest-core" % "3.2.10",
        "org.scalatest" %% "scalatest-matchers-core" % "3.2.10",
        "org.scalatestplus" %% "scalacheck-1-15" % "3.2.10.0",
        "org.typelevel" %% "cats-core" % V.cats,
        "org.typelevel" %% "cats-effect" % V.catsEffect,
        "eu.timepit" %% "refined" % V.refined,
        "com.chuusai" %% "shapeless" % V.shapeless,
        "eu.timepit" %% "refined-scalacheck" % V.refined,
        "io.chrisdavenport" %% "cats-scalacheck" % V.catsScalacheck,
        "co.fs2" %% "fs2-core" % V.fs2,
        "org.scala-lang.modules" %% "scala-collection-compat" % V.scalaCollectionCompat
      )
    },
    unusedCompileDependenciesFilter -= moduleFilter("org.scala-lang.modules", "scala-collection-compat"),
  ) ++ commonSettings: _*)
  .dependsOn(`fs2-pgp`)

lazy val `fs2-pgp-root` = (project in file("."))
  .settings(publish / skip := true)
  .aggregate(`fs2-pgp`, tests, `pgp-testkit`)
