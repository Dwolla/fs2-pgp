# fs2-pgp

A library for encrypting and decrypting fs2 `Stream[F, Byte]` using PGP.

<table>
<thead>
<tr>
<th>Artifact</th>
<th>Description</th>
<th align="center">Scala 2.12</th>
<th align="center">Scala 2.13</th>
<th align="center">Scala 3</th>
</tr>
</thead>
<tbody>
<tr>
<td><code>"fs2-pgp"</code></td>
<td>Load PGP keys and use them to encrypt, decrypt, and armor byte streams.</td>
<td align="center"><g-emoji class="g-emoji" alias="white_check_mark" fallback-src="https://github.githubassets.com/images/icons/emoji/unicode/2705.png">✅</g-emoji></td>
<td align="center"><g-emoji class="g-emoji" alias="white_check_mark" fallback-src="https://github.githubassets.com/images/icons/emoji/unicode/2705.png">✅</g-emoji></td>
<td align="center"><g-emoji class="g-emoji" alias="white_check_mark" fallback-src="https://github.githubassets.com/images/icons/emoji/unicode/2705.png">✅</g-emoji></td>
</tr>
<tr>
<td><code>"pgp-testkit"</code></td>
<td>ScalaCheck Generators and Arbitrary instances for PGP classes</td>
<td align="center"><g-emoji class="g-emoji" alias="white_check_mark" fallback-src="https://github.githubassets.com/images/icons/emoji/unicode/2705.png">✅</g-emoji></td>
<td align="center"><g-emoji class="g-emoji" alias="white_check_mark" fallback-src="https://github.githubassets.com/images/icons/emoji/unicode/2705.png">✅</g-emoji></td>
<td align="center"><g-emoji class="g-emoji" alias="white_check_mark" fallback-src="https://github.githubassets.com/images/icons/emoji/unicode/2705.png">✅</g-emoji></td>
</tr>
<tr>
<td><code>"fs2-pgp-scalafix"</code></td>
<td>Scalafix rewrite rules to help update from `v0.4` to `v0.5`</td>
<td align="center"><g-emoji class="g-emoji" alias="white_check_mark" fallback-src="https://github.githubassets.com/images/icons/emoji/unicode/2705.png">✅</g-emoji></td>
<td align="center"><g-emoji class="g-emoji" alias="white_check_mark" fallback-src="https://github.githubassets.com/images/icons/emoji/unicode/2705.png">✅</g-emoji></td>
<td align="center"><g-emoji class="g-emoji" alias="x" fallback-src="https://github.githubassets.com/images/icons/emoji/unicode/274c.png">❌</g-emoji></td>
</tr>
</tbody>
</table>

## Bouncy Castle Versions

Bouncy Castle often releases versions that report as binary-incompatible with 
previous versions when examined with a tool such as [MiMa](https://github.com/lightbend/mima).

Upon each release of this library, artifacts are published with the version of
Bouncy Castle supported by that artifact. For example, the latest Bouncy Castle
version is `1.80`, so we'll publish `com.dwolla::fs2-pgp-bcpg1.80`. In addition,
we publish `com.dwolla::fs2-pgp` as an empty artifact that will depend on the
more specifically versioned artifact (i.e., `com.dwolla::fs2-pgp` depends on
`com.dwolla::fs2-pgp-bcpg1.80`.)

This allows applications to depend on `com.dwolla::fs2-pgp` and always stay up
to date with the latest supported version of Bouncy Castle, while libraries
can depend on a specifically versioned artifact like
`com.dwolla::fs2-pgp-bcpg1.80` to address binary compatibility concerns.

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

## Scalafix Rule

Add Scalafix to your project's build by [following the instructions](https://scalacenter.github.io/scalafix/docs/users/installation.html#sbt):

1. Add the Scalafix plugin to the project by adding this to `project/plugins.sbt`:

    ```scala
    addSbtPlugin("ch.epfl.scala" % "sbt-scalafix" % "0.11.1")
    ```

2. Enable SemanticDB by adding this to `build.sbt`:

    ```scala
    ThisBuild / semanticdbEnabled := true
    ThisBuild / semanticdbVersion := scalafixSemanticdb.revision
    ThisBuild / scalafixScalaBinaryVersion := CrossVersion.binaryScalaVersion(scalaVersion.value)
    ThisBuild / scalafixDependencies += "com.dwolla" %% "fs2-pgp-scalafix" % "0.5.0"
    ```

3. Make sure everything compiles, and run the Scalafix rule on the sbt console:

    ```
    Test/compile
    Test/scalafix com.dwolla.security.crypto.V04to05
    scalafix com.dwolla.security.crypto.V04to05
    ```

   (Run the Scalafix rule on the leafs of your project graph, and then work back to the middle. Tests should be updated before production code, because if the production code is updated, the test project won't compile anymore, etc.)

4. Update the fs2-pgp version from `v0.4` to `v0.5`. (At this point, you can remove the Scalafix and SemanticDB keys from your build if you want.)
