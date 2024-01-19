package fix

import cats.effect._
import cats.syntax.all._
import cats.effect.unsafe.implicits.global
import org.typelevel.log4cats._
import fs2._
import fs2.text._
import com.dwolla.security.crypto._
import eu.timepit.refined.types.numeric.PosInt
import com.dwolla.security.crypto.ChunkSize

object Fs2Pgp {
  implicit val lf: LoggerFactory[IO] = org.typelevel.log4cats.noop.NoOpFactory.impl[IO]
  val key =
    """-----BEGIN PGP PUBLIC KEY BLOCK-----
      |Version: GnuPG v1
      |
      |mQENBFVoyeYBCACy0S/9y/c/CpoYLL6aD3TMCV1Pe/0jcWN0ykULf9l4znYODZLr
      |f10BGAJETj9ghrJCNXMib2ogz0wo43KVAp9o3mkg01vVyqs1rzM5jw+yCZmyGPFf
      |GsE2lxZFMX+rS0dyq2w0FQN2IjYsELwIFeQ02GXTLyTlhY+u5wwCXo4e7AEXaEo7
      |jl8129NA46gf6l+6lUMyFpKnunO7L4W5rCCrIizP4Fmll1adYfClSX6cztIfz4vg
      |Fs2HuViPin5y8THodkg9cIkCfyNHivEbfBbx0xfx67BCwxFcYgF/84H8TASRhjRl
      |4s1fZDA7rETWDJIcC+neNV/qtF0kY1ECSd3nABEBAAG0IFRlc3QgVXNlciA8ZnJl
      |ZCt0ZXN0QGR3b2xsYS5jb20+iQE4BBMBAgAiBQJVaMnmAhsDBgsJCAcDAgYVCAIJ
      |CgsEFgIDAQIeAQIXgAAKCRA2OYfNakCqV1bYB/9QNR5DN5J27Z4DIGoOto/PuVvs
      |bQHZj8NLcvIZL1cUyKOg+oRICq2z4BXHAMqyouhs/GLiR5P74I9cJTSIudAvBhwi
      |du9AcMQy+Qg3K1rUQGlNU+iamD8DFNUhLoK+Oicij0Mw4TSWBsoR3+Pg/jZ5SDUc
      |dUsGGaBJthYoiJR8vZ6Uf9oCn+mpVhrso0zemBDud4AHKaVa+8o7VUWGa6jeyRHX
      |RKVbHn7GGYiHZkl+qfpthcxyPHOIkZo+t8GVTItLpvVuU+X36N70+rIzXj5t8NDZ
      |KfD3M4p6BSq6Cu6DtJOZ1F28hwaWiRoCdbPrJfW33fo1RxLB6+nLf/ttYGmhuQEN
      |BFVoyeYBCADiZfKA98YQip/kvj5rBS2ilQDycBX7Ls2IftuwzO6Q9QSF2lDiz708
      |zyvg0czQPZYaZkFgziZEmjbvOc7hDG+icVWRLCjCcZk4i2TXy7bGcTZmBQ31iVMJ
      |ia7GxsJhu4ngrP15pZakAYcCwEk3QH17TdhOwvV8ixHmv9USCMJyiNnuhVAP2tY/
      |Ef0EoCV6qAMoP3dNPT30sFI8+55Ce9yAtWQItT5q4vYOmC9Q34XtSxvpLsLzVByd
      |rdvgXe0acjvMiTGcYBdjitawFYeLuz2s5mQAi4X1vcJqxBSBjG7X+1PiDqFFIid3
      |+6rIQtR3ho+Xqz/ucGglKxtn6m49wMHJABEBAAGJAR8EGAECAAkFAlVoyeYCGwwA
      |CgkQNjmHzWpAqldxFgf/SZIT1AiBAOLkqdWEObg0cU7n1YOXbj56sUeUCFxdbnl9
      |V2paf2SaMB6EEGLTk9PN0GG3hPyDkl4O6w3mn2J46uP8ecVaNvTSxoq2OmkMmD1H
      |/OSnF8a/jB6R1ODiAwekVuUMtAS7JiaAAcKcenG1f0XRKwQs52uavGXPgUuJbVtK
      |bB0SyLBhvGG8YIWTXRMHoJRt/Ls4JEuYaoBYqfV2eDn4WhW1LVuXP13gXixy0RiV
      |8rHs9aH8BAU7Dy0BBnaS3R9m8vtfdFxMI3/+1iGt0+xh/B4w++9oFE2DgyoZXUF8
      |mbjKYhiRPKNoj6Rn/mHUGcnuPlKvKP+1X5bObpDbQQ==
      |=TJUS
      |-----END PGP PUBLIC KEY BLOCK-----""".stripMargin
  val wrappedKey = PGPKeyAlg[IO].readPublicKey(key).unsafeRunSync()
  val pos = PosInt(100)
  val chunkSize = ChunkSize(PosInt(100))
  val chunkSize2 = ChunkSize(pos)
  (for {
    crypto <- Stream.resource(CryptoAlg.resource[IO])
    output <- Stream.emit("hello world")
      .through(utf8.encode)
      .through(crypto.encrypt(EncryptionConfig().withFileName("BigMoney.txt".some), wrappedKey))
      .through(crypto.armor)
      .through(utf8.decode)
  } yield output).compile.string.unsafeRunSync()
}
