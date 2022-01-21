package lila.tv

import lila.common.LightUser
import lila.game.{ Game, GameRepo, Pov }
import lila.hub.Trouper

final class Tv(
    gameRepo: GameRepo,
    trouper: Trouper,
    gameProxyRepo: lila.round.GameProxyRepo
)(implicit ec: scala.concurrent.ExecutionContext) {

  import Tv._
  import ChannelTrouper._

  private def roundProxyGame = gameProxyRepo.game _

  def getGame(channel: Tv.Channel): Fu[Option[Game]] =
    trouper.ask[Option[Game.ID]](TvTrouper.GetGameId(channel, _)) flatMap { _ ?? roundProxyGame }

  def getGameAndHistory(channel: Tv.Channel): Fu[Option[(Game, List[Pov])]] =
    trouper.ask[GameIdAndHistory](TvTrouper.GetGameIdAndHistory(channel, _)) flatMap {
      case GameIdAndHistory(gameId, historyIds) =>
        for {
          game <- gameId ?? roundProxyGame
          games <-
            historyIds
              .map { id =>
                roundProxyGame(id) orElse gameRepo.game(id)
              }
              .sequenceFu
              .dmap(_.flatten)
          history = games map Pov.naturalOrientation
        } yield game map (_ -> history)
    }

  def getGames(channel: Tv.Channel, max: Int): Fu[List[Game]] =
    trouper.ask[List[Game.ID]](TvTrouper.GetGameIds(channel, max, _)) flatMap {
      _.map(roundProxyGame).sequenceFu.map(_.flatten)
    }

  def getBestGame = getGame(Tv.Channel.Best) orElse gameRepo.random

  def getBestAndHistory = getGameAndHistory(Tv.Channel.Best)

  def getChampions: Fu[Champions] =
    trouper.ask[Champions](TvTrouper.GetChampions.apply)
}

object Tv {
  import strategygames.chess.{ variant => CV }
  import strategygames.draughts.{ variant => DV }
  import strategygames.fairysf.{ variant => FV }
  import strategygames.{ Speed => S, GameFamily }

  case class Champion(user: LightUser, rating: Int, gameId: Game.ID)
  case class Champions(channels: Map[Channel, Champion]) {
    def get = channels.get _
  }

  private[tv] case class Candidate(game: Game, hasBot: Boolean)
  private[tv] def toCandidate(lightUser: LightUser.GetterSync)(game: Game) =
    Tv.Candidate(
      game = game,
      hasBot = game.userIds.exists { userId =>
        lightUser(userId).exists(_.isBot)
      }
    )

