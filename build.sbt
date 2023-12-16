lazy val V = new {
  val SCALA_2_12 = "2.12.18"
  val SCALA_2_13 = "2.13.12"
  val SCALA_3 = "3.3.1"
  val Scalas = Seq(SCALA_3, SCALA_2_13, SCALA_2_12)
}

ThisBuild / scalaVersion := V.Scalas.head
ThisBuild / crossScalaVersions := V.Scalas
ThisBuild / organization := "net.tazato"
ThisBuild / homepage := Option(url("https://github.com/CJSmith-0141/fs2-pgp"))
ThisBuild / licenses += ("MIT", url("http://opensource.org/licenses/MIT"))
ThisBuild / startYear := Option(2020)
ThisBuild / developers := List(
  Developer(
    "bpholt",
    "Brian Holt",
    "bholt@dwolla.com",
    url("https://dwolla.com")
  ),
  Developer(
    "CJSmith-0141",
    "CJ Smith",
    "connor.smith1@octoenergy.com",
    url("https://tazato.net")
  )
)
ThisBuild / githubWorkflowBuild := Seq(WorkflowStep.Sbt(List("test", "mimaReportBinaryIssues", "doc")))
ThisBuild / tlJdkRelease := Option(8)
ThisBuild / githubWorkflowJavaVersions := Seq(JavaSpec.temurin("17"))
ThisBuild / githubWorkflowScalaVersions := Seq("3", "2.13", "2.12")
ThisBuild / tlCiReleaseBranches := Seq("main")
ThisBuild / tlBaseVersion := "0.5"
ThisBuild / tlSonatypeUseLegacyHost := false
ThisBuild / mergifyStewardConfig ~= {
  _.map(_.copy(mergeMinors = true, author = "dwolla-oss-scala-steward[bot]"))
}

lazy val `fs2-pgp-root` = (project in file("."))
  .settings(publishArtifact := false)
  .enablePlugins(BouncyCastlePlugin)
  .aggregate(BouncyCastlePlugin.extraProjects.map(_.project) *)
