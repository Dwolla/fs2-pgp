package com.dwolla.testutils

import java.time._
import java.util.Date

import org.scalatest.matchers.{MatchResult, Matcher}

import scala.util.Try

trait DateMatchers {
  private def failed(msg: String): MatchResult =
    MatchResult(matches = false, msg, "this shouldn't be reached")

  def beTheSameInstantAs(expectedString: String): Matcher[Date] = (maybeDate: Date) =>
    Try(Instant.parse(expectedString))
      .fold[MatchResult](
        _ => failed(s"Could not parse $expectedString as an Instant"),
        beTheSameInstantAs(_)(maybeDate)
      )

  def beTheSameInstantAs(expected: Instant): Matcher[Date] = (maybeDate: Date) =>
    Option(maybeDate).fold(failed(s"Got null, but expected $expected")) { actual =>
      MatchResult(expected.equals(actual.toInstant), s"$actual was not $expected", s"$actual is the same instant as $expected")
    }
}
