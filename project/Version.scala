/*
 * Copyright 2022 Typelevel
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/* Adapted from https://github.com/typelevel/sbt-typelevel/blob/9106a7e3114d94ea1d9b9af3511e2f5e5806be17/kernel/src/main/scala/org/typelevel/sbt/kernel/V.scala,
 * but with different sorting rules for when patch versions are missing and prerelease support removed,
 * to correspond to Bouncy Castle's version scheme.
 */

import scala.util.Try

final case class Version(major: Int,
                         minor: Int,
                         patch: Option[Int]
                        ) extends Ordered[Version] {

  override def toString: String =
    s"$major.$minor${patch.fold("")(p => s".$p")}"

  def isSameSeries(that: Version): Boolean =
    this.major == that.major && this.minor == that.minor

  def mustBeBinCompatWith(that: Version): Boolean =
    this >= that && this.major == that.major && (major > 0 || this.minor == that.minor)

  private final val ThisGreater = 1
  private final val ThatGreater = -1

  def compare(that: Version): Int = {
    val x = this.major.compare(that.major)
    if (x != 0) return x
    val y = this.minor.compare(that.minor)
    if (y != 0) return y
    (this.patch, that.patch) match {
      case (None, None) => 0
      case (None, Some(patch)) => ThatGreater // a version with a patch comes after a version without a patch
      case (Some(patch), None) => ThisGreater // a version with a patch comes after a version without a patch
      case (Some(thisPatch), Some(thatPatch)) =>
        thisPatch.compare(thatPatch)
    }
  }

}

object Version {
  private val version = """^(0|[1-9]\d*)\.(0|[1-9]\d*)(?:\.(0|[1-9]\d*))?$""".r

  def apply(v: String): Version = Version.unapply(v).get

  def unapply(v: String): Option[Version] = v match {
    case version(major, minor, patch) =>
      Try(Version(major.toInt, minor.toInt, Option(patch).map(_.toInt))).toOption
    case _ => None
  }

  object Tag {
    def unapply(v: String): Option[Version] =
      if (v.startsWith("v")) Version.unapply(v.substring(1)) else None
  }
}
