package lila.simul

import play.api.libs.json._

import scala.util.Random
import lila.common.LightUser
import lila.game.{ Game, GameRepo }
import lila.user.User
import lila.i18n.VariantKeys
import lila.quote.Quote
import strategygames.{ GameFamily, P1, P2 }
import strategygames.variant.Variant

final class JsonView(
    gameRepo: GameRepo,
    getLightUser: LightUser.Getter,
    proxyRepo: lila.round.GameProxyRepo
)(implicit ec: scala.concurrent.ExecutionContext) {

  implicit private val playerIndexWriter: Writes[strategygames.Player] = Writes { c =>
    JsString(c.name)
  }

  implicit private val simulTeamWriter = Json.writes[SimulTeam]

  private def fetchGames(simul: Simul) =
    if (simul.isFinished) gameRepo gamesFromSecondary simul.gameIds
    else simul.gameIds.map(proxyRepo.game).sequenceFu.dmap(_.flatten)

  def apply(simul: Simul, team: Option[SimulTeam]): Fu[JsObject] =
    for {
      games      <- fetchGames(simul)
      lightHost  <- getLightUser(simul.hostId)
      applicants <- simul.applicants.sortBy(-_.player.rating).map(applicantJson).sequenceFu
      pairingOptions <-
        simul.pairings
          .sortBy(-_.player.rating)
          .map(pairingJson(games, simul.hostId))
          .sequenceFu
      pairings = pairingOptions.flatten
    } yield baseSimul(simul, lightHost) ++ Json
      .obj(
        "applicants" -> applicants,
        "pairings"   -> pairings
      )
      .add("team", team)
      .add(
        "quote" -> simul.isCreated
          .option(Quote.one(simul.id, Some(simul.variants(Random.nextInt(simul.variants.size)).gameFamily)))
      )

  def api(simul: Simul): Fu[JsObject] =
    getLightUser(simul.hostId) map { lightHost =>
      baseSimul(simul, lightHost) ++ Json.obj(
        "nbApplicants" -> simul.applicants.size,
        "nbPairings"   -> simul.pairings.size
      )
    }

  def api(simuls: List[Simul]): Fu[JsArray] =
    simuls.map(api).sequenceFu map JsArray.apply

  def apiAll(
      pending: List[Simul],
      created: List[Simul],
      started: List[Simul],
      finished: List[Simul]
  ): Fu[JsObject] =
    for {
      pendingJson  <- api(pending)
      createdJson  <- api(created)
      startedJson  <- api(started)
      finishedJson <- api(finished)
    } yield Json.obj(
      "pending"  -> pendingJson,
      "created"  -> createdJson,
      "started"  -> startedJson,
      "finished" -> finishedJson
    )

  private def baseSimul(simul: Simul, lightHost: Option[LightUser]) =
    Json.obj(
      "id" -> simul.id,
      "host" -> lightHost.map { host =>
        Json
          .obj(
            "id"     -> host.id,
            "name"   -> host.name,
            "rating" -> simul.hostRating
          )
          .add("gameId" -> simul.hostGameId.ifTrue(simul.isRunning))
          .add("title" -> host.title)
          .add("patron" -> host.isPatron)
      },
      "name"       -> simul.name,
      "fullName"   -> simul.fullName,
      "variants"   -> simul.variants.map(variantJson(strategygames.Speed(simul.clock.config.some))),
      "isCreated"  -> simul.isCreated,
      "isRunning"  -> simul.isRunning,
      "isFinished" -> simul.isFinished,
      "text"       -> simul.text
    )

  private def variantJson(speed: strategygames.Speed)(v: strategygames.variant.Variant) =
    Json.obj(
      "key"  -> v.key,
      "icon" -> lila.game.PerfPicker.perfType(speed, v, none).map(_.iconChar.toString),
      "name" -> VariantKeys.variantName(v)
    )

  private def playerJson(player: SimulPlayer): Fu[JsObject] =
    getLightUser(player.user) map { light =>
      Json
        .obj(
          "id"     -> player.user,
          "rating" -> player.rating
        )
        .add("name" -> light.map(_.name))
        .add("title" -> light.map(_.title))
        .add("provisional" -> ~player.provisional)
        .add("patron" -> light.??(_.isPatron))
    }

  private def applicantJson(app: SimulApplicant): Fu[JsObject] =
    playerJson(app.player) map { player =>
      Json.obj(
        "player"   -> player,
        "variant"  -> app.player.variant.key,
        "accepted" -> app.accepted
      )
    }

  private def boardSizeJson(v: Variant) = v match {
    case Variant.Draughts(v) =>
      Some(
        Json.obj(
          "size" -> Json.arr(v.boardSize.width, v.boardSize.height),
          "key"  -> v.boardSize.key
        )
      )
    case _ => None
  }

  private def gameJson(hostId: User.ID, g: Game) =
    Json
      .obj(
        "id"     -> g.id,
        "status" -> g.status.id,
        "fen" -> (strategygames.format.Forsyth.boardAndPlayer(
          g.situation.board.variant.gameLogic,
          g.situation
        )),
        "gameLogic" -> g.situation.board.variant.gameLogic.name.toLowerCase(),
        "boardSize" -> boardSizeJson(g.situation.board.variant),
        "lastMove"  -> ~g.lastMoveKeys,
        "orient"    -> g.playerByUserId(hostId).map(_.playerIndex)
      )
      .add(
        "clock" -> g.clock.ifTrue(g.isBeingPlayed).map { c =>
          Json.obj(
            "p1" -> c.remainingTime(strategygames.P1).roundSeconds,
            "p2" -> c.remainingTime(strategygames.P2).roundSeconds
          )
        }
      )
      .add("winner" -> g.winnerPlayerIndex.map(_.name))

  private def pairingJson(games: List[Game], hostId: String)(p: SimulPairing): Fu[Option[JsObject]] =
    games.find(_.id == p.gameId) ?? { game =>
      playerJson(p.player) map { player =>
        Json
          .obj(
            "player"          -> player,
            "variant"         -> p.player.variant.key,
            "hostPlayerIndex" -> p.hostPlayerIndex,
            "game"            -> gameJson(hostId, game),
            "p1Color"         -> p.player.variant.playerColors(P1),
            "p2Color"         -> p.player.variant.playerColors(P2)
          )
          .some
      }
    }
}
