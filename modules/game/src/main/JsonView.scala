package lila.game

import play.api.libs.json._

import strategygames.format.{ FEN, Forsyth }
import strategygames.opening.FullOpening
import strategygames.{ P2, Clock, Player => SGPlayer, Division, GameLogic, Pocket, PocketData, Role, Status, P1 }
import strategygames.variant.Variant
import lila.common.Json.jodaWrites

final class JsonView(rematches: Rematches) {

  import JsonView._

  def apply(game: Game, initialFen: Option[FEN]) =
    Json
      .obj(
        "id"            -> game.id,
        "lib"           -> game.variant.gameLogic.id,
        "variant"       -> game.variant,
        "speed"         -> game.speed.key,
        "perf"          -> PerfPicker.key(game),
        "rated"         -> game.rated,
        "initialFen"    -> (initialFen | (Forsyth.>>(game.variant.gameLogic, game.chess))),
        "fen"           -> (Forsyth.>>(game.variant.gameLogic, game.chess)),
        "player"        -> game.turnSGPlayer,
        "turns"         -> game.turns,
        "startedAtTurn" -> game.chess.startedAtTurn,
        "source"        -> game.source,
        "status"        -> game.status,
        "createdAt"     -> game.createdAt,
        "winnerPlayer"  -> (game.winnerSGPlayer match {
          case Some(winner) => game.variant.playerNames(winner)
          case None => ""
        }),
        "loserPlayer"   -> (game.winnerSGPlayer match {
          case Some(winner) => game.variant.playerNames(!winner)
          case None => ""
        })
      )
      .add("threefold" -> game.situation.threefoldRepetition)
      .add("boosted" -> game.boosted)
      .add("tournamentId" -> game.tournamentId)
      .add("swissId" -> game.swissId)
      .add("winner" -> game.winnerSGPlayer)
      .add("lastMove" -> game.lastMoveKeys)
      .add("check" -> game.situation.checkSquare.map(_.key))
      .add("rematch" -> rematches.of(game.id))
      .add("drawOffers" -> (!game.drawOffers.isEmpty).option(game.drawOffers.normalizedPlies))
      .add("microMatch" -> game.metadata.microMatchGameNr.map { index =>
        Json.obj("index" -> index)
          .add("gameId" -> game.metadata.microMatchGameId.filter("*" !=))
      })
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

  implicit val pocketWriter: OWrites[Pocket] = OWrites { v =>
    JsObject(
      Role.storable(v.roles.headOption match {
        case Some(r) => r match {
          case Role.ChessRole(_)   => GameLogic.Chess()
          case Role.FairySFRole(_) => GameLogic.FairySF()
          case _ => sys.error("Pocket not implemented for GameLogic")
        }
        case None => GameLogic.Chess()
      }).flatMap { role =>
        Some(v.roles.count(role ==)).filter(0 <).map { count =>
          role.groundName -> JsNumber(count)
        }
      }
    )
  }

  implicit val pocketDataWriter: OWrites[PocketData] = OWrites { v =>
    Json.obj("pockets" -> List(v.pockets.p1, v.pockets.p2))
  }

  implicit val blursWriter: OWrites[Blurs] = OWrites { blurs =>
    Json.obj(
      "nb"   -> blurs.nb,
      "bits" -> blurs.binaryString
    )
  }

  implicit val variantWriter: OWrites[Variant] = OWrites { v =>
    v match {
      case Variant.Draughts(draughtsVariant) =>
        Json.obj(
          "key"   -> v.key,
          "name"  -> v.name,
          "short" -> v.shortName,
          "gameType" -> v.gameType,
          "board" -> draughtsVariant.boardSize,
          "lib"   -> v.gameLogic.id
        )
      case Variant.FairySF(fairyVariant) =>
        Json.obj(
          "key"   -> v.key,
          "name"  -> v.name,
          "short" -> v.shortName,
          "lib"   -> v.gameLogic.id,
          "boardSize" -> fairyVariant.boardSize
        )
      case _ =>
        Json.obj(
          "key"   -> v.key,
          "name"  -> v.name,
          "short" -> v.shortName,
          "lib"   -> v.gameLogic.id,
          "boardSize" -> Json.obj(
                              "width" -> 8,
                              "height" -> 8
                            )
        )
    }
  }

  implicit val boardSizeFairyWriter: Writes[strategygames.fairysf.Board.BoardSize] = Writes[strategygames.fairysf.Board.BoardSize] { b =>
    Json.obj(
      "width" -> b.width,
      "height" -> b.height
    )
  }

  implicit val boardSizeWriter: Writes[strategygames.draughts.Board.BoardSize] = Writes[strategygames.draughts.Board.BoardSize] { b =>
    Json.obj(
      "key" -> b.key,
      "size" -> b.sizes
    )
  }

  implicit val clockWriter: OWrites[Clock] = OWrites { c =>
    Json.obj(
      "running"   -> c.isRunning,
      "initial"   -> c.limitSeconds,
      "increment" -> c.incrementSeconds,
      "p1"     -> c.remainingTime(P1).toSeconds,
      "p2"     -> c.remainingTime(P2).toSeconds,
      "emerg"     -> c.config.emergSeconds
    )
  }

  implicit val correspondenceWriter: OWrites[CorrespondenceClock] = OWrites { c =>
    Json.obj(
      "daysPerTurn" -> c.daysPerTurn,
      "increment"   -> c.increment,
      "p1"       -> c.p1Time,
      "p2"       -> c.p2Time
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

  implicit val sgPlayerWrites: Writes[SGPlayer] = Writes { c =>
    JsString(c.name)
  }

  implicit val fenWrites: Writes[FEN] = Writes { f =>
    JsString(f.value)
  }
}
