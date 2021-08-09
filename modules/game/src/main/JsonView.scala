package lila.game

import play.api.libs.json._

import strategygames.format.{ FEN, Forsyth }
import strategygames.chess.variant.Crazyhouse
import strategygames.opening.FullOpening
import strategygames.{ Black, Clock, Color, Division, Status, White }
import strategygames.variant.Variant
import lila.common.Json.jodaWrites

final class JsonView(rematches: Rematches) {

  import JsonView._

  def apply(game: Game, initialFen: Option[FEN]) =
    Json
      .obj(
        "id"            -> game.id,
        "variant"       -> game.variant,
        "speed"         -> game.speed.key,
        "perf"          -> PerfPicker.key(game),
        "rated"         -> game.rated,
        "initialFen"    -> (initialFen | Forsyth.initial(game.variant.gameLib)),
        "fen"           -> (Forsyth.>>(game.variant.gameLib, game.chess)),
        "player"        -> game.turnColor,
        "turns"         -> game.turns,
        "startedAtTurn" -> game.chess.startedAtTurn,
        "source"        -> game.source,
        "status"        -> game.status,
        "createdAt"     -> game.createdAt
      )
      .add("threefold" -> game.history.threefoldRepetition)
      .add("boosted" -> game.boosted)
      .add("tournamentId" -> game.tournamentId)
      .add("swissId" -> game.swissId)
      .add("winner" -> game.winnerColor)
      .add("lastMove" -> game.lastMoveKeys)
      .add("check" -> game.situation.checkSquare.map(_.key))
      .add("rematch" -> rematches.of(game.id))
      .add("drawOffers" -> (!game.drawOffers.isEmpty).option(game.drawOffers.normalizedPlies))
}

object JsonView {

  implicit val statusWrites: OWrites[Status] = OWrites { s =>
    Json.obj(
      "id"   -> s.id,
      "name" -> s.name
    )
  }

  implicit val crosstableResultWrites = Json.writes[Crosstable.Result]

  implicit val crosstableUsersWrites = OWrites[Crosstable.Users] { users =>
    JsObject(users.toList.map { u =>
      u.id -> JsNumber(u.score / 10d)
    })
  }

  implicit val crosstableWrites = OWrites[Crosstable] { c =>
    Json.obj(
      "users"   -> c.users,
      "nbGames" -> c.nbGames
      // "results" -> c.results
    )
  }

  implicit val matchupWrites = OWrites[Crosstable.Matchup] { m =>
    Json.obj(
      "users"   -> m.users,
      "nbGames" -> m.users.nbGames
    )
  }

  def crosstable(ct: Crosstable, matchup: Option[Crosstable.Matchup]) =
    crosstableWrites
      .writes(ct)
      .add("matchup" -> matchup)

  implicit val crazyhousePocketWriter: OWrites[Crazyhouse.Pocket] = OWrites { v =>
    JsObject(
      Crazyhouse.storableRoles.flatMap { role =>
        Some(v.roles.count(role ==)).filter(0 <).map { count =>
          role.name -> JsNumber(count)
        }
      }
    )
  }

  implicit val crazyhouseDataWriter: OWrites[Crazyhouse.Data] = OWrites { v =>
    Json.obj("pockets" -> List(v.pockets.white, v.pockets.black))
  }

  implicit val blursWriter: OWrites[Blurs] = OWrites { blurs =>
    Json.obj(
      "nb"   -> blurs.nb,
      "bits" -> blurs.binaryString
    )
  }

  implicit val variantWriter: OWrites[Variant] = OWrites { v =>
    Json.obj(
      "key"   -> v.key,
      "name"  -> v.name,
      "short" -> v.shortName
    )
  }

  implicit val clockWriter: OWrites[Clock] = OWrites { c =>
    Json.obj(
      "running"   -> c.isRunning,
      "initial"   -> c.limitSeconds,
      "increment" -> c.incrementSeconds,
      "white"     -> c.remainingTime(White).toSeconds,
      "black"     -> c.remainingTime(Black).toSeconds,
      "emerg"     -> c.config.emergSeconds
    )
  }

  implicit val correspondenceWriter: OWrites[CorrespondenceClock] = OWrites { c =>
    Json.obj(
      "daysPerTurn" -> c.daysPerTurn,
      "increment"   -> c.increment,
      "white"       -> c.whiteTime,
      "black"       -> c.blackTime
    )
  }

  implicit val openingWriter: OWrites[FullOpening.AtPly] = OWrites { o =>
    Json.obj(
      "eco"  -> o.opening.eco,
      "name" -> o.opening.name,
      "ply"  -> o.ply
    )
  }

  implicit val divisionWriter: OWrites[Division] = OWrites { o =>
    Json.obj(
      "middle" -> o.middle,
      "end"    -> o.end
    )
  }

  implicit val sourceWriter: Writes[Source] = Writes { s =>
    JsString(s.name)
  }

  implicit val colorWrites: Writes[Color] = Writes { c =>
    JsString(c.name)
  }

  implicit val fenWrites: Writes[FEN] = Writes { f =>
    JsString(f.value)
  }
}
