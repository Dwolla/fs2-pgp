lazy val V = new {
  val SCALA_2_12 = "2.12.18"
  val SCALA_2_13 = "2.13.11"
  val SCALA_3    = "3.3.0"
  val Scalas = Seq(SCALA_2_13, SCALA_2_12, SCALA_3)
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
  ),
  Developer(
    "cjsmith-0141",
    "CJ Smith",
    "cjs.connor.smith@gmail.com",
    url("https://tazato.net")
  )
)
ThisBuild / githubWorkflowBuild := Seq(WorkflowStep.Sbt(List("test", "mimaReportBinaryIssues", "doc")))
ThisBuild / tlJdkRelease := Option(8)
ThisBuild / githubWorkflowJavaVersions := Seq(JavaSpec.temurin("17"))
ThisBuild / githubWorkflowScalaVersions := Seq("3.3.0", "2.13", "2.12")
ThisBuild / tlCiReleaseBranches := Seq("main")
ThisBuild / tlBaseVersion := "0.5"
ThisBuild / tlSonatypeUseLegacyHost := true
ThisBuild / mergifyStewardConfig ~= {
  _.map(_.copy(mergeMinors = true, author = "dwolla-oss-scala-steward[bot]"))
}
ThisBuild / tlVersionIntroduced := Map("3" -> "0.5.0")

lazy val `fs2-pgp-root` = (project in file("."))
  .settings(publishArtifact := false)
  .enablePlugins(BouncyCastlePlugin)
  .aggregate(BouncyCastlePlugin.aggregate)
