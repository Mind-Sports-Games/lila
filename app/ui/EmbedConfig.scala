package lila.app
package ui

import play.api.mvc.RequestHeader
import play.api.i18n.Lang

import lila.common.Nonce
import lila.api.Context

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

  def fromContext(ctx: Context): EmbedConfig =
    EmbedConfig(
      bg = get("bg", ctx.req).filterNot("auto".==) | "light",
      board = (get("theme", ctx.req), get("gf", ctx.req)) match {
        case (Some("auto"), _)       => ctx.currentTheme.map(_.cssClass).mkString(" ")
        case (Some(theme), Some(gf)) => lila.pref.Theme(theme, gf.toInt).cssClass
        case (_, _)                  => ctx.currentTheme.map(_.cssClass).mkString(" ")
      },
      pieceSets = (get("pieceSet", ctx.req), get("gf", ctx.req)) match {
        case (Some("auto"), _)    => ctx.currentPieceSet
        case (Some(ps), Some(gf)) => List(lila.pref.PieceSet(ps, gf.toInt))
        case (_, _)               => ctx.currentPieceSet
      },
      lang = lila.i18n.I18nLangPicker(ctx.req, ctx.me.flatMap(_.lang)),
      req = ctx.req,
      nonce = Nonce.random
    )

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
