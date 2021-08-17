import sbt.VirtualAxis

case class CatsEffectAxis(idSuffix: String, directorySuffix: String) extends VirtualAxis.WeakAxis

object ConfigAxes {
  val CatsEffect2Axis = CatsEffectAxis("_ce2", "ce2")
  val CatsEffect3Axis = CatsEffectAxis("_ce3", "ce3")
}
