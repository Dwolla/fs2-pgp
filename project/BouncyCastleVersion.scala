import sbt.*
import sbt.Keys.*
import sbt.internal.ProjectMatrix

/**
 * Represents a specific version of Bouncy Castle with additional metadata.
 *
 * @param version              The version of Bouncy Castle.
 * @param introducedIntoFs2Pgp The version in which this Bouncy Castle version was first introduced into fs2-pgp.
 * @param bcutilsMapping       An optional mapping for `bcutil` versions when they differ from the corresponding `bcpg` version.
 *
 *                             The `bcutilsMapping` is necessary because the `bcutil` library from Bouncy Castle is not always versioned
 *                             exactly in sync with the `bcpg` library. When they are not synchronized, this mapping provides the
 *                             appropriate `bcutil` version to use (with the `bcpg` version as the key).
 *
 *                             If no mapping is provided, the `bcpg` version is assumed to match the `bcutil` version.
 */
case class BouncyCastleVersion(version: Version,
                               introducedIntoFs2Pgp: Version,
                               bcutilsMapping: Option[Version]) extends VirtualAxis.WeakAxis {
  override def directorySuffix: String =
    s"-bcpg-$version"

  override def idSuffix: String =
    directorySuffix.replace('.', '_')

  def moduleNameSuffix: String =
    s"-bcpg$version"
}

object BouncyCastleVersion {
  def apply(version: Version, introducedIntoFs2Pgp: String): BouncyCastleVersion =
    BouncyCastleVersion(version, Version(introducedIntoFs2Pgp), None)

  def apply(version: String, introducedIntoFs2Pgp: String): BouncyCastleVersion =
    BouncyCastleVersion(Version(version), Version(introducedIntoFs2Pgp), None)

  def apply(version: String, introducedIntoFs2Pgp: String, bcutilsMapping: String): BouncyCastleVersion =
    BouncyCastleVersion(Version(version), Version(introducedIntoFs2Pgp), Option(Version(bcutilsMapping)))

  def resolve[T](matrix: ProjectMatrix,
                 key: TaskKey[T],
                ): Def.Initialize[Task[T]] =
    Def.taskDyn {
      val project = matrix.finder().apply(scalaVersion.value)
      Def.task((project / key).value)
    }

  def resolve[T](matrix: ProjectMatrix,
                 key: SettingKey[T]
                ): Def.Initialize[T] =
    Def.settingDyn {
      val project = matrix.finder().apply(scalaVersion.value)
      Def.setting((project / key).value)
    }
}
