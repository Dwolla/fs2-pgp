import com.typesafe.tools.mima.plugin.MimaKeys.*
import org.typelevel.sbt.NoPublishPlugin
import sbt.*
import sbt.Keys.*

case class ArtifactVersions(latest: ModuleID, compatibleVersions: Seq[String]) {
  val history: Set[ModuleID] = compatibleVersions.map(latest.withRevision).toSet
}

object BouncyCastlePlugin extends AutoPlugin {

  override def trigger = allRequirements

  object autoImport {
    lazy val bouncyCastleArtifacts: Seq[ModuleID] = modules.map(_.latest)
  }

  /* When a new BouncyCastle version is released, add it to the beginning of
   * these lists. If `rootBouncyCastle/mimaReportBinaryIssues` shows no issues,
   * we have a chain of binary-compatible releases and the new list can
   * be left as it is (i.e. with the new version at the head). If issues
   * are found, then remove everything other than the new version,
   * because the newest release is not binary compatible with the older
   * versions it was checked against.
   */
  lazy val modules = Seq(
    ArtifactVersions("org.bouncycastle" % "bcpg-jdk18on" % "1.71", Seq()),
    ArtifactVersions("org.bouncycastle" % "bcprov-jdk18on" % "1.71", Seq()),
  )

  private lazy val subprojects = modules.map { module =>
    Project(module.latest.name, file(s".${module.latest.name}"))
      .enablePlugins(NoPublishPlugin)
      .settings(
        libraryDependencies += module.latest,
        mimaCurrentClassfiles := {
          (Compile / dependencyClasspath).value.seq.map(_.data).find(_.getName.startsWith(module.latest.name)).get
        },
        mimaPreviousArtifacts := module.history,
      )
  }

  lazy val rootBouncyCastle =
    project
      .in(file(s".rootBouncyCastle"))
      .enablePlugins(NoPublishPlugin)
      .aggregate(subprojects.map(_.project): _*)

  override lazy val extraProjects: Seq[Project] = rootBouncyCastle +: subprojects
}
