package lila.game

import play.api.libs.json._

import strategygames.format.{ FEN, Forsyth }
import strategygames.opening.FullOpening
import strategygames.{
  P2,
  ByoyomiClock,
  ClockBase,
  Player => PlayerIndex,
  Division,
  Clock,
  GameLogic,
  Pocket,
  PocketData,
  Role,
  Status,
  P1
}
import strategygames.variant.Variant
import lila.common.Json.jodaWrites
import lila.common.Json._
import lila.common.LightUser
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
        "initialFen"    -> (initialFen | game.variant.initialFen),
        "fen"           -> (Forsyth.>>(game.variant.gameLogic, game.stratGame)),
        "player"        -> game.activePlayerIndex,
        "plies"         -> game.plies,
        "turns"         -> game.turnCount,
        "startedAtTurn" -> game.stratGame.startedAtTurn,
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
      .add("lastMove" -> game.lastActionKeys)
      .add("check" -> game.situation.checkSquare.map(_.key))
      .add("rematch" -> rematches.of(game.id))
      .add("canOfferDraw" -> game.variant.canOfferDraw)
      .add("drawOffers" -> (!game.drawOffers.isEmpty).option(game.drawOffers.normalizedTurns))
      .add("canDoPassAction" -> (game.situation.passes.size > 0))
      .add("multiMatch" -> game.metadata.multiMatchGameNr.map { index =>
        Json
          .obj("index" -> index)
          .add("gameId" -> game.metadata.multiMatchGameId.filter("*" !=))
      })

  def boardSize(variant: Variant) = variant match {
    case Variant.Draughts(v) =>
      Some(
        Json.obj(
          "size" -> Json.arr(v.boardSize.width, v.boardSize.height),
          "key"  -> v.boardSize.key
        )
      )
    case _ => None
  }

  def ownerPreview(pov: Pov)(lightUserSync: LightUser.GetterSync) =
    Json
      .obj(
        "fullId"      -> pov.fullId,
        "gameId"      -> pov.gameId,
        "fen"         -> Forsyth.exportBoard(pov.game.variant.gameLogic, pov.game.board),
        "playerIndex" -> pov.playerIndex.name,
        "lastMove"    -> ~pov.game.lastActionKeys,
        "source"      -> pov.game.source,
        "status"      -> pov.game.status,
        "variant" -> Json.obj(
          "gameLogic" -> Json.obj(
            "id"   -> pov.game.variant.gameLogic.id,
            "name" -> pov.game.variant.gameLogic.name
          ),
          "gameFamily" -> pov.game.variant.gameFamily.key,
          "key"        -> pov.game.variant.key,
          "name"       -> VariantKeys.variantName(pov.game.variant),
          "boardSize"  -> boardSize(pov.game.variant)
        ),
        "speed"    -> pov.game.speed.key,
        "perf"     -> lila.game.PerfPicker.key(pov.game),
        "rated"    -> pov.game.rated,
        "hasMoved" -> pov.hasMoved,
        "opponent" -> Json
          .obj(
            "id" -> pov.opponent.userId,
            "username" -> lila.game.Namer
              .playerTextBlocking(pov.opponent, withRating = false)(lightUserSync)
          )
          .add("rating" -> pov.opponent.rating)
          .add("ai" -> pov.opponent.aiLevel),
        "isMyTurn" -> pov.isMyTurn
      )
      .add("secondsLeft" -> pov.remainingSeconds)
      .add("tournamentId" -> pov.game.tournamentId)
      .add("swissId" -> pov.game.tournamentId)
      .add("winner" -> pov.game.winnerPlayerIndex)
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
        .storable(v.roles.headOption.map(_.gameLogic).getOrElse(GameLogic.Chess()))
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
      case Variant.Go(goVariant) =>
        Json.obj(
          "key"       -> v.key,
          "name"      -> VariantKeys.variantName(v),
          "short"     -> VariantKeys.variantShortName(v),
          "lib"       -> v.gameLogic.id,
          "boardSize" -> goVariant.boardSize
        )
      case Variant.Backgammon(backgammonVariant) =>
        Json.obj(
          "key"       -> v.key,
          "name"      -> VariantKeys.variantName(v),
          "short"     -> VariantKeys.variantShortName(v),
          "lib"       -> v.gameLogic.id,
          "boardSize" -> backgammonVariant.boardSize
        )
      case Variant.Abalone(abaloneVariant) =>
        Json.obj(
          "key"       -> v.key,
          "name"      -> VariantKeys.variantName(v),
          "short"     -> VariantKeys.variantShortName(v),
          "lib"       -> v.gameLogic.id,
          "boardSize" -> abaloneVariant.boardSize
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

  implicit val boardSizeGoWriter: Writes[strategygames.go.Board.BoardSize] =
    Writes[strategygames.go.Board.BoardSize] { b =>
      Json.obj(
        "width"  -> b.width,
        "height" -> b.height
      )
    }

  implicit val boardSizeBackgammonWriter: Writes[strategygames.backgammon.Board.BoardSize] =
    Writes[strategygames.backgammon.Board.BoardSize] { b =>
      Json.obj(
        "width"  -> b.width,
        "height" -> b.height
      )
    }

  //TODO: Abalone do we need to do anything different because of Hex boards here?
  implicit val boardSizeAbaloneWriter: Writes[strategygames.abalone.Board.BoardSize] =
    Writes[strategygames.abalone.Board.BoardSize] { b =>
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

  private def baseClockJson(fc: Clock) =
    Json.obj(
      "running"   -> fc.isRunning,
      "p1"        -> fc.remainingTime(P1).toSeconds,
      "p2"        -> fc.remainingTime(P2).toSeconds,
      "p1Pending" -> (fc.pending(P1) + fc.completedActionsOfTurnTime(P1)).toSeconds,
      "p2Pending" -> (fc.pending(P2) + fc.completedActionsOfTurnTime(P2)).toSeconds,
      "emerg"     -> fc.config.emergSeconds
    )

  implicit val clockWriter: OWrites[ClockBase] = OWrites { c =>
    c match {
      case fc: Clock =>
        fc.config match {
          case fConfig: Clock.Config =>
            Json.obj(
              "initial"   -> fConfig.limitSeconds,
              "increment" -> fConfig.incrementSeconds
            ) ++ baseClockJson(fc)
          case bConfig: Clock.BronsteinConfig =>
            Json.obj(
              "initial"   -> bConfig.limitSeconds,
              "delay"     -> bConfig.delaySeconds,
              "delayType" -> "bronstein"
            ) ++ baseClockJson(fc)
          case udConfig: Clock.SimpleDelayConfig =>
            Json.obj(
              "initial"   -> udConfig.limitSeconds,
              "delay"     -> udConfig.delaySeconds,
              "delayType" -> "usdelay"
            ) ++ baseClockJson(fc)
          case _: ByoyomiClock.Config => Json.obj() // TODO: this is annoying
        }
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
