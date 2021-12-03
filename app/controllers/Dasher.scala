package controllers

import play.api.libs.json._

import lila.api.Context
import lila.app._
import lila.common.LightUser.lightUserWrites
import lila.i18n.{ enLang, I18nKeys => trans, I18nLangPicker, LangList }
import strategygames.{GameFamily}

final class Dasher(env: Env) extends LilaController(env) {

  private val translationsBase = List(
    trans.networkLagBetweenYouAndPlayStrategy,
    trans.timeToProcessAMoveOnPlayStrategyServer,
    trans.sound,
    trans.background,
    trans.light,
    trans.dark,
    trans.transparent,
    trans.backgroundImageUrl,
    trans.boardGeometry,
    trans.boardTheme,
    trans.boardSize,
    trans.pieceSet,
    trans.preferences.zenMode
  ).map(_.key)

  private val translationsAnon = List(
    trans.signIn,
    trans.signUp
  ).map(_.key) ::: translationsBase

  private val translationsAuth = List(
    trans.profile,
    trans.inbox,
    trans.preferences.preferences,
    trans.logOut
  ).map(_.key) ::: translationsBase

  private def translations(implicit ctx: Context) =
    lila.i18n.JsDump.keysToObject(
      if (ctx.isAnon) translationsAnon else translationsAuth,
      ctx.lang
    ) ++ lila.i18n.JsDump.keysToObject(
      // the language settings should never be in a totally foreign language
      List(trans.language.key),
      if (I18nLangPicker.allFromRequestHeaders(ctx.req).has(ctx.lang)) ctx.lang
      else I18nLangPicker.bestFromRequestHeaders(ctx.req) | enLang
    )

  def get =
    Open { implicit ctx =>
      negotiate(
        html = notFound,
        api = _ =>
          ctx.me.??(env.streamer.api.isPotentialStreamer) map { isStreamer =>
            Ok {
              Json.obj(
                "user" -> ctx.me.map(_.light),
                "lang" -> Json.obj(
                  "current"  -> ctx.lang.code,
                  "accepted" -> I18nLangPicker.allFromRequestHeaders(ctx.req).map(_.code),
                  "list"     -> LangList.allChoices
                ),
                "sound" -> Json.obj(
                  "list" -> lila.pref.SoundSet.list.map { set =>
                    s"${set.key} ${set.name}"
                  }
                ),
                "background" -> Json.obj(
                  "current" -> lila.pref.Pref.Bg.asString.get(ctx.pref.bg),
                  "image"   -> ctx.pref.bgImgOrDefault
                ),
                "board" -> Json.obj(
                  "is3d" -> ctx.pref.is3d
                ),
                "theme" -> Json.obj(
                  "d2" -> Json.obj(
                    "current" -> ctx.currentTheme.name,
                    "list"    -> lila.pref.Theme.all.map(_.name)
                  ),
                  "d3" -> Json.obj(
                    "current" -> ctx.currentTheme3d.name,
                    "list"    -> lila.pref.Theme3d.all.map(_.name)
                  )
                ),
                "piece" -> Json.obj(
                    "d2" -> Json.obj( 
                      "chess" -> Json.obj(
                          "current" -> Json.obj("name" -> ctx.currentPieceSet[0].name,
                                                "gameFamily" -> "chess", 
                                                "displayPiece" -> "wN"),
                          "list"    -> lila.pref.PieceSet.allOfFamily(GameFamily.Chess()).map( p =>
                                              Json.obj("name" -> p.name,
                                                      "gameFamily" -> p.gameFamily,
                                                      "displayPiece" -> p.displayPiece 
                                                      ))),
                      "draughts" -> Json.obj(
                          "current" -> Json.obj("name" -> ctx.currentPieceSet[1].name,
                                                "gameFamily" -> "draughts",
                                                "displayPiece" -> "wM" ),
                          "list"    -> lila.pref.PieceSet.allOfFamily(GameFamily.Draughts()).map( p =>
                                              Json.obj("name" -> p.name,
                                                      "gameFamily" -> p.gameFamily,
                                                      "displayPiece" -> p.displayPiece  
                                                      ))),
                      "loa" -> Json.obj(
                          "current" -> Json.obj("name" -> ctx.currentPieceSet[2].name,
                                                "gameFamily" -> "loa",
                                                "displayPiece" -> "wM" ),
                          "list"    -> lila.pref.PieceSet.allOfFamily(GameFamily.LinesOfAction()).map( p =>
                                              Json.obj("name" -> p.name,
                                                      "gameFamily" -> p.gameFamily,
                                                      "displayPiece" -> p.displayPiece  
                                                      ))),
                      "xiangqi" -> Json.obj(
                          "current" -> Json.obj("name" -> ctx.currentPieceSet[3].name,
                                                "gameFamily" -> "xiangqi",
                                                "displayPiece" -> "RH" ),
                          "list"    -> lila.pref.PieceSet.allOfFamily(GameFamily.Xiangqi()).map( p =>
                                              Json.obj("name" -> p.name,
                                                      "gameFamily" -> p.gameFamily,
                                                      "displayPiece" -> p.displayPiece  
                                                      ))),
                      "shogi" -> Json.obj(
                          "current" -> Json.obj("name" -> ctx.currentPieceSet[4].name,
                                                "gameFamily" -> "shogi",
                                                "displayPiece" -> "0KE" ),
                          "list"    -> lila.pref.PieceSet.allOfFamily(GameFamily.Shogi()).map( p =>
                                              Json.obj("name" -> p.name,
                                                      "gameFamily" -> p.gameFamily,
                                                      "displayPiece" -> p.displayPiece  
                                                      )))
                    ),
                    "d3" -> Json.obj(
                      "chess" -> Json.obj(
                        "current" -> Json.obj("name" -> ctx.currentPieceSet3d.name,
                                              "gameFamily" -> "chess",
                                               "displayPiece" -> "wN"),
                        "list"    -> lila.pref.PieceSet3d.all.map( p => 
                                            Json.obj("name" -> p.name,
                                                    "gameFamily" -> "chess",
                                                    "displayPiece" -> p.displayPiece 
                                                    ))
                      ))
                ),
                "coach"    -> isGranted(_.Coach),
                "streamer" -> isStreamer,
                "i18n"     -> translations
              )
            }
          }
      )
    }
}
