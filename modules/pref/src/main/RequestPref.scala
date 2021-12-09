package lila.pref

import play.api.mvc.RequestHeader
import play.api.libs.json._
import lila.pref.JsonView.pieceSetsRead

object RequestPref {

  import Pref.default

  def queryParamOverride(req: RequestHeader)(pref: Pref): Pref =
    queryParam(req, "bg").flatMap(Pref.Bg.fromString.get).fold(pref) { bg =>
      pref.copy(bg = bg)
    }

  def fromRequest(req: RequestHeader): Pref = {

    def paramOrSession(name: String): Option[String] =
      queryParam(req, name) orElse req.session.get(name)
    
    def updateSessionWithParam(name: String): Option[List[PieceSet]] = {
      //Session data is only used for guests it would seem...
      req.session.pp("session").get(name.pp("pref name")).pp("name value")
        .map(Json.parse)
        .flatMap(_.validate(pieceSetsRead).asOpt)
        .map{ps => queryParam(req, name).pp("query param") 
                  .fold(ps)(p2 => PieceSet.updatePieceSet(ps, p2.pp("update 2 value")))
    }}

    default.copy(
      bg = paramOrSession("bg").flatMap(Pref.Bg.fromString.get) | default.bg,
      theme = paramOrSession("theme") | default.theme,
      theme3d = paramOrSession("theme3d") | default.theme3d,
      pieceSet = updateSessionWithParam("pieceSet") | default.pieceSet,
      pieceSet3d = paramOrSession("pieceSet3d") | default.pieceSet3d,
      soundSet = paramOrSession("soundSet") | default.soundSet,
      bgImg = paramOrSession("bgImg"),
      is3d = paramOrSession("is3d") has "true"
    )
  }

  private def queryParam(req: RequestHeader, name: String): Option[String] =
    req.queryString.get(name).flatMap(_.headOption).filter { v =>
      v.nonEmpty && v != "auto"
    }
}
