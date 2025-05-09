import scalafix.sbt.ScalafixTestkitPlugin.autoImport.*
import scalafix.sbt.ScalafixTestkitPlugin
import scalafix.sbt.ScalafixPlugin.autoImport.*
import com.typesafe.tools.mima.plugin.MimaKeys.*
import com.typesafe.tools.mima.plugin.MimaPlugin
import explicitdeps.ExplicitDepsPlugin.autoImport.*
import org.typelevel.sbt.TypelevelKernelPlugin.autoImport.tlIsScala3
import org.typelevel.sbt.TypelevelMimaPlugin.autoImport.*
import org.typelevel.sbt.{NoPublishPlugin, TypelevelSettingsPlugin}
import org.typelevel.sbt.TypelevelSettingsPlugin.autoImport.*
import org.typelevel.sbt.TypelevelSonatypeCiReleasePlugin.autoImport.*
import org.typelevel.sbt.TypelevelVersioningPlugin.autoImport.*
import org.typelevel.sbt.gha.GenerativePlugin.autoImport.*
import org.typelevel.sbt.gha.GitHubActionsPlugin.autoImport.*
import org.typelevel.sbt.mergify.MergifyPlugin
import org.typelevel.sbt.mergify.MergifyPlugin.autoImport.*
import sbt.*
import sbt.Keys.*
import sbt.internal.ProjectMatrix
import sbt.librarymanagement.DependencyBuilders.OrganizationArtifactName
import sbtprojectmatrix.ProjectMatrixPlugin
import sbtprojectmatrix.ProjectMatrixPlugin.autoImport.*
import xerial.sbt.Sonatype.autoImport.*

object BouncyCastlePlugin extends AutoPlugin {
  override def trigger = noTrigger

  override def requires: Plugins =
    ProjectMatrixPlugin && MimaPlugin && MergifyPlugin && TypelevelSettingsPlugin && WarnNonUnitStatements

  object autoImport {
    lazy val allProjects: Seq[Project] =
      List(
        core,
        testkit,
        tests,
        `scalafix-rules`,
        `scalafix-input`,
        `scalafix-output`,
        `scalafix-tests`,
      )
        .flatMap(pm => if (pm.id.contains("scalafix") || pm.id.contains("tests")) List(pm) else List(pm, latestVersionAlias(pm)))
        .flatMap(_.componentProjects)
  }

  private val currentBouncyCastleVersion = BouncyCastleVersion("1.80", introducedIntoFs2Pgp = "0.5.0")

  /** When a new version is released, move what was previously the current version into the list of old versions.
   *
   * This plugin will automatically release a new suffixed artifact that can be used by users with bincompat issues.
   *
   * Remember to regenerate the GitHub Actions workflow by running the `githubWorkflowGenerate` sbt task.
   */
  private val oldVersions: List[BouncyCastleVersion] = List(
    BouncyCastleVersion("1.78.1", introducedIntoFs2Pgp = "0.5.0"),
    BouncyCastleVersion("1.77", introducedIntoFs2Pgp = "0.5.0"),
    BouncyCastleVersion("1.76", introducedIntoFs2Pgp = "0.5.0"),
    BouncyCastleVersion("1.75", introducedIntoFs2Pgp = "0.5.0"),
    BouncyCastleVersion("1.74", introducedIntoFs2Pgp = "0.5.0"),
    BouncyCastleVersion("1.73", introducedIntoFs2Pgp = "0.5.0"),
    BouncyCastleVersion("1.72.2", introducedIntoFs2Pgp = "0.5.0", bcutilsMapping = "1.72"),
    BouncyCastleVersion("1.72.1", introducedIntoFs2Pgp = "0.5.0", bcutilsMapping = "1.72"),
    BouncyCastleVersion("1.72", introducedIntoFs2Pgp = "0.5.0"),
    BouncyCastleVersion("1.71.1", introducedIntoFs2Pgp = "0.5.0"),
    BouncyCastleVersion("1.71", introducedIntoFs2Pgp = "0.5.0"),
  )

