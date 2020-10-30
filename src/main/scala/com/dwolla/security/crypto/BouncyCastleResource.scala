package com.dwolla.security.crypto

import java.security.Security

import cats.effect._
import cats.syntax.all._
import org.bouncycastle.jce.provider.BouncyCastleProvider

sealed trait BouncyCastleResource
object BouncyCastleResource {
  def apply[F[_] : Sync : ContextShift](blocker: Blocker,
                                        removeOnClose: Boolean = true): Resource[F, BouncyCastleResource] = {
    def registerBouncyCastle: F[BouncyCastleRegistrationToken] =
      for {
        provider <- blocker.delay(new BouncyCastleProvider)
        pos <- blocker.delay(Security.addProvider(provider))
      } yield BouncyCastleRegistrationToken(pos, provider.getName)

    def removeBouncyCastle(token: BouncyCastleRegistrationToken): F[Unit] =
      token match {
        case Registered(name) if removeOnClose => blocker.delay(Security.removeProvider(name))
        case _ => ().pure[F]
      }

    Resource.make(registerBouncyCastle)(removeBouncyCastle).as(new BouncyCastleResource {})
  }
}

private[crypto] sealed trait BouncyCastleRegistrationToken
private[crypto] object BouncyCastleRegistrationToken {
  def apply(position: Int, name: String): BouncyCastleRegistrationToken =
    if (-1 == position) AlreadyRegistered
    else Registered(name)
}

private[crypto] case class Registered(name: String) extends BouncyCastleRegistrationToken
private[crypto] case object AlreadyRegistered extends BouncyCastleRegistrationToken
