package lila.challenge

import play.api.libs.json._
import play.api.i18n.Lang

import lila.i18n.{ I18nKeys => trans }
import lila.socket.Socket.SocketVersion
import lila.socket.UserLagCache

import strategygames.variant.Variant
import strategygames.{ GameFamily, GameLogic, P1, P2 }

final class JsonView(
    baseUrl: lila.common.config.BaseUrl,
    getLightUser: lila.common.LightUser.GetterSync,
    isOnline: lila.socket.IsOnline
) {

  import lila.game.JsonView._
  import Challenge._

  implicit private val RegisteredWrites = OWrites[Challenger.Registered] { r =>
    val light = getLightUser(r.id)
    Json
      .obj(
        "id"     -> r.id,
        "name"   -> light.fold(r.id)(_.name),
        "title"  -> light.map(_.title),
        "rating" -> r.rating.int
      )
      .add("provisional" -> r.rating.provisional)
      .add("patron" -> light.??(_.isPatron))
      .add("online" -> isOnline(r.id))
      .add("lag" -> UserLagCache.getLagRating(r.id))
  }

  def apply(a: AllChallenges)(implicit lang: Lang): JsObject =
    Json.obj(
      "in"   -> a.in.map(apply(Direction.In.some)),
      "out"  -> a.out.map(apply(Direction.Out.some)),
      "i18n" -> lila.i18n.JsDump.keysToObject(i18nKeys, lang),
      "reasons" -> JsObject(Challenge.DeclineReason.allExceptBot.map { r =>
        r.key -> JsString(r.trans.txt())
      })
    )

  def show(challenge: Challenge, socketVersion: SocketVersion, direction: Option[Direction])(implicit
      lang: Lang
  ) =
    Json.obj(
      "challenge"     -> apply(direction)(challenge),
      "socketVersion" -> socketVersion
    )

  private def setupInfoJson(c: Challenge): String = {
    (c.initialFen, c.variant.gameFamily) match {
      case (Some(f), GameFamily.Go()) => c.variant.toGo.setupInfo(f.toGo).getOrElse("")
      case _                          => ""
    }
  }

  def apply(direction: Option[Direction])(c: Challenge)(implicit lang: Lang): JsObject =
    Json
      .obj(
        "id"         -> c.id,
        "url"        -> s"$baseUrl/${c.id}",
        "status"     -> c.status.name,
        "challenger" -> c.challengerUser,
        "destUser"   -> c.destUser,
        "lib"        -> c.variant.gameLogic.id,
        "variant"    -> c.variant,
        "rated"      -> c.mode.rated,
        "speed"      -> c.speed.key,
        "timeControl" -> (c.timeControl match {
          case TimeControl.Clock(clock) =>
            Json.obj(
              "type"  -> "clock",
              "limit" -> clock.limitSeconds,
              // TODO: this should be renamed to better reflect that it also
              //       represents Bronstein/Byoyomi/SimpleDelay
              "increment" -> clock.graceSeconds,
              "show"      -> clock.show
            )
          case TimeControl.Correspondence(d) =>
            Json.obj(
              "type"        -> "correspondence",
              "daysPerTurn" -> d
            )
          case TimeControl.Unlimited => Json.obj("type" -> "unlimited")
        }),
        "playerIndex"      -> c.playerIndexChoice.toString.toLowerCase,
        "finalPlayerIndex" -> c.finalPlayerIndex.toString.toLowerCase,
        "p1Color"          -> c.variant.playerColors(P1),
        "p2Color"          -> c.variant.playerColors(P2),
        "setupInfo"        -> setupInfoJson(c),
        "perf" -> Json.obj(
          "icon" -> iconChar(c).toString,
          "name" -> c.perfType.trans
        )
      )
      .add("direction" -> direction.map(_.name))
      .add("initialFen" -> c.initialFen)
      .add("declineReason" -> c.declineReason.map(_.trans.txt()))
      .add("multiMatch" -> c.multiMatch)

  private def iconChar(c: Challenge) =
    if (c.variant.fromPositionVariant) '*'
    else c.perfType.iconChar

  private val i18nKeys = List(
    trans.rated,
    trans.casual,
    trans.waiting,
    trans.accept,
    trans.decline,
    trans.viewInFullSize,
    trans.cancel,
    trans.multiMatch
  ).map(_.key)
}