  private val supportedVersions = (currentBouncyCastleVersion :: oldVersions).sortBy(_.version).reverse

  private val SCALA_2_13: String = "2.13.16"
  private val SCALA_2_12 = "2.12.20"
  private val Scala2Versions: Seq[String] = Seq(SCALA_2_13, SCALA_2_12)
  private val SCALA_3 = "3.3.6"
  private val AllScalaVersions = Seq(SCALA_3, SCALA_2_13, SCALA_2_12)

  private def projectMatrixForSupportedBouncyCastleVersions(id: String,
                                                            path: String)
                                                           (s: BouncyCastleVersion => List[Setting[?]]): ProjectMatrix =
    supportedVersions.foldLeft(ProjectMatrix(id, file(path)))(addBouncyCastleCustomRow(s))

  private def addBouncyCastleCustomRow(s: BouncyCastleVersion => List[Setting[?]])
                                      (p: ProjectMatrix, v: BouncyCastleVersion): ProjectMatrix =
    p.customRow(
      scalaVersions = if (p.id.contains("scalafix")) Scala2Versions else AllScalaVersions,
      axisValues = List(v, VirtualAxis.jvm),
      _.settings(
        moduleName := name.value + v.moduleNameSuffix,
        s(v),
        libraryDependencies ++= {
          if (tlIsScala3.value) Seq.empty
          else Seq(
            compilerPlugin("org.typelevel" %% "kind-projector" % "0.13.3" cross CrossVersion.full),
            compilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1"),
          )
        },
        Compile / scalacOptions ++= {
          CrossVersion.partialVersion(scalaVersion.value) match {
            case Some((2, n)) if n >= 13 => "-Ymacro-annotations" :: Nil
            case _ => Nil
          }
        },
        libraryDependencies ++= {
          CrossVersion.partialVersion(scalaVersion.value) match {
            case Some((2, n)) if n >= 13 => Nil
            case Some((2, n)) => compilerPlugin("org.scalamacros" % "paradise" % "2.1.1" cross CrossVersion.full) :: Nil
            case _ => Nil
          }
        },
      )
    )

  private def latestVersionAlias(p: ProjectMatrix): ProjectMatrix =
    ProjectMatrix(s"${p.id}-latest", file(s".${p.id}-latest"))
      .customRow(
        scalaVersions = if (p.id.contains("scalafix")) Scala2Versions else AllScalaVersions,
        axisValues = List(currentBouncyCastleVersion, VirtualAxis.jvm),
        _.settings(
          moduleName := p.id,
          tlVersionIntroduced := Map("2.12" -> "0.4.7", "2.13" -> "0.4.7"),
        )
      )
      .dependsOn(p)

  private def deprecationWarningsAddedIn1_80(v: Version): Seq[String] = Seq(
    "-Wconf:msg=method getKeyID in class PGPPublicKeyEncryptedData is deprecated:s",
    "-Wconf:msg=constructor JcaPGPKeyPair in class JcaPGPKeyPair is deprecated:s"
  ).filter(_ => v == Version("1.80"))