  sealed abstract class Channel(
      val name: String,
      val icon: String,
      val secondsSinceLastMove: Int,
      filters: Seq[Candidate => Boolean]
  ) {
    def isFresh(g: Game): Boolean     = fresh(secondsSinceLastMove, g)
    def filter(c: Candidate): Boolean = filters.forall { _(c) } && isFresh(c.game)
    val key                           = s"${toString.head.toLower}${toString.drop(1)}"
  }
  object Channel {
    case object Best
        extends Channel(
          name = "Top Rated",
          icon = "C",
          secondsSinceLastMove = freshBlitz,
          filters = Seq(rated(1250), noBot)
        )
    case object ChessFamily
        extends Channel(
          name = "All Chess",
          icon = CV.Standard.perfIcon.toString,
          secondsSinceLastMove = freshBlitz,
          filters = Seq(anyVariant(CV.Variant.all.filter(v => v.gameFamily == GameFamily.Chess() ).map(_.key)), noBot)
        )     
    case object DraughtsFamily
        extends Channel(
          name = "All Draughts",
          icon = DV.Standard.perfIcon.toString,
          secondsSinceLastMove = freshBlitz,
          filters = Seq(anyVariant(DV.Variant.all.map(_.key)), noBot)
        )
    case object LinesOfActionFamily
        extends Channel(
          name = "All Lines of Action",
          icon = CV.LinesOfAction.perfIcon.toString,
          secondsSinceLastMove = freshBlitz,
          filters = Seq(anyVariant(CV.Variant.all.filter(v => v.gameFamily == GameFamily.LinesOfAction() ).map(_.key)), noBot)
        )
    case object ShogiFamily
        extends Channel(
          name = "All Shogi",
          icon = FV.Shogi.perfIcon.toString,
          secondsSinceLastMove = freshBlitz,
          filters = Seq(anyVariant(FV.Variant.all.filter(v => v.gameFamily == GameFamily.Shogi() ).map(_.key)), noBot)
        )      
    case object XiangqiFamily
        extends Channel(
          name = "All Xiangqi",
          icon = FV.Xiangqi.perfIcon.toString,
          secondsSinceLastMove = freshBlitz,
          filters = Seq(anyVariant(FV.Variant.all.filter(v => v.gameFamily == GameFamily.Xiangqi() ).map(_.key)), noBot)
        )  
    case object Bullet
        extends Channel(
          name = S.Bullet.name,
          icon = S.Bullet.perfIcon.toString,
          secondsSinceLastMove = 35,
          //filters = Seq(speed(S.Bullet), rated(2000), standard, noBot)
          filters = Seq(speed(S.Bullet), standard, noBot)
        )
    case object Blitz
        extends Channel(
          name = S.Blitz.name,
          icon = S.Blitz.perfIcon.toString,
          secondsSinceLastMove = freshBlitz,
          //filters = Seq(speed(S.Blitz), rated(2000), standard, noBot)
          filters = Seq(speed(S.Blitz), standard, noBot)
        )
    case object Rapid
        extends Channel(
          name = S.Rapid.name,
          icon = S.Rapid.perfIcon.toString,
          secondsSinceLastMove = freshRapid,
          //filters = Seq(speed(S.Rapid), rated(1800), standard, noBot)
          filters = Seq(speed(S.Rapid), standard, noBot)
        )
    case object Classical
        extends Channel(
          name = S.Classical.name,
          icon = S.Classical.perfIcon.toString,
          secondsSinceLastMove = 60 * 8,
          //filters = Seq(speed(S.Classical), rated(1650), standard, noBot)
          filters = Seq(speed(S.Classical), standard, noBot)
        )
    case object Chess960
        extends Channel(
          name = CV.Chess960.name,
          icon = CV.Chess960.perfIcon.toString,
          secondsSinceLastMove = freshBlitz,
          filters = Seq(variant(CV.Chess960.key), noBot)
        )
    case object KingOfTheHill
        extends Channel(
          name = CV.KingOfTheHill.name,
          icon = CV.KingOfTheHill.perfIcon.toString,
          secondsSinceLastMove = freshBlitz,
          filters = Seq(variant(CV.KingOfTheHill.key), noBot)
        )
    case object ThreeCheck
        extends Channel(
          name = CV.ThreeCheck.name,
          icon = CV.ThreeCheck.perfIcon.toString,
          secondsSinceLastMove = freshBlitz,
          filters = Seq(variant(CV.ThreeCheck.key), noBot)
        )
    case object FiveCheck
        extends Channel(
          name = CV.FiveCheck.name,
          icon = CV.FiveCheck.perfIcon.toString,
          secondsSinceLastMove = freshBlitz,
          filters = Seq(variant(CV.FiveCheck.key), noBot)
        )
    case object Antichess
        extends Channel(
          name = CV.Antichess.name,
          icon = CV.Antichess.perfIcon.toString,
          secondsSinceLastMove = freshBlitz,
          filters = Seq(variant(CV.Antichess.key), noBot)
        )
    case object Atomic
        extends Channel(
          name = CV.Atomic.name,
          icon = CV.Atomic.perfIcon.toString,
          secondsSinceLastMove = freshBlitz,
          filters = Seq(variant(CV.Atomic.key), noBot)
        )
    case object Horde
        extends Channel(
          name = CV.Horde.name,
          icon = CV.Horde.perfIcon.toString,
          secondsSinceLastMove = freshBlitz,
          filters = Seq(variant(CV.Horde.key), noBot)
        )
    case object RacingKings
        extends Channel(
          name = CV.RacingKings.name,
          icon = CV.RacingKings.perfIcon.toString,
          secondsSinceLastMove = freshBlitz,
          filters = Seq(variant(CV.RacingKings.key), noBot)
        )
    case object Crazyhouse
        extends Channel(
          name = CV.Crazyhouse.name,
          icon = CV.Crazyhouse.perfIcon.toString,
          secondsSinceLastMove = freshBlitz,
          filters = Seq(variant(CV.Crazyhouse.key), noBot)
        )
    case object NoCastling
        extends Channel(
          name = CV.NoCastling.name,
          icon = CV.NoCastling.perfIcon.toString,
          secondsSinceLastMove = freshBlitz,
          filters = Seq(variant(CV.NoCastling.key), noBot)
        )   
    case object UltraBullet
        extends Channel(
          name = S.UltraBullet.name,
          icon = S.UltraBullet.perfIcon.toString,
          secondsSinceLastMove = 20,
          //filters = Seq(speed(S.UltraBullet), rated(1600), standard, noBot)
          filters = Seq(speed(S.UltraBullet), standard, noBot)
        )
    case object LinesOfAction
        extends Channel(
          name = CV.LinesOfAction.name,
          icon = CV.LinesOfAction.perfIcon.toString,
          secondsSinceLastMove = freshBlitz,
          filters = Seq(variant(CV.LinesOfAction.key), noBot)
        ) 
    case object ScrambledEggs
        extends Channel(
          name = CV.ScrambledEggs.name,
          icon = CV.ScrambledEggs.perfIcon.toString,
          secondsSinceLastMove = freshBlitz,
          filters = Seq(variant(CV.ScrambledEggs.key), noBot)
        )
    case object International
        extends Channel(
          name = DV.Standard.name,
          icon = DV.Standard.perfIcon.toString,
          secondsSinceLastMove = freshBlitz,
          filters = Seq(variant(DV.Standard.key), noBot)
        )
    case object Shogi
        extends Channel(
          name = FV.Shogi.name,
          icon = FV.Shogi.perfIcon.toString,
          secondsSinceLastMove = freshBlitz,
          filters = Seq(variant(FV.Shogi.key), noBot)
        )
    case object MiniShogi
        extends Channel(
          name = FV.MiniShogi.name,
          icon = FV.MiniShogi.perfIcon.toString,
          secondsSinceLastMove = freshBlitz,
          filters = Seq(variant(FV.MiniShogi.key), noBot)
        )
    case object Xiangqi
        extends Channel(
          name = FV.Xiangqi.name,
          icon = FV.Xiangqi.perfIcon.toString,
          secondsSinceLastMove = freshBlitz,
          filters = Seq(variant(FV.Xiangqi.key), noBot)
        )
    case object MiniXiangqi
        extends Channel(
          name = FV.MiniXiangqi.name,
          icon = FV.MiniXiangqi.perfIcon.toString,
          secondsSinceLastMove = freshBlitz,
          filters = Seq(variant(FV.MiniXiangqi.key), noBot)
        )
    case object Bot
        extends Channel(
          name = "Bot",
          icon = "n",
          secondsSinceLastMove = freshBlitz,
          filters = Seq(hasBot)
        )
    case object Computer
        extends Channel(
          name = "Computer",
          icon = "n",
          secondsSinceLastMove = freshBlitz,
          filters = Seq(computerFromInitialPosition)
        )
    val all = List(
      Best,
      ChessFamily,
      DraughtsFamily,
      LinesOfActionFamily,
      ShogiFamily,
      XiangqiFamily,
      Bullet,
      Blitz,
      Rapid,
      Classical,
      Crazyhouse,
      Chess960,
      KingOfTheHill,
      ThreeCheck,
      FiveCheck,
      Antichess,
      Atomic,
      Horde,
      RacingKings,
      NoCastling,
      UltraBullet,
      International, //add other draughts
      LinesOfAction,
      ScrambledEggs,
      Shogi,
      MiniShogi,
      Xiangqi,
      MiniXiangqi,
      Bot,
      Computer
    )
    val byKey = all.map { c =>
      c.key -> c
    }.toMap

