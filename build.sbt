lazy val commonSettings = Seq(
  organization := "com.dwolla",
  homepage := Some(url("https://github.com/Dwolla/fs2-pgp")),
  licenses += ("MIT", url("http://opensource.org/licenses/MIT")),
  releaseVersionBump := sbtrelease.Version.Bump.Minor,
  releaseCrossBuild := false,
  releaseProcess := {
    import sbtrelease.ReleaseStateTransformations._
    Seq[ReleaseStep](
      checkSnapshotDependencies,
      inquireVersions,
      runClean,
      releaseStepCommandAndRemaining("+test"),
      setReleaseVersion,
      commitReleaseVersion,
      tagRelease,
      releaseStepCommandAndRemaining("+publish"),
      setNextVersion,
      commitNextVersion,
      pushChanges
    )
  },
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

lazy val bintraySettings = Seq(
  bintrayVcsUrl := homepage.value.map(_.toString),
  bintrayRepository := "maven",
  bintrayOrganization := Option("dwolla"),
  pomIncludeRepository := { _ => false }
)

lazy val `fs2-pgp` = (project in file("."))
  .settings(Seq(
    description := "fs2 pipes for encrypting and decrypting streams with BouncyCastle PGP",
    libraryDependencies ++= {
      val bouncyCastleV = "1.66"
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
        "org.scalatest" %% "scalatest" % "3.2.2" % Test,
        "com.codecommit" %% "cats-effect-testing-scalatest-scalacheck" % "0.4.1" % Test,
      )
    },
  ) ++ commonSettings ++ bintraySettings: _*)

lazy val noPublishSettings = Seq(
  publish := {},
  publishLocal := {},
  publishArtifact := false,
  Keys.`package` := file(""),
)
