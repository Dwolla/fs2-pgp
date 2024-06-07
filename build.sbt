lazy val V = new {
  val SCALA_2_12 = "2.12.19"
  val SCALA_2_13 = "2.13.14"
  val Scalas = Seq(SCALA_2_13, SCALA_2_12)
}

ThisBuild / scalaVersion := V.Scalas.head
ThisBuild / crossScalaVersions := V.Scalas
ThisBuild / organization := "com.dwolla"
ThisBuild / homepage := Option(url("https://github.com/Dwolla/fs2-pgp"))
ThisBuild / licenses += ("MIT", url("https://opensource.org/licenses/MIT"))
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
ThisBuild / tlJdkRelease := Option(8)
ThisBuild / githubWorkflowJavaVersions := Seq(JavaSpec.temurin("17"))
ThisBuild / githubWorkflowScalaVersions := Seq("2.13", "2.12")
ThisBuild / tlCiReleaseBranches := Seq("main", "series/0.5")
ThisBuild / tlBaseVersion := "0.4"
ThisBuild / tlSonatypeUseLegacyHost := true
ThisBuild / mergifyStewardConfig ~= { _.map {
  _.withAuthor("dwolla-oss-scala-steward[bot]")
    .withMergeMinors(true)
}}

lazy val `fs2-pgp-root` = (project in file("."))
  .settings(publishArtifact := false)
  .enablePlugins(BouncyCastlePlugin)
  .aggregate(BouncyCastlePlugin.aggregate)
