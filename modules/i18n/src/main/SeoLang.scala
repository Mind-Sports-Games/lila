package lila.i18n

import play.api.i18n.Lang
import play.api.mvc.RequestHeader

// The locales maintained by hand, and the URL segment each one's pages are served under.
// Only these get an alternate URL and an hreflang link.
object SeoLang {

  case class Entry(href: String, tag: String, lang: Lang)

  val all: List[Entry] = List(
    Entry("de", "de", Lang("de", "DE")),
    Entry("es", "es", Lang("es", "ES")),
    Entry("fr", "fr", Lang("fr", "FR")),
    Entry("ja", "ja", Lang("ja", "JP")),
    Entry("ko", "ko", Lang("ko", "KR")),
    Entry("pt-br", "pt-BR", Lang("pt", "BR")),
    Entry("pt-pt", "pt-PT", Lang("pt", "PT")),
    Entry("ru", "ru", Lang("ru", "RU")),
    Entry("vi", "vi", Lang("vi", "VN")),
    Entry("zh-cn", "zh-CN", Lang("zh", "CN"))
  )

  // Every hreflang/path pair for a page that exists in all maintained locales: x-default and en are
  // the unprefixed page. One list, so the page's own link tags and the sitemap's cannot disagree.
  def alternates(barePath: String): List[(String, String)] =
    ("x-default" -> barePath) :: ("en" -> barePath) :: all.map(e => e.tag -> s"/${e.href}$barePath")

  private val byHrefSegment: Map[String, Entry] = all.view.map(e => e.href -> e).toMap

  sealed trait ByHref
  object ByHref {
    case class Found(lang: Lang)   extends ByHref
    case class Refused(lang: Lang) extends ByHref
    case object NotFound           extends ByHref
  }

  // "/vi/library/xiangqi" -> (vi, "/library/xiangqi"). Anything else keeps its path untouched.
  def splitPath(path: String): (Option[Entry], String) = {
    val (segment, rest) = path.stripPrefix("/").span(_ != '/')
    byHrefSegment.get(segment.toLowerCase).fold(Option.empty[Entry] -> path) { entry =>
      entry.some -> rest
    }
  }

  // A crawler is always served what the URL asked for - several send an Accept-Language, and a
  // redirect would make every hreflang link point at a redirecting URL. A visitor whose browser
  // wants another language goes back to the unprefixed page, where their own preference wins.
  def byHref(segment: String, req: RequestHeader): ByHref =
    byHrefSegment.get(segment.toLowerCase) match {
      case None => ByHref.NotFound
      case Some(entry) =>
        val accepted = I18nLangPicker.allFromRequestHeaders(req)
        if (accepted.isEmpty || lila.common.HTTPRequest.isCrawler(req) ||
          accepted.exists(_.language == entry.lang.language)) ByHref.Found(entry.lang)
        else ByHref.Refused(entry.lang)
    }
}
