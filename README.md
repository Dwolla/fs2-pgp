# fs2-pgp

A library for encrypting and decrypting fs2 `Stream[F, Byte]` using PGP.

<table>
<thead>
<tr>
<th>Artifact</th>
<th>Description</th>
<th align="center">Cats Effect Version</th>
<th align="center">fs2 Version</th>
<th align="center">Scala 2.12</th>
<th align="center">Scala 2.13</th>
</tr>
</thead>
<tbody>
<tr>
<td><code>"fs2-pgp"</code></td>
<td>Load PGP keys and use them to encrypt, decrypt, and armor byte streams.</td>
<td align="center">Cats Effect 3</td>
<td align="center">fs2 3.x</td>
<td align="center"><g-emoji class="g-emoji" alias="white_check_mark" fallback-src="https://github.githubassets.com/images/icons/emoji/unicode/2705.png">✅</g-emoji></td>
<td align="center"><g-emoji class="g-emoji" alias="white_check_mark" fallback-src="https://github.githubassets.com/images/icons/emoji/unicode/2705.png">✅</g-emoji></td>
</tr>
<tr>
<td><code>"pgp-testkit"</code></td>
<td>ScalaCheck Generators and Arbitrary instances for PGP classes</td>
<td align="center">Cats Effect 3</td>
<td align="center">fs2 3.x</td>
<td align="center"><g-emoji class="g-emoji" alias="white_check_mark" fallback-src="https://github.githubassets.com/images/icons/emoji/unicode/2705.png">✅</g-emoji></td>
<td align="center"><g-emoji class="g-emoji" alias="white_check_mark" fallback-src="https://github.githubassets.com/images/icons/emoji/unicode/2705.png">✅</g-emoji></td>
</tr>
</tbody>
</table>

## Bouncy Castle Versions

Bouncy Castle often releases versions that report as binary-incompatible with 
previous versions when examined with a tool such as [MiMa](https://github.com/lightbend/mima).
The "current" version of the artifacts published by this project intend to track
the latest Bouncy Castle version, with previously supported versions published 
as supplemental artifacts with the supported Bouncy Castle version appended to
the artifact name.

For example, the latest Bouncy Castle version is `1.75`, so the latest version of
`com.dwolla::fs2-pgp` depends on `org.bouncycastle:bcpg-jdk18on:1.75`. In addition,
we publish artifacts named like `com.dwolla::fs2-pgp-bcpg1.73` for each of the
previously supported Bouncy Castle artifacts.

## Keys

```scala
import com.dwolla.security.crypto._
import cats.effect._

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

PGPKeyAlg[IO].readPublicKey(key).unsafeRunSync()
val res0: org.bouncycastle.openpgp.PGPPublicKey = org.bouncycastle.openpgp.PGPPublicKey@1003b416
```

## Encryption

Read a `PGPPublicKey` using `PGPKeyAlg[F]`, then pipe your message bytes through `CryptoAlg[F].encrypt`. 

```scala
import cats.effect._
import cats.syntax.all._
import org.typelevel.log4cats.Logger
import fs2._
import fs2.text._
import com.dwolla.security.crypto._
import org.bouncycastle.openpgp._

val key: PGPPublicKey = ??? // from above

(for {
  crypto <- Stream.resource(CryptoAlg[IO])
  output <- Stream.emit("hello world")
                  .through(utf8.encode)
                  .through(crypto.encrypt(key))
                  .through(crypto.armor())
                  .through(utf8.decode)
} yield output).compile.string.unsafeRunSync()
val res1: String =
"-----BEGIN PGP MESSAGE-----
Version: BCPG v1.66

hQEMAzY5h81qQKpXAQf/YTq6GtTkWlbg2DRu7r133FZaAudA149WB2BV/vsgyHkN
…
"
```

## Decryption

Read a `PGPPrivateKey` using `PGPKeyAlg[F]`, then pipe the encrypted message bytes through `CryptoAlg[F].decrypt`. 
