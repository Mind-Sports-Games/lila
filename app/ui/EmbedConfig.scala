package lila.app
package ui

import play.api.mvc.RequestHeader
import play.api.i18n.Lang

import lila.common.Nonce

case class EmbedConfig(
    bg: String,
    board: String,
    pieceSets: List[lila.pref.PieceSet],
    lang: Lang,
    req: RequestHeader,
    nonce: Nonce
)

object EmbedConfig {

  object implicits {
    implicit def configLang(implicit config: EmbedConfig): Lang         = config.lang
    implicit def configReq(implicit config: EmbedConfig): RequestHeader = config.req
  }

  def apply(req: RequestHeader): EmbedConfig =
    EmbedConfig(
      bg = get("bg", req).filterNot("auto".==) | "light",
      board = (get("theme", req), get("gf", req)) match {
        case (Some("auto"), _)       => lila.pref.Theme.defaults.map(_.cssClass).mkString(" ")
        case (Some(theme), Some(gf)) => lila.pref.Theme(theme, gf.toInt).cssClass
        case (_, _)                  => lila.pref.Theme.defaults.map(_.cssClass).mkString(" ")
      },
      pieceSets = (get("pieceSet", req), get("gf", req)) match {
        case (Some("auto"), _)    => lila.pref.PieceSet.defaults
        case (Some(ps), Some(gf)) => List(lila.pref.PieceSet(ps, gf.toInt))
        case (_, _)               => lila.pref.PieceSet.defaults
      },
      lang = lila.i18n.I18nLangPicker(req, none),
      req = req,
      nonce = Nonce.random
    )

  private def get(name: String, req: RequestHeader): Option[String] =
    req.queryString get name flatMap (_.headOption) filter (_.nonEmpty)
}
