import ConfigAxes._

lazy val V = new {
  val SCALA_2_12 = "2.12.15"
  val SCALA_2_13 = "2.13.8"
  val Scalas = Seq(SCALA_2_13, SCALA_2_12)
  val bouncyCastle = "1.70"
  val refined = "0.9.28"
  val shapeless = "2.3.7"
  val catsScalacheck = "0.3.0"
  val scalaCollectionCompat = "2.6.0"
  val munit = "0.7.29"
  val scalacheckEffect = "1.0.3"
  val munitCatsEffect = "1.0.7"
  val expecty = "0.15.4"
  val cats = "2.7.0"
}

lazy val CE2_V = new {
  val catsEffect = "2.5.4"
  val fs2 = "2.5.10"
  val log4cats = "1.4.0"
}

lazy val CE3_V = new {
  val catsEffect = "3.2.3"
  val fs2 = "3.1.0"
  val log4cats = "2.1.1"
}

inThisBuild(List(
  scalaVersion := V.Scalas.head,
  crossScalaVersions := V.Scalas,
  organization := "com.dwolla",
  homepage := Option(url("https://github.com/Dwolla/fs2-pgp")),
  licenses += ("MIT", url("http://opensource.org/licenses/MIT")),
  startYear := Option(2020),
  developers := List(
    Developer(
      "bpholt",
      "Brian Holt",
      "bholt@dwolla.com",
      url("https://dwolla.com")
    )
  ),
))

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

lazy val `fs2-pgp` = (projectMatrix in file("core"))
  .customRow(V.Scalas, Seq(CatsEffect2Axis, VirtualAxis.jvm), _.settings(
    moduleName := s"${name.value}-ce2",
    description := "fs2 pipes for encrypting and decrypting streams with BouncyCastle PGP",
    Compile / unmanagedSourceDirectories += (Compile / scalaSource).value.getParentFile / s"scala-${CatsEffect2Axis.directorySuffix}",
    libraryDependencies ++= {
      Seq(
        "org.typelevel" %% "cats-core" % V.cats,
        "org.typelevel" %% "cats-effect" % CE2_V.catsEffect,
        "org.bouncycastle" % "bcpg-jdk15on" % V.bouncyCastle,
        "org.bouncycastle" % "bcprov-jdk15on" % V.bouncyCastle,
        "co.fs2" %% "fs2-core" % CE2_V.fs2,
        "co.fs2" %% "fs2-io" % CE2_V.fs2,
        "com.chuusai" %% "shapeless" % V.shapeless,
        "org.scala-lang.modules" %% "scala-collection-compat" % V.scalaCollectionCompat,
        "org.typelevel" %% "log4cats-core" % CE2_V.log4cats,
        "eu.timepit" %% "refined" % V.refined,
      )
    },
    unusedCompileDependenciesFilter -= moduleFilter("org.scala-lang.modules", "scala-collection-compat"),
  ))
  .customRow(V.Scalas, Seq(CatsEffect3Axis, VirtualAxis.jvm), _.settings(
    moduleName := s"${name.value}",
    description := "fs2 pipes for encrypting and decrypting streams with BouncyCastle PGP",
    Compile / unmanagedSourceDirectories += (Compile / scalaSource).value.getParentFile / s"scala-${CatsEffect3Axis.directorySuffix}",
    libraryDependencies ++= {
      Seq(
        "org.typelevel" %% "cats-core" % V.cats,
        "org.typelevel" %% "cats-effect-kernel" % CE3_V.catsEffect,
        "org.typelevel" %% "cats-effect" % CE3_V.catsEffect,
        "org.bouncycastle" % "bcpg-jdk15on" % V.bouncyCastle,
        "org.bouncycastle" % "bcprov-jdk15on" % V.bouncyCastle,
        "co.fs2" %% "fs2-core" % CE3_V.fs2,
        "co.fs2" %% "fs2-io" % CE3_V.fs2,
        "com.chuusai" %% "shapeless" % V.shapeless,
        "org.scala-lang.modules" %% "scala-collection-compat" % V.scalaCollectionCompat,
        "org.typelevel" %% "log4cats-core" % CE3_V.log4cats,
        "eu.timepit" %% "refined" % V.refined,
      )
    },
    unusedCompileDependenciesFilter -= moduleFilter("org.scala-lang.modules", "scala-collection-compat"),
  ))
  .settings(commonSettings: _*)

