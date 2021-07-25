package lila.setup

import strategygames.{ Clock, Game => StratGame, GameLib, Situation, Speed }
import strategygames.variant.Variant
import strategygames.format.FEN
import strategygames.chess.{ Game => ChessGame }
import strategygames.chess.variant.{ FromPosition }

import lila.game.Game
import lila.lobby.Color

private[setup] trait Config {

  // Whether or not to use a clock
  val timeMode: TimeMode

  // Clock time in minutes
  val time: Double

  // Clock increment in seconds
  val increment: Int

  // Correspondence days per turn
  val days: Int

  // Game variant code
  val variant: Variant

  // Creator player color
  val color: Color

  def hasClock = timeMode == TimeMode.RealTime

  lazy val creatorColor = color.resolve

  def makeGame(v: Variant): StratGame =
    StratGame(v.gameLib, situation = Situation(v.gameLib, v), clock = makeClock.map(_.toClock))

  def makeGame: StratGame = makeGame(variant)

  def validClock = !hasClock || clockHasTime

  def validSpeed(isBot: Boolean) =
    !isBot || makeClock.fold(true) { c =>
      Speed(c) >= Speed.Bullet
    }

  def clockHasTime = time + increment > 0

  def makeClock = hasClock option justMakeClock

  protected def justMakeClock =
    Clock.Config((time * 60).toInt, if (clockHasTime) increment else 1)

  def makeDaysPerTurn: Option[Int] = (timeMode == TimeMode.Correspondence) option days
}

trait Positional { self: Config =>

  import strategygames.format.Forsyth;
  import strategygames.format.Forsyth.SituationPlus

  val lib = GameLib.Chess()

  def fen: Option[FEN]

  def strictFen: Boolean

  lazy val validFen = variant != FromPosition || {
    fen exists { f =>
      (Forsyth.<<<(lib, f)).exists(_.situation playable strictFen)
    }
  }

  def fenGame(builder: StratGame => Game): Game = {
    val baseState = fen ifTrue (variant.fromPosition) flatMap {
      Forsyth.<<<@(lib, Variant.wrap(FromPosition), _)
    }
    val (chessGame, state) = baseState.fold(makeGame -> none[SituationPlus]) {
      case sit @ SituationPlus(s, _) =>
        val game = StratGame(
          s.gameLib,
          situation = s,
          turns = sit.turns,
          startedAtTurn = sit.turns,
          clock = makeClock.map(_.toClock)
        )
        if (Forsyth.>>(lib, game).initial) makeGame(Variant.wrap(strategygames.chess.variant.Standard)) -> none
        else game                                                      -> baseState
    }
    val game = builder(chessGame)
    state.fold(game) { case sit @ SituationPlus(s, _) =>
      game.copy(
        chess = game.chess.copy(
          situation = game.situation.copy(
            board = game.board.copy(
              history = s.board.history,
              variant = Variant.wrap(FromPosition)
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
  val gameLibs       = List(strategygames.GameLib.Chess().id, strategygames.GameLib.Draughts().id)
  val variants       = List(strategygames.chess.variant.Standard.id, strategygames.chess.variant.Chess960.id)
  val variantDefault = strategygames.chess.variant.Standard
  val variantDefaultStrat = Variant.Chess(strategygames.chess.variant.Standard)

  val variantsWithFen = variants :+ FromPosition.id
  val aiVariants = variants :+
    strategygames.chess.variant.Crazyhouse.id :+
    strategygames.chess.variant.KingOfTheHill.id :+
    strategygames.chess.variant.ThreeCheck.id :+
    strategygames.chess.variant.Antichess.id :+
    strategygames.chess.variant.Atomic.id :+
    strategygames.chess.variant.Horde.id :+
    strategygames.chess.variant.RacingKings.id :+
    //chess.variant.LinesOfAction.id :+
    strategygames.chess.variant.FromPosition.id
  val variantsWithVariants =
    variants :+
      strategygames.chess.variant.Crazyhouse.id :+
      strategygames.chess.variant.KingOfTheHill.id :+
      strategygames.chess.variant.ThreeCheck.id :+
      strategygames.chess.variant.Antichess.id :+
      strategygames.chess.variant.Atomic.id :+
      strategygames.chess.variant.Horde.id :+
      strategygames.chess.variant.RacingKings.id :+
      strategygames.chess.variant.LinesOfAction.id
  val variantsWithFenAndVariants =
    variantsWithVariants :+ FromPosition.id

  val speeds = Speed.all.map(_.id)

  private val timeMin             = 0
  private val timeMax             = 180
  private val acceptableFractions = Set(1 / 4d, 1 / 2d, 3 / 4d, 3 / 2d)
  def validateTime(t: Double) =
    t >= timeMin && t <= timeMax && (t.isWhole || acceptableFractions(t))

  private val incrementMin      = 0
  private val incrementMax      = 180
  def validateIncrement(i: Int) = i >= incrementMin && i <= incrementMax
}