  private lazy val core =
    projectMatrixForSupportedBouncyCastleVersions("fs2-pgp", "core") { v =>
      List(
        description := "fs2 pipes for encrypting and decrypting streams with BouncyCastle PGP",
        sourceDirectory := (ThisBuild / baseDirectory).value / "core" / "src",
        libraryDependencies ++= {
          Seq(
            "org.typelevel" %% "cats-core" % "2.13.0",
            "org.typelevel" %% "cats-effect" % "3.6.1",
            "co.fs2" %% "fs2-core" % "3.12.0",
            "co.fs2" %% "fs2-io" % "3.12.0",
            "io.monix" %% "newtypes-core" % "0.2.3",
            "org.scala-lang.modules" %% "scala-collection-compat" % "2.13.0",
            "org.typelevel" %% "log4cats-core" % "2.7.0",
            "eu.timepit" %% "refined" % "0.11.3",
            "org.bouncycastle" % "bcpg-jdk18on" % v.version,
            "org.bouncycastle" % "bcutil-jdk18on" % v.bcutilsMapping.getOrElse(v.version),
          )
        },
        unusedCompileDependenciesFilter -= moduleFilter("org.scala-lang.modules", "scala-collection-compat"),
        mimaBinaryIssueFilters ++= {
          import com.typesafe.tools.mima.core.*
          Seq(
            // the CanCreateDecryptorFactory filters ignore a class and companion object that should have been package private
            // and have been replaced with an alternative implementation that is package private
            ProblemFilters.exclude[MissingClassProblem]("com.dwolla.security.crypto.CanCreateDecryptorFactory"),
            ProblemFilters.exclude[MissingClassProblem]("com.dwolla.security.crypto.CanCreateDecryptorFactory$"),
          )
        },
        Compile / scalacOptions ++= deprecationWarningsAddedIn1_80(v.version),
      )
    }

  private lazy val testkit =
    projectMatrixForSupportedBouncyCastleVersions("pgp-testkit", "testkit") { v =>
      List(
        description := "Scalacheck Arbitraries for BouncyCastle PGP",
        sourceDirectory := (ThisBuild / baseDirectory).value / "testkit" / "src",
        libraryDependencies ++= {
          Seq(
            "org.scalacheck" %% "scalacheck" % "1.18.1",
            "eu.timepit" %% "refined-scalacheck" % "0.11.3",
            "io.chrisdavenport" %% "cats-scalacheck" % "0.3.2",
          )
        },
        unusedCompileDependenciesFilter -= moduleFilter("org.scala-lang.modules", "scala-collection-compat"),
        Compile / scalacOptions ++= deprecationWarningsAddedIn1_80(v.version),
      )
    }
      .dependsOn(core)

  val tests =
    projectMatrixForSupportedBouncyCastleVersions("tests", "tests") { v =>
      List(
        description := "Tests broken out into a separate project to break a circular dependency",
        sourceDirectory := (ThisBuild / baseDirectory).value / "tests" / "src",
        libraryDependencies ++= {
          Seq(
            "org.typelevel" %% "log4cats-noop" % "2.7.0" % Test,
            "org.typelevel" %% "log4cats-slf4j" % "2.7.0" % Test,
            "ch.qos.logback" % "logback-classic" % "1.5.18" % Test,
            "org.scalameta" %% "munit" % "1.1.1" % Test,
            "org.typelevel" %% "scalacheck-effect" % "2.0.0-M2" % Test,
            "org.typelevel" %% "scalacheck-effect-munit" % "2.0.0-M2" % Test,
            "org.typelevel" %% "munit-cats-effect" % "2.1.0" % Test,
            "dev.holt" %% "java-time-literals" % "1.1.1" % Test,
            "com.eed3si9n.expecty" %% "expecty" % "0.17.0" % Test,
          )
        },
        unusedCompileDependenciesFilter -= moduleFilter("org.scala-lang.modules", "scala-collection-compat"),
      )
    }
      .enablePlugins(NoPublishPlugin)
      .dependsOn(core, testkit)

  lazy val `scalafix-rules` =
    projectMatrixForSupportedBouncyCastleVersions("fs2-pgp-scalafix", "scalafix/rules") { v =>
      List(
        libraryDependencies ++= Seq(
          "ch.epfl.scala" %% "scalafix-core" % _root_.scalafix.sbt.BuildInfo.scalafixVersion,
        )
      )
    }

  lazy val `scalafix-input` =
    projectMatrixForSupportedBouncyCastleVersions("scalafix-input", "scalafix/input") { v =>
      List(
        libraryDependencies ++= Seq(
          "com.dwolla" %% "fs2-pgp" % "0.4.6",
          "org.typelevel" %% "log4cats-noop" % "2.7.0",
        ),
        scalacOptions ~= {
          _.filterNot(_ == "-Xfatal-warnings")
        },
        semanticdbEnabled := true,
        semanticdbVersion := scalafixSemanticdb.revision,
      )
    }
      .enablePlugins(NoPublishPlugin)

