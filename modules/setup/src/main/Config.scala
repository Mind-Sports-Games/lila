package lila.setup

import strategygames.{
  ByoyomiClock,
  ClockConfig,
  FischerClock,
  Game => StratGame,
  GameFamily,
  GameLogic,
  Situation,
  Speed
}
import strategygames.variant.Variant
import strategygames.format.FEN

import lila.game.Game
import lila.lobby.PlayerIndex

private[setup] trait Config {

  // Whether or not to use a clock
  val timeMode: TimeMode

  // Clock time in minutes
  val time: Double

  // Clock increment in seconds
  val increment: Int

  // Clock byoyomi in seconds
  val byoyomi: Int

  // Clock periods
  val periods: Int

  // Correspondence days per turn
  val days: Int

  // Game variant code
  val variant: Variant

  // Creator player playerIndex
  val playerIndex: PlayerIndex

  def isFischer = timeMode == TimeMode.FischerClock
  def isByoyomi = timeMode == TimeMode.ByoyomiClock
  def hasClock  = isFischer || isByoyomi

  lazy val creatorPlayerIndex = playerIndex.resolve

  def makeGame(v: Variant): StratGame =
    StratGame(v.gameLogic, situation = Situation(v.gameLogic, v), clock = makeClock.map(_.toClock))

  def makeGame: StratGame = makeGame(variant)

  def validClock = !hasClock || clockHasTime

  def validSpeed(isBot: Boolean) =
    !isBot || makeClock.fold(true) { c =>
      Speed(c) >= Speed.Bullet
    }

  def clockHasFischerTime = isFischer && time + increment > 0
  def clockHasByoyomiTime = isByoyomi && time + increment + byoyomi > 0
  def clockHasTime        = clockHasFischerTime || clockHasByoyomiTime

  def makeClock = hasClock option justMakeClock

  protected def justMakeClock: ClockConfig =
    if (isByoyomi)
      ByoyomiClock.Config(
        (time * 60).toInt,
        if (clockHasByoyomiTime) increment else 0,
        if (clockHasByoyomiTime) byoyomi else 10,
        periods
      )
    else
      FischerClock.Config((time * 60).toInt, if (clockHasFischerTime) increment else 1)

  def makeDaysPerTurn: Option[Int] = (timeMode == TimeMode.Correspondence) option days
}

trait Positional { self: Config =>

  import strategygames.format.Forsyth;
  import strategygames.format.Forsyth.SituationPlus

  def fen: Option[FEN]
  def fenVariant: Option[Variant]

  def strictFen: Boolean

  lazy val validFen = variant.gameLogic match {
    //TODO: LOA defaults here, perhaps want to add LOA fromPosition
    case GameLogic.Chess() =>
      variant != strategygames.chess.variant.FromPosition || {
        fen exists { f =>
          (Forsyth.<<<(variant.gameLogic, f)).exists(_.situation playable strictFen)
        }
      }
    case GameLogic.Draughts() =>
      !(variant.fromPosition && Config
        .fenVariants(GameFamily.Draughts().id)
        .contains((fenVariant | Variant.libStandard(GameLogic.Draughts())).id)) || {
        fen ?? { f =>
          ~Forsyth
            .<<<@(variant.gameLogic, fenVariant | Variant.libStandard(GameLogic.Draughts()), f)
            .map(_.situation playable strictFen)
        }
      }
    case GameLogic.FairySF()      => true //no fromPosition yet
    case GameLogic.Samurai()      => true //no fromPosition yet
    case GameLogic.Togyzkumalak() => true //no fromPosition yet
    case _ =>
      fen exists { f =>
        (Forsyth.<<<(variant.gameLogic, f)).exists(_.situation playable strictFen)
      }
  }

  lazy val validKingCount = variant.gameLogic match {
    case GameLogic.Draughts() =>
      !(variant.fromPosition && Config
        .fenVariants(GameFamily.Draughts().id)
        .contains((fenVariant | Variant.libStandard(GameLogic.Draughts())).id)) || {
        fen ?? { f =>
          strategygames.draughts.format.Forsyth.countKings(
            strategygames.draughts.format.FEN(f.value)
          ) <= 30
        }
      }
    case _ => false
  }

