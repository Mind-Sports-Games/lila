package lila.common

import play.api.data.*
import play.api.data.Forms.*
import play.api.data.format.*
import play.api.data.format.Formats.*
import play.api.data.validation.*

import lila.common.Form.*

class FormTest extends munit.FunSuite:

  import org.joda.time.{ DateTime, DateTimeZone }
  val date = new DateTime(2023, 4, 12, 11, 1, 15, 337, DateTimeZone.UTC)

  test("format iso datetime"):
    val mapping = single("t" -> lila.common.Form.ISODateTime.isoDateTime)
    assertEquals(mapping.unbind(date), Map("t" -> "2023-04-12T11:01:15.337+0000"))
  test("format iso date"):
    val mapping = single("t" -> lila.common.Form.ISODate.isoDateTime)
    assertEquals(mapping.unbind(date), Map("t" -> "2023-04-12"))
  test("format utc date"):
    val mapping = single("t" -> lila.common.Form.UTCDate.utcDate)
    assertEquals(mapping.unbind(date), Map("t" -> "2023-04-12 11:01"))
  test("format timestamp"):
    val mapping = single("t" -> lila.common.Form.Timestamp.timestamp)
    assertEquals(mapping.unbind(date), Map("t" -> "1681297275337"))
  test("format iso datetime or timestamp"):
    val mapping = single("t" -> lila.common.Form.ISODateTimeOrTimestamp.isoDateTimeOrTimestamp)
    assertEquals(mapping.unbind(date), Map("t" -> "2023-04-12T11:01:15.337+0000"))
  test("format iso date or timestamp"):
    val mapping = single("t" -> lila.common.Form.ISODateOrTimestamp.isoDateOrTimestamp)
    assertEquals(mapping.unbind(date), Map("t" -> "2023-04-12"))

  test("parse iso datetime"):
    val mapping = single("t" -> lila.common.Form.ISODateTime.isoDateTime)
    assert(mapping.bind(Map("t" -> "2023-04-25T10:00:00.000+0000")).isRight)
    assert(mapping.bind(Map("t" -> "2017-01-01T12:34:56.000+0000")).isRight)
    assert(mapping.bind(Map("t" -> "2017-01-01T12:34:56")).isLeft)
    assert(mapping.bind(Map("t" -> "2017-01-01")).isLeft)
  test("parse iso date"):
    val mapping = single("t" -> lila.common.Form.ISODate.isoDateTime)
    assert(mapping.bind(Map("t" -> "2017-01-01")).isRight)
    assert(mapping.bind(Map("t" -> "2023-04-25T10:00:00.000+0000")).isLeft)
    assert(mapping.bind(Map("t" -> "2017-01-01T12:34:56+0000")).isLeft)
    assert(mapping.bind(Map("t" -> "2017-01-01T12:34:56")).isLeft)
  test("parse utc date"):
    val mapping = single("t" -> lila.common.Form.UTCDate.utcDate)
    assert(mapping.bind(Map("t" -> "2017-01-01 23:11")).isRight)
    assert(mapping.bind(Map("t" -> "2023-04-25T10:00:00.000+0000")).isLeft)
    assert(mapping.bind(Map("t" -> "2017-01-01T12:34:56+0000")).isLeft)
    assert(mapping.bind(Map("t" -> "2017-01-01")).isLeft)
  test("parse timestamp"):
    val mapping = single("t" -> lila.common.Form.Timestamp.timestamp)
    assert(mapping.bind(Map("t" -> "1483228800000")).isRight)
    assert(mapping.bind(Map("t" -> "2017-01-01 23:11")).isLeft)
    assert(mapping.bind(Map("t" -> "2023-04-25T10:00:00.000+0000")).isLeft)
    assert(mapping.bind(Map("t" -> "2017-01-01T12:34:56+0000")).isLeft)
    assert(mapping.bind(Map("t" -> "2017-01-01")).isLeft)
  test("parse iso datetime or timestamp"):
    val mapping = single("t" -> lila.common.Form.ISODateTimeOrTimestamp.isoDateTimeOrTimestamp)
    assert(mapping.bind(Map("t" -> "1483228800000")).isRight)
    assert(mapping.bind(Map("t" -> "2023-04-25T10:00:00.000+0000")).isRight)
    assert(mapping.bind(Map("t" -> "2017-01-01 23:11")).isLeft)
    assert(mapping.bind(Map("t" -> "2017-01-01T12:34:56.000+0000")).isRight)
    assert(mapping.bind(Map("t" -> "2017-01-01")).isLeft)
  test("parse iso date or timestamp"):
    val mapping = single("t" -> lila.common.Form.ISODateOrTimestamp.isoDateOrTimestamp)
    assert(mapping.bind(Map("t" -> "1483228800000")).isRight)
    assert(mapping.bind(Map("t" -> "2017-01-01")).isRight)
    assert(mapping.bind(Map("t" -> "2023-04-25T10:00:00.000+0000")).isLeft)
    assert(mapping.bind(Map("t" -> "2017-01-01 23:11")).isLeft)
    assert(mapping.bind(Map("t" -> "2017-01-01T12:34:56+0000")).isLeft)

  test("trim before validation"):

    assert(
      FieldMapping("t", List(Constraints.minLength(1)))
        .bind(Map("t" -> " "))
        .isRight
    )

    assert(
      FieldMapping("t", List(Constraints.minLength(1)))
        .as(cleanTextFormatter)
        .bind(Map("t" -> " "))
        .isLeft
    )

    assert(
      single("t" -> cleanText(minLength = 3))
        .bind(Map("t" -> "     "))
        .isLeft
    )

    assert(
      single("t" -> cleanText(minLength = 3))
        .bind(Map("t" -> "aa "))
        .isLeft
    )

    assert(
      single("t" -> cleanText(minLength = 3))
        .bind(Map("t" -> "aaa"))
        .isRight
    )

    assert(
      single("t" -> text)
        .bind(Map("t" -> ""))
        .isRight
    )
    assert(
      single("t" -> cleanText)
        .bind(Map("t" -> ""))
        .isRight
    )
    assert(
      single("t" -> cleanText)
        .bind(Map("t" -> "   "))
        .isRight
    )

  test("invisible chars are removed before validation"):
    val invisibleChars = List('\u200e', '\u200f', '\u202e', '\u1160')
    val invisibleStr = invisibleChars.mkString("")
    assertEquals(single("t" -> cleanText).bind(Map("t" -> invisibleStr)), Right(""))
    assertEquals(single("t" -> cleanText).bind(Map("t" -> s"  $invisibleStr  ")), Right(""))
    assert(single("t" -> cleanText(minLength = 1)).bind(Map("t" -> invisibleStr)).isLeft)
    assert(single("t" -> cleanText(minLength = 1)).bind(Map("t" -> s"  $invisibleStr  ")).isLeft)
    // braille space
    assert(single("t" -> cleanText(minLength = 1)).bind(Map("t" -> "⠀")).isLeft)
  test("other garbage chars are also removed before validation, unless allowed"):
    val garbageStr = "꧁ ۩۞"
    assertEquals(single("t" -> cleanText).bind(Map("t" -> garbageStr)), Right(""))
  test("emojis are removed before validation, unless allowed"):
    val emojiStr = "🌈🌚"
    assertEquals(single("t" -> cleanText).bind(Map("t" -> emojiStr)), Right(""))

  test("special chars"):
    val half = '½'
    assertEquals(single("t" -> cleanText).bind(Map("t" -> half.toString)), Right(half.toString))