  lazy val `scalafix-output` =
    projectMatrixForSupportedBouncyCastleVersions("scalafix-output", "scalafix/output") { v =>
      List(
        scalacOptions ~= {
          _.filterNot(_ == "-Xfatal-warnings")
        },
      )
    }
      .dependsOn(core)
      .enablePlugins(NoPublishPlugin)

  lazy val `scalafix-tests` =
    projectMatrixForSupportedBouncyCastleVersions("scalafix-tests", "scalafix/tests") { v =>
      List(
        libraryDependencies += "ch.epfl.scala" % "scalafix-testkit" % _root_.scalafix.sbt.BuildInfo.scalafixVersion % Test cross CrossVersion.full,
        scalafixTestkitOutputSourceDirectories := BouncyCastleVersion.resolve(`scalafix-output`, Compile / unmanagedSourceDirectories).value,
        scalafixTestkitInputSourceDirectories := BouncyCastleVersion.resolve(`scalafix-input`, Compile / unmanagedSourceDirectories).value,
        scalafixTestkitInputClasspath := BouncyCastleVersion.resolve(`scalafix-input`, Compile / fullClasspath).value,
        scalafixTestkitInputScalacOptions := BouncyCastleVersion.resolve(`scalafix-input`, Compile / scalacOptions).value,
        scalafixTestkitInputScalaVersion := BouncyCastleVersion.resolve(`scalafix-input`, Compile / scalaVersion).value,
      )
    }
      .dependsOn(`scalafix-rules`)
      .enablePlugins(ScalafixTestkitPlugin, NoPublishPlugin)

  override def buildSettings: Seq[Def.Setting[?]] = Seq(
    mergifyLabelPaths :=
      List(
        "core",
        "testkit",
        "tests",
      )
        .map(x => x -> file(x))
        .toMap,

    githubWorkflowScalaVersions := Seq("per-project-matrix"),
    githubWorkflowBuildSbtStepPreamble := Nil,
    githubWorkflowBuild := Seq(WorkflowStep.Sbt(List("test", "mimaReportBinaryIssues", "doc"))),
    githubWorkflowJavaVersions := Seq(JavaSpec.temurin("17")),

    organization := "com.dwolla",
    homepage := Some(url("https://github.com/Dwolla/fs2-pgp")),
    licenses := Seq(License.MIT),
    developers := List(
      Developer(
        "bpholt",
        "Brian Holt",
        "bholt+fs2-pgp@dwolla.com",
        url("https://dwolla.com")
      ),
    ),
    startYear := Option(2020),
    sonatypeCredentialHost := xerial.sbt.Sonatype.sonatypeLegacy,
    tlBaseVersion := "0.4",
    tlCiReleaseBranches := Seq("main", "series/0.5"),
    mergifyRequiredJobs ++= Seq("validate-steward"),
    mergifyStewardConfig ~= { _.map {
      _.withAuthor("dwolla-oss-scala-steward[bot]")
        .withMergeMinors(true)
    }},
    mergifyLabelPaths += "scalafix" -> file("scalafix"),
    tlJdkRelease := Option(8),
    tlFatalWarnings := githubIsWorkflowBuild.value,
  )

  override def extraProjects: Seq[Project] = autoImport.allProjects

  private implicit class OrganizationArtifactNameOps(val oan: OrganizationArtifactName) extends AnyVal {
    def %(vav: Version): ModuleID =
      oan % vav.toString
  }

}

object WarnNonUnitStatements extends AutoPlugin {
  override def trigger = allRequirements

  override def projectSettings: Seq[Def.Setting[?]] = Seq(
    scalacOptions ++= {
      if (scalaVersion.value.startsWith("2.13"))
        Seq("-Wnonunit-statement")
      else if (scalaVersion.value.startsWith("2.12"))
        Seq()
      else
        Nil
    },
  )
}