  def fenGame(builder: StratGame => Game): Game = {
    val baseState = fen ifTrue (variant.fromPosition) flatMap {
      Forsyth.<<<@(variant.gameLogic, Variant.libFromPosition(variant.gameLogic), _)
    }
    val (chessGame, state) = baseState.fold(makeGame -> none[SituationPlus]) {
      case sit @ SituationPlus(s, _) =>
        val game = StratGame(
          s.gameLogic,
          situation = s,
          turns = sit.turns,
          startedAtTurn = sit.turns,
          startPlayer = s.player,
          clock = makeClock.map(_.toClock)
        )
        if (Forsyth.>>(s.gameLogic, game).initial)
          makeGame(Variant.libStandard(s.gameLogic)) -> none
        else game                                    -> baseState
    }
    val game = builder(chessGame)
    state.fold(game) { case sit @ SituationPlus(s, _) =>
      game.copy(
        chess = game.chess.copy(
          situation = game.situation.copy(
            board = game.board.copy(
              history = s.board.history,
              variant = Variant.libFromPosition(s.board.variant.gameLogic)
            )
          ),
          turns = sit.turns
        )
      )
    }
  }
}

object Config extends BaseConfig

trait BaseConfig {
  val gameFamilys = GameFamily.all.map(_.id)

  val baseVariants = GameFamily.all.map(gf => (gf.id, gf.variants.filter(_.baseVariant).map(_.id))).toMap

  val defaultVariants = GameFamily.all.map(gf => (gf.id, gf.defaultVariant)).toMap

  val variantDefaultStrat = Variant.Chess(strategygames.chess.variant.Standard)

  val variantsWithFen = GameFamily.all
    .map(gf =>
      (
        gf.id,
        (
          gf.variants.filter(_.baseVariant) :::
            gf.variants.filter(_.fromPositionVariant)
        ).map(_.id)
      )
    )
    .toMap

  //concat ensures ordering that FromPosition is the last element
  val fishnetVariants = GameFamily.all
    .map(gf =>
      (
        gf.id,
        (
          gf.variants.filter(v => v.hasFishnet && !v.fromPositionVariant) :::
            gf.variants.filter(_.fromPositionVariant)
        ).map(_.id)
      )
    )
    .toMap

  val variantsWithVariants =
    GameFamily.all.map(gf => (gf.id, gf.variants.filter(!_.fromPositionVariant).map(_.id))).toMap

  //concat ensures ordering that FromPosition is the last element
  val variantsWithFenAndVariants = GameFamily.all
    .map(gf =>
      (
        gf.id,
        (
          gf.variants.filter(!_.fromPositionVariant) :::
            gf.variants.filter(_.fromPositionVariant)
        ).map(_.id)
      )
    )
    .toMap

  val fenVariants = GameFamily.all
    .map(gf => (gf.id, (gf.variants.filter(v => v.baseVariant || v.fenVariant)).map(_.id)))
    .toMap

  val boardApiVariants =
    GameFamily.all.map(gf => (gf.id, gf.variants.filter(!_.fromPositionVariant).map(_.key))).toMap

  val speeds = Speed.all.map(_.id)

  private val timeMin             = 0
  private val timeMax             = 180
  private val acceptableFractions = Set(1 / 4d, 1 / 2d, 3 / 4d, 3 / 2d)
  def validateTime(t: Double) =
    t >= timeMin && t <= timeMax && (t.isWhole || acceptableFractions(t))

  private val incrementMin      = 0
  private val incrementMax      = 180
  def validateIncrement(i: Int) = i >= incrementMin && i <= incrementMax

  private val byoyomiMin      = 0
  private val byoyomiMax      = 180
  def validateByoyomi(i: Int) = i >= byoyomiMin && i <= byoyomiMax

  private val periodsMin      = 0
  private val periodsMax      = 5
  def validatePeriods(i: Int) = i >= periodsMin && i <= periodsMax
}
