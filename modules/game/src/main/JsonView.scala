package lila.game

import play.api.libs.json._

import strategygames.format.{ FEN, Forsyth }
import strategygames.opening.FullOpening
import strategygames.{
  P2,
  ByoyomiClock,
  Clock,
  Player => PlayerIndex,
  Division,
  FischerClock,
  GameLogic,
  Pocket,
  PocketData,
  Role,
  Status,
  P1
}
import strategygames.variant.Variant
import lila.common.Json.jodaWrites
import lila.i18n.VariantKeys

final class JsonView(rematches: Rematches) {

  import JsonView._

  def apply(game: Game, initialFen: Option[FEN]) =
    Json
      .obj(
        "id"            -> game.id,
        "lib"           -> game.variant.gameLogic.id,
        "variant"       -> game.variant,
        "gameFamily"    -> game.variant.gameFamily.key,
        "speed"         -> game.speed.key,
        "perf"          -> PerfPicker.key(game),
        "rated"         -> game.rated,
        "initialFen"    -> (initialFen | Forsyth.initial(game.variant.gameLogic)),
        "fen"           -> (Forsyth.>>(game.variant.gameLogic, game.chess)),
        "player"        -> game.turnPlayerIndex,
        "turns"         -> game.turns,
        "startedAtTurn" -> game.chess.startedAtTurn,
        "source"        -> game.source,
        "status"        -> game.status,
        "createdAt"     -> game.createdAt
      )
      .add("threefold" -> game.situation.threefoldRepetition)
      .add("isRepetition" -> game.situation.isRepetition)
      .add("perpetualWarning" -> game.situation.perpetualPossible)
      .add("boosted" -> game.boosted)
      .add("tournamentId" -> game.tournamentId)
      .add("swissId" -> game.swissId)
      .add("winner" -> game.winnerPlayerIndex)
      .add("winnerPlayer" -> game.winnerPlayerIndex.map(game.variant.playerNames))
      .add("loserPlayer" -> game.winnerPlayerIndex.map(w => game.variant.playerNames(!w)))
      .add("lastMove" -> game.lastMoveKeys)
      .add("check" -> game.situation.checkSquare.map(_.key))
      .add("rematch" -> rematches.of(game.id))
      .add("drawOffers" -> (!game.drawOffers.isEmpty).option(game.drawOffers.normalizedPlies))
      .add("multiMatch" -> game.metadata.multiMatchGameNr.map { index =>
        Json
          .obj("index" -> index)
          .add("gameId" -> game.metadata.multiMatchGameId.filter("*" !=))
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
      Role
        .storable(v.roles.headOption match {
          case Some(r) =>
            r match {
              case Role.ChessRole(_)   => GameLogic.Chess()
              case Role.FairySFRole(_) => GameLogic.FairySF()
              case _                   => sys.error("Pocket not implemented for GameLogic")
            }
          case None => GameLogic.Chess()
        })
        .flatMap { role =>
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
          "key"       -> v.key,
          "name"      -> VariantKeys.variantName(v),
          "short"     -> VariantKeys.variantShortName(v),
          "gameType"  -> v.gameType,
          "board"     -> draughtsVariant.boardSize,
          "boardSize" -> draughtsVariant.boardSize,
          "lib"       -> v.gameLogic.id
        )
      case Variant.FairySF(fairyVariant) =>
        Json.obj(
          "key"       -> v.key,
          "name"      -> VariantKeys.variantName(v),
          "short"     -> VariantKeys.variantShortName(v),
          "lib"       -> v.gameLogic.id,
          "boardSize" -> fairyVariant.boardSize
        )
      case Variant.Samurai(samuraiVariant) =>
        Json.obj(
          "key"       -> v.key,
          "name"      -> VariantKeys.variantName(v),
          "short"     -> VariantKeys.variantShortName(v),
          "lib"       -> v.gameLogic.id,
          "boardSize" -> samuraiVariant.boardSize
        )
      case Variant.Togyzkumalak(togyzkumalakVariant) =>
        Json.obj(
          "key"       -> v.key,
          "name"      -> VariantKeys.variantName(v),
          "short"     -> VariantKeys.variantShortName(v),
          "lib"       -> v.gameLogic.id,
          "boardSize" -> togyzkumalakVariant.boardSize
        )
      case _ =>
        Json.obj(
          "key"   -> v.key,
          "name"  -> VariantKeys.variantName(v),
          "short" -> VariantKeys.variantShortName(v),
          "lib"   -> v.gameLogic.id,
          "boardSize" -> Json.obj(
            "width"  -> 8,
            "height" -> 8
          )
        )
    }
  }

  implicit val boardSizeFairyWriter: Writes[strategygames.fairysf.Board.BoardSize] =
    Writes[strategygames.fairysf.Board.BoardSize] { b =>
      Json.obj(
        "width"  -> b.width,
        "height" -> b.height
      )
    }

  implicit val boardSizeSamuraiWriter: Writes[strategygames.samurai.Board.BoardSize] =
    Writes[strategygames.samurai.Board.BoardSize] { b =>
      Json.obj(
        "width"  -> b.width,
        "height" -> b.height
      )
    }

  implicit val boardSizeTogyzkumalakWriter: Writes[strategygames.togyzkumalak.Board.BoardSize] =
    Writes[strategygames.togyzkumalak.Board.BoardSize] { b =>
      Json.obj(
        "width"  -> b.width,
        "height" -> b.height
      )
    }

  implicit val boardSizeWriter: Writes[strategygames.draughts.Board.BoardSize] =
    Writes[strategygames.draughts.Board.BoardSize] { b =>
      Json.obj(
        "key"    -> b.key,
        "size"   -> b.sizes,
        "width"  -> b.width,
        "height" -> b.height
      )
    }

  implicit val clockWriter: OWrites[Clock] = OWrites { c =>
    c match {
      case fc: FischerClock =>
        Json.obj(
          "running"   -> fc.isRunning,
          "initial"   -> fc.limitSeconds,
          "increment" -> fc.incrementSeconds,
          "p1"        -> fc.remainingTime(P1).toSeconds,
          "p2"        -> fc.remainingTime(P2).toSeconds,
          "emerg"     -> fc.config.emergSeconds
        )
      case bc: ByoyomiClock => {
        val p1Clock = bc.currentClockFor(P1)
        val p2Clock = bc.currentClockFor(P2)
        Json.obj(
          "running"   -> bc.isRunning,
          "initial"   -> bc.limitSeconds,
          "increment" -> bc.incrementSeconds,
          "p1"        -> p1Clock.time.toSeconds,
          "p2"        -> p2Clock.time.toSeconds,
          "emerg"     -> bc.config.emergSeconds,
          "byoyomi"   -> bc.byoyomiSeconds,
          "periods"   -> bc.periodsTotal,
          "p1Periods" -> p1Clock.periods,
          "p2Periods" -> p2Clock.periods
        )
      }
    }
  }

  implicit val correspondenceWriter: OWrites[CorrespondenceClock] = OWrites { c =>
    Json.obj(
      "daysPerTurn" -> c.daysPerTurn,
      "increment"   -> c.increment,
      "p1"          -> c.p1Time,
      "p2"          -> c.p2Time
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

  implicit val playerIndexWrites: Writes[PlayerIndex] = Writes { c =>
    JsString(c.name)
  }

  implicit val fenWrites: Writes[FEN] = Writes { f =>
    JsString(f.value)
  }
}