lazy val tests = (projectMatrix in file("tests"))
  .customRow(V.Scalas, Seq(CatsEffect2Axis, VirtualAxis.jvm), _.settings(
    description := "Tests broken out into a separate project to break a circular dependency",
    Test / unmanagedSourceDirectories += (Test / scalaSource).value.getParentFile / s"scala-${CatsEffect2Axis.directorySuffix}",
    libraryDependencies ++= {
      Seq(
        "org.scalameta" %% "munit" % V.munit % Test,
        "org.typelevel" %% "scalacheck-effect" % V.scalacheckEffect % Test,
        "org.typelevel" %% "scalacheck-effect-munit" % V.scalacheckEffect % Test,
        "org.typelevel" %% "munit-cats-effect-2" % V.munitCatsEffect % Test,
        "eu.timepit" %% "refined-scalacheck" % V.refined % Test,
        "io.chrisdavenport" %% "cats-scalacheck" % V.catsScalacheck % Test,
        "org.typelevel" %% "log4cats-noop" % CE3_V.log4cats % Test,
        "dev.holt" %% "java-time-literals" % "1.1.0" % Test,
        "com.eed3si9n.expecty" %% "expecty" % V.expecty % Test,
      )
    },
    publish / skip := true,
  ))
  .customRow(V.Scalas, Seq(CatsEffect3Axis, VirtualAxis.jvm), _.settings(
    description := "Tests broken out into a separate project to break a circular dependency",
    Test / unmanagedSourceDirectories += (Test / scalaSource).value.getParentFile / s"scala-${CatsEffect3Axis.directorySuffix}",
    libraryDependencies ++= {
      Seq(
        "org.scalameta" %% "munit" % V.munit % Test,
        "org.typelevel" %% "scalacheck-effect" % V.scalacheckEffect % Test,
        "org.typelevel" %% "scalacheck-effect-munit" % V.scalacheckEffect % Test,
        "org.typelevel" %% "munit-cats-effect-3" % V.munitCatsEffect % Test,
        "eu.timepit" %% "refined-scalacheck" % V.refined % Test,
        "io.chrisdavenport" %% "cats-scalacheck" % V.catsScalacheck % Test,
        "org.typelevel" %% "log4cats-noop" % CE3_V.log4cats % Test,
        "dev.holt" %% "java-time-literals" % "1.1.0" % Test,
        "com.eed3si9n.expecty" %% "expecty" % V.expecty % Test,
      )
    },
    publish / skip := true,
  ))
  .dependsOn(`fs2-pgp`, `pgp-testkit`)
  .settings(commonSettings: _*)

lazy val `pgp-testkit` = (projectMatrix in file("testkit"))
  .customRow(V.Scalas, Seq(CatsEffect2Axis, VirtualAxis.jvm), _.settings(
    moduleName := s"${name.value}-ce2",
    description := "Scalacheck Arbitraries for PGP resources",
    Compile / unmanagedSourceDirectories += (Compile / scalaSource).value.getParentFile / s"scala-${CatsEffect2Axis.directorySuffix}",
    libraryDependencies ++= {
      Seq(
        "org.bouncycastle" % "bcpg-jdk15on" % V.bouncyCastle,
        "org.bouncycastle" % "bcprov-jdk15on" % V.bouncyCastle % Runtime,
        "org.scalacheck" %% "scalacheck" % "1.15.4",
        "org.typelevel" %% "cats-core" % V.cats,
        "org.typelevel" %% "cats-effect" % CE2_V.catsEffect,
        "eu.timepit" %% "refined" % V.refined,
        "com.chuusai" %% "shapeless" % V.shapeless,
        "eu.timepit" %% "refined-scalacheck" % V.refined,
        "io.chrisdavenport" %% "cats-scalacheck" % V.catsScalacheck,
        "co.fs2" %% "fs2-core" % CE2_V.fs2,
        "org.scala-lang.modules" %% "scala-collection-compat" % V.scalaCollectionCompat
      )
    },
    unusedCompileDependenciesFilter -= moduleFilter("org.scala-lang.modules", "scala-collection-compat"),
  ))
  .customRow(V.Scalas, Seq(CatsEffect3Axis, VirtualAxis.jvm), _.settings(
    moduleName := s"${name.value}",
    description := "Scalacheck Arbitraries for PGP resources",
    Compile / unmanagedSourceDirectories += (Compile / scalaSource).value.getParentFile / s"scala-${CatsEffect3Axis.directorySuffix}",
    libraryDependencies ++= {
      Seq(
        "org.bouncycastle" % "bcpg-jdk15on" % V.bouncyCastle,
        "org.bouncycastle" % "bcprov-jdk15on" % V.bouncyCastle % Runtime,
        "org.scalacheck" %% "scalacheck" % "1.15.4",
        "org.typelevel" %% "cats-core" % V.cats,
        "org.typelevel" %% "cats-effect-kernel" % CE3_V.catsEffect,
        "org.typelevel" %% "cats-effect" % CE3_V.catsEffect,
        "eu.timepit" %% "refined" % V.refined,
        "com.chuusai" %% "shapeless" % V.shapeless,
        "eu.timepit" %% "refined-scalacheck" % V.refined,
        "io.chrisdavenport" %% "cats-scalacheck" % V.catsScalacheck,
        "co.fs2" %% "fs2-core" % CE3_V.fs2,
        "org.scala-lang.modules" %% "scala-collection-compat" % V.scalaCollectionCompat
      )
    },
    unusedCompileDependenciesFilter -= moduleFilter("org.scala-lang.modules", "scala-collection-compat"),
  ))
  .dependsOn(`fs2-pgp`)
  .settings(commonSettings: _*)

lazy val `fs2-pgp-root` = (project in file("."))
  .settings(publish / skip := true)
  .aggregate(
    Seq(
      `fs2-pgp`,
      tests,
      `pgp-testkit`
    ).flatMap(_.projectRefs): _*
  )