    val toDisplay = List(
      Best,
      ChessFamily,
      DraughtsFamily,
      LinesOfActionFamily,
      ShogiFamily,
      XiangqiFamily,
      Bot,
      Computer
    )
    val toSelect = all.filter(g => !toDisplay.contains(g))
  }

  private def rated(min: Int)                           = (c: Candidate) => c.game.rated && hasMinRating(c.game, min)
  private def speed(speed: strategygames.Speed)         = (c: Candidate) => c.game.speed == speed
  private def variant(variantKey: String)               = (c: Candidate) => c.game.variant.key == variantKey
  private def anyVariant(variantKeyList: List[String])  = (c: Candidate) => variantKeyList.contains(c.game.variant.key)
  private val standard                                  = variant("standard")
  private val freshBlitz                                = 60 * 2
  private val freshRapid                                = 60 * 5
  private def computerFromInitialPosition(c: Candidate) = c.game.hasAi && !c.game.fromPosition
  private def hasBot(c: Candidate)                      = c.hasBot
  private def noBot(c: Candidate)                       = !c.hasBot

  private def fresh(seconds: Int, game: Game): Boolean = {
    game.isBeingPlayed && !game.olderThan(seconds)
  } || {
    game.finished && !game.olderThan(7)
  } // rematch time
  private def hasMinRating(g: Game, min: Int) = g.players.exists(_.rating.exists(_ >= min))

  private[tv] val titleScores = Map(
    "GM"  -> 500,
    "WGM" -> 500,
    "IM"  -> 300,
    "WIM" -> 300,
    "FM"  -> 200,
    "WFM" -> 200,
    "NM"  -> 100,
    "CM"  -> 100,
    "WCM" -> 100,
    "WNM" -> 100
  )
}
