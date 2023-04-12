lazy val V = new {
  val SCALA_2_12 = "2.12.17"
  val SCALA_2_13 = "2.13.10"
  val Scalas = Seq(SCALA_2_13, SCALA_2_12)
  val refined = "0.10.3"
  val shapeless = "2.3.10"
  val catsScalacheck = "0.3.2"
  val scalaCollectionCompat = "2.9.0"
  val munit = "0.7.29"
  val scalacheckEffect = "1.0.4"
  val munitCatsEffect = "1.0.7"
  val expecty = "0.16.0"
  val cats = "2.9.0"
  val catsEffect = "3.4.8"
  val fs2 = "3.6.1"
  val log4cats = "2.5.0"
}

ThisBuild / scalaVersion := V.Scalas.head
ThisBuild / crossScalaVersions := V.Scalas
ThisBuild / organization := "com.dwolla"
ThisBuild / homepage := Option(url("https://github.com/Dwolla/fs2-pgp"))
ThisBuild / licenses += ("MIT", url("http://opensource.org/licenses/MIT"))
ThisBuild / startYear := Option(2020)
ThisBuild / developers := List(
  Developer(
    "bpholt",
    "Brian Holt",
    "bholt@dwolla.com",
    url("https://dwolla.com")
  )
)
ThisBuild / githubWorkflowBuild := Seq(WorkflowStep.Sbt(List("test", "mimaReportBinaryIssues", "doc")))
tpolecatScalacOptions += ScalacOptions.release("8")
ThisBuild / githubWorkflowJavaVersions := Seq(JavaSpec.temurin("17"))
ThisBuild / githubWorkflowScalaVersions := Seq("2.13", "2.12")
ThisBuild / tlCiReleaseBranches := Seq("main")
ThisBuild / tlBaseVersion := "0.4"
ThisBuild / tlSonatypeUseLegacyHost := true
ThisBuild / mergifyStewardConfig ~= {
  _.map(_.copy(mergeMinors = true, author = "dwolla-oss-scala-steward[bot]"))
}

lazy val commonSettings = Seq(
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

lazy val `fs2-pgp` = (project in file("core"))
  .settings(
    moduleName := s"${name.value}",
    description := "fs2 pipes for encrypting and decrypting streams with BouncyCastle PGP",
    libraryDependencies ++= {
      Seq(
        "org.typelevel" %% "cats-core" % V.cats,
        "org.typelevel" %% "cats-effect-kernel" % V.catsEffect,
        "org.typelevel" %% "cats-effect" % V.catsEffect,
        "org.bouncycastle" % "bcpg-jdk18on" % "1.72.2",
        "org.bouncycastle" % "bcprov-jdk18on" % "1.73",
        "co.fs2" %% "fs2-core" % V.fs2,
        "co.fs2" %% "fs2-io" % V.fs2,
        "com.chuusai" %% "shapeless" % V.shapeless,
        "org.scala-lang.modules" %% "scala-collection-compat" % V.scalaCollectionCompat,
        "org.typelevel" %% "log4cats-core" % V.log4cats,
        "eu.timepit" %% "refined" % V.refined,
      )
    },
    unusedCompileDependenciesFilter -= moduleFilter("org.scala-lang.modules", "scala-collection-compat"),
    mimaBinaryIssueFilters ++= {
      import com.typesafe.tools.mima.core._
      Seq(
        // the CanCreateDecryptorFactory filters ignore a class and companion object that should have been package private
        // and have been replaced with an alternative implementation that is package private
        ProblemFilters.exclude[MissingClassProblem]("com.dwolla.security.crypto.CanCreateDecryptorFactory"),
        ProblemFilters.exclude[MissingClassProblem]("com.dwolla.security.crypto.CanCreateDecryptorFactory$"),
      )
    },
  )
  .settings(commonSettings: _*)

lazy val tests = (project in file("tests"))
  .settings(
    description := "Tests broken out into a separate project to break a circular dependency",
    libraryDependencies ++= {
      Seq(
        "org.scalameta" %% "munit" % V.munit % Test,
        "org.typelevel" %% "scalacheck-effect" % V.scalacheckEffect % Test,
        "org.typelevel" %% "scalacheck-effect-munit" % V.scalacheckEffect % Test,
        "org.typelevel" %% "munit-cats-effect-3" % V.munitCatsEffect % Test,
        "eu.timepit" %% "refined-scalacheck" % V.refined % Test,
        "io.chrisdavenport" %% "cats-scalacheck" % V.catsScalacheck % Test,
        "org.typelevel" %% "log4cats-noop" % V.log4cats % Test,
        "dev.holt" %% "java-time-literals" % "1.1.0" % Test,
        "com.eed3si9n.expecty" %% "expecty" % V.expecty % Test,
      )
    },
    publishArtifact := false,
  )
  .dependsOn(`fs2-pgp`, `pgp-testkit`)
  .settings(commonSettings: _*)

lazy val `pgp-testkit` = (project in file("testkit"))
  .settings(
    moduleName := s"${name.value}",
    description := "Scalacheck Arbitraries for PGP resources",
    libraryDependencies ++= {
      Seq(
        "org.bouncycastle" % "bcpg-jdk18on" % "1.72.2",
        "org.bouncycastle" % "bcprov-jdk18on" % "1.73",
        "org.scalacheck" %% "scalacheck" % "1.17.0",
        "org.typelevel" %% "cats-core" % V.cats,
        "org.typelevel" %% "cats-effect-kernel" % V.catsEffect,
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
  )
  .dependsOn(`fs2-pgp`)
  .settings(commonSettings: _*)

lazy val `fs2-pgp-root` = (project in file("."))
  .settings(publishArtifact := false)
  .aggregate(
    `fs2-pgp`,
    tests,
    `pgp-testkit`,
  )
