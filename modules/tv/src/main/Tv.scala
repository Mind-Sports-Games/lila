package lila.tv

import lila.common.LightUser
import lila.game.{ Game, GameRepo, Pov }
import lila.hub.Trouper
import lila.i18n.VariantKeys
import strategygames.variant.Variant
import strategygames.{ GameFamily, GameGroup, GameLogic }
import cats.implicits._

final class Tv(
    gameRepo: GameRepo,
    trouper: Trouper,
    gameProxyRepo: lila.round.GameProxyRepo,
    lightUserSync: LightUser.GetterSync
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

  def getBestGame = getGame(Tv.Channel.AllGames) orElse gameRepo.random

  def getBestAndHistory = getGameAndHistory(Tv.Channel.AllGames)

  def getChampions: Fu[Champions] =
    trouper.ask[Champions](TvTrouper.GetChampions.apply)

  def getCorrespondenceGames: Fu[List[Game]] = {
    gameRepo.playingCorrespondenceNoAi flatMap {
      _.map(gameRepo.game(_)).sequenceFu.map(_.flatten)
    }
  }

  def getNonLiveCorrespondenceGamesOfChannel(
      channel: Tv.Channel,
      cGames: List[Game],
      max: Int,
      lGames: List[Game]
  ): List[Game] = {
    cGames
      .filter(g => channel.filter(Candidate(g, false)))
      .filter(g => !lGames.map(_.id).contains(g.id))
      .sortBy(g => -(~g.averageUsersRating))
      .take(max)
  }

  def getCorrespondenceChampions(games: List[Game]): Champions = {
    var champions = Map[Channel, Champion]()
    Tv.Channel.all.map(channel => {
      games.filter(g => channel.filter(Candidate(g, false))).map { game =>
        val player = game.players.sortBy { p =>
          ~p.rating + ~p.userId.flatMap(lightUserSync).flatMap(_.title).flatMap(Tv.titleScores.get)
        }.lastOption | game.player(game.naturalOrientation)
        val user = player.userId flatMap lightUserSync
        (user, player.rating) mapN { (u, r) =>
          champions += (channel -> Champion(u, r, game.id))
        }
      }
    })
    Champions(champions)
  }
}

object Tv {
  import strategygames.chess.{ variant => CV }
  import strategygames.draughts.{ variant => DV }
  import strategygames.fairysf.{ variant => FV }
  import strategygames.samurai.{ variant => MSV }
  import strategygames.togyzkumalak.{ variant => MTV }
  import strategygames.go.{ variant => GV }
  import strategygames.backgammon.{ variant => BV }
  import strategygames.abalone.{ variant => AV }
  import strategygames.{ Speed => S, GameFamily }

  case class Champion(user: LightUser, rating: Int, gameId: Game.ID)
  case class Champions(channels: Map[Channel, Champion]) {
    def get = channels.get _
    def combineWithAndFavour(overWritingChampions: Champions) = {
      Champions(channels ++ overWritingChampions.channels)
    }
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
      filters: Seq[Candidate => Boolean],
      val familyChannel: Boolean,
      val gameFamily: String
  ) {
    def isFresh(g: Game): Boolean       = fresh(secondsSinceLastMove, g)
    def isOngoingGame(g: Game): Boolean = onGoingGame(g)
    //def filter(c: Candidate): Boolean = filters.forall { _(c) } && isFresh(c.game)
    def filter(c: Candidate): Boolean = filters.forall { _(c) } && isOngoingGame(c.game)
    val key                           = s"${toString.head.toLower}${toString.drop(1)}"
  }
  object Channel {
    case object AllGames
        extends Channel(
          name = "All Games",
          icon = "C",
          secondsSinceLastMove = freshBlitz,
          filters = Seq(),
          familyChannel = true,
          gameFamily = "other"
        )
    // case object Best
    //     extends Channel(
    //       name = "Top Rated",
    //       icon = "C",
    //       secondsSinceLastMove = freshBlitz,
    //       filters = Seq(rated(1250), noBot),
    //       familyChannel = true,
    //       gameFamily = "other"
    //     )
    case object ChessFamily
        extends Channel(
          name = s"All ${VariantKeys.gameFamilyName(GameFamily.Chess())}",
          icon = CV.Standard.perfIcon.toString,
          secondsSinceLastMove = freshBlitz,
          filters = Seq(anyVariant(Variant.all.filter(v => v.gameFamily == GameFamily.Chess())), noBot),
          familyChannel = true,
          gameFamily = "chess"
        )
    case object DraughtsFamily
        extends Channel(
          name = s"All ${VariantKeys.gameFamilyName(GameFamily.Draughts())}",
          icon = DV.Standard.perfIcon.toString,
          secondsSinceLastMove = freshBlitz,
          filters = Seq(anyVariant(Variant.all(GameLogic.Draughts())), noBot),
          familyChannel = true,
          gameFamily = "draughts"
        )
    case object LinesOfActionFamily
        extends Channel(
          name = s"All ${VariantKeys.gameFamilyName(GameFamily.LinesOfAction())}",
          icon = CV.LinesOfAction.perfIcon.toString,
          secondsSinceLastMove = freshBlitz,
          filters =
            Seq(anyVariant(Variant.all.filter(v => v.gameFamily == GameFamily.LinesOfAction())), noBot),
          familyChannel = true,
          gameFamily = "loa"
        )
    case object ShogiFamily
        extends Channel(
          name = s"All ${VariantKeys.gameFamilyName(GameFamily.Shogi())}",
          icon = FV.Shogi.perfIcon.toString,
          secondsSinceLastMove = freshBlitz,
          filters = Seq(
            anyVariant(Variant.all(GameLogic.FairySF()).filter(v => v.gameFamily == GameFamily.Shogi())),
            noBot
          ),
          familyChannel = true,
          gameFamily = "shogi"
        )
    case object XiangqiFamily
        extends Channel(
          name = s"All ${VariantKeys.gameFamilyName(GameFamily.Xiangqi())}",
          icon = FV.Xiangqi.perfIcon.toString,
          secondsSinceLastMove = freshBlitz,
          filters = Seq(
            anyVariant(Variant.all(GameLogic.FairySF()).filter(v => v.gameFamily == GameFamily.Xiangqi())),
            noBot
          ),
          familyChannel = true,
          gameFamily = "xiangqi"
        )
    case object FlipelloFamily
        extends Channel(
          name = s"All ${VariantKeys.gameFamilyName(GameFamily.Flipello())}",
          icon = FV.Flipello.perfIcon.toString,
          secondsSinceLastMove = freshBlitz,
          filters = Seq(
            anyVariant(Variant.all(GameLogic.FairySF()).filter(v => v.gameFamily == GameFamily.Flipello())),
            noBot
          ),
          familyChannel = true,
          gameFamily = "flipello"
        )
    case object MancalaFamily
        extends Channel(
          name = s"All ${GameGroup(7).name}",
          icon = MSV.Oware.perfIcon.toString,
          secondsSinceLastMove = freshBlitz,
          filters = Seq(
            anyVariant(GameGroup(7).variants),
            noBot
          ),
          familyChannel = true,
          gameFamily = "mancala"
        )
    case object GoFamily
        extends Channel(
          name = s"All ${GameGroup(9).name}",
          icon = GV.Go19x19.perfIcon.toString,
          secondsSinceLastMove = freshBlitz,
          filters = Seq(
            anyVariant(GameGroup(9).variants),
            noBot
          ),
          familyChannel = true,
          gameFamily = "go"
        )
    case object BackgammonFamily
        extends Channel(
          name = s"All ${GameGroup(10).name}",
          icon = BV.Backgammon.perfIcon.toString,
          secondsSinceLastMove = freshBlitz,
          filters = Seq(anyVariant(GameGroup(10).variants), noBot),
          familyChannel = true,
          gameFamily = "backgammon"
        )
    case object BreakthroughTroykaFamily
        extends Channel(
          name = s"All ${VariantKeys.gameFamilyName(GameFamily.BreakthroughTroyka())}",
          icon = FV.BreakthroughTroyka.perfIcon.toString,
          secondsSinceLastMove = freshBlitz,
          filters = Seq(
            anyVariant(
              Variant.all(GameLogic.FairySF()).filter(v => v.gameFamily == GameFamily.BreakthroughTroyka())
            ),
            noBot
          ),
          familyChannel = true,
          gameFamily = "breakthroughtroyka"
        )
    case object Bullet
        extends Channel(
          name = S.Bullet.name,
          icon = S.Bullet.perfIcon.toString,
          secondsSinceLastMove = 35,
          //filters = Seq(speed(S.Bullet), rated(2000), standard, noBot)
          filters = Seq(speed(S.Bullet), standard, noBot),
          familyChannel = false,
          gameFamily = "chess"
        )
    case object Blitz
        extends Channel(
          name = S.Blitz.name,
          icon = S.Blitz.perfIcon.toString,
          secondsSinceLastMove = freshBlitz,
          //filters = Seq(speed(S.Blitz), rated(2000), standard, noBot)
          filters = Seq(speed(S.Blitz), standard, noBot),
          familyChannel = false,
          gameFamily = "chess"
        )
    case object Rapid
        extends Channel(
          name = S.Rapid.name,
          icon = S.Rapid.perfIcon.toString,
          secondsSinceLastMove = freshRapid,
          //filters = Seq(speed(S.Rapid), rated(1800), standard, noBot)
          filters = Seq(speed(S.Rapid), standard, noBot),
          familyChannel = false,
          gameFamily = "chess"
        )
    case object Classical
        extends Channel(
          name = S.Classical.name,
          icon = S.Classical.perfIcon.toString,
          secondsSinceLastMove = 60 * 8,
          //filters = Seq(speed(S.Classical), rated(1650), standard, noBot)
          filters = Seq(speed(S.Classical), standard, noBot),
          familyChannel = false,
          gameFamily = "chess"
        )
    case object Chess960
        extends Channel(
          name = VariantKeys.variantName(Variant.wrap(CV.Chess960)),
          icon = CV.Chess960.perfIcon.toString,
          secondsSinceLastMove = freshBlitz,
          filters = Seq(variant(Variant.wrap(CV.Chess960)), noBot),
          familyChannel = false,
          gameFamily = "chess"
        )
    case object KingOfTheHill
        extends Channel(
          name = VariantKeys.variantName(Variant.wrap(CV.KingOfTheHill)),
          icon = CV.KingOfTheHill.perfIcon.toString,
          secondsSinceLastMove = freshBlitz,
          filters = Seq(variant(Variant.wrap(CV.KingOfTheHill)), noBot),
          familyChannel = false,
          gameFamily = "chess"
        )
    case object ThreeCheck
        extends Channel(
          name = VariantKeys.variantName(Variant.wrap(CV.ThreeCheck)),
          icon = CV.ThreeCheck.perfIcon.toString,
          secondsSinceLastMove = freshBlitz,
          filters = Seq(variant(Variant.wrap(CV.ThreeCheck)), noBot),
          familyChannel = false,
          gameFamily = "chess"
        )
    case object FiveCheck
        extends Channel(
          name = VariantKeys.variantName(Variant.wrap(CV.FiveCheck)),
          icon = CV.FiveCheck.perfIcon.toString,
          secondsSinceLastMove = freshBlitz,
          filters = Seq(variant(Variant.wrap(CV.FiveCheck)), noBot),
          familyChannel = false,
          gameFamily = "chess"
        )
    case object Antichess
        extends Channel(
          name = VariantKeys.variantName(Variant.wrap(CV.Antichess)),
          icon = CV.Antichess.perfIcon.toString,
          secondsSinceLastMove = freshBlitz,
          filters = Seq(variant(Variant.wrap(CV.Antichess)), noBot),
          familyChannel = false,
          gameFamily = "chess"
        )
    case object Atomic
        extends Channel(
          name = VariantKeys.variantName(Variant.wrap(CV.Atomic)),
          icon = CV.Atomic.perfIcon.toString,
          secondsSinceLastMove = freshBlitz,
          filters = Seq(variant(Variant.wrap(CV.Atomic)), noBot),
          familyChannel = false,
          gameFamily = "chess"
        )
    case object Horde
        extends Channel(
          name = VariantKeys.variantName(Variant.wrap(CV.Horde)),
          icon = CV.Horde.perfIcon.toString,
          secondsSinceLastMove = freshBlitz,
          filters = Seq(variant(Variant.wrap(CV.Horde)), noBot),
          familyChannel = false,
          gameFamily = "chess"
        )
    case object RacingKings
        extends Channel(
          name = VariantKeys.variantName(Variant.wrap(CV.RacingKings)),
          icon = CV.RacingKings.perfIcon.toString,
          secondsSinceLastMove = freshBlitz,
          filters = Seq(variant(Variant.wrap(CV.RacingKings)), noBot),
          familyChannel = false,
          gameFamily = "chess"
        )
    case object Crazyhouse
        extends Channel(
          name = VariantKeys.variantName(Variant.wrap(CV.Crazyhouse)),
          icon = CV.Crazyhouse.perfIcon.toString,
          secondsSinceLastMove = freshBlitz,
          filters = Seq(variant(Variant.wrap(CV.Crazyhouse)), noBot),
          familyChannel = false,
          gameFamily = "chess"
        )
    case object NoCastling
        extends Channel(
          name = VariantKeys.variantName(Variant.wrap(CV.NoCastling)),
          icon = CV.NoCastling.perfIcon.toString,
          secondsSinceLastMove = freshBlitz,
          filters = Seq(variant(Variant.wrap(CV.NoCastling)), noBot),
          familyChannel = false,
          gameFamily = "chess"
        )
    case object Monster
        extends Channel(
          name = VariantKeys.variantName(Variant.wrap(CV.Monster)),
          icon = CV.Monster.perfIcon.toString,
          secondsSinceLastMove = freshBlitz,
          filters = Seq(variant(Variant.wrap(CV.Monster)), noBot),
          familyChannel = false,
          gameFamily = "chess"
        )
    case object UltraBullet
        extends Channel(
          name = S.UltraBullet.name,
          icon = S.UltraBullet.perfIcon.toString,
          secondsSinceLastMove = 20,
          //filters = Seq(speed(S.UltraBullet), rated(1600), standard, noBot)
          filters = Seq(speed(S.UltraBullet), standard, noBot),
          familyChannel = false,
          gameFamily = "chess"
        )
    case object LinesOfAction
        extends Channel(
          name = VariantKeys.variantName(Variant.wrap(CV.LinesOfAction)),
          icon = CV.LinesOfAction.perfIcon.toString,
          secondsSinceLastMove = freshBlitz,
          filters = Seq(variant(Variant.wrap(CV.LinesOfAction)), noBot),
          familyChannel = false,
          gameFamily = "loa"
        )
    case object ScrambledEggs
        extends Channel(
          name = VariantKeys.variantName(Variant.wrap(CV.ScrambledEggs)),
          icon = CV.ScrambledEggs.perfIcon.toString,
          secondsSinceLastMove = freshBlitz,
          filters = Seq(variant(Variant.wrap(CV.ScrambledEggs)), noBot),
          familyChannel = false,
          gameFamily = "loa"
        )
    case object International
        extends Channel(
          name = VariantKeys.variantName(Variant.wrap(DV.Standard)),
          icon = DV.Standard.perfIcon.toString,
          secondsSinceLastMove = freshBlitz,
          filters = Seq(variant(Variant.wrap(DV.Standard)), noBot),
          familyChannel = false,
          gameFamily = "draughts"
        )
    case object Frisian
        extends Channel(
          name = VariantKeys.variantName(Variant.wrap(DV.Frisian)),
          icon = DV.Frisian.perfIcon.toString,
          secondsSinceLastMove = freshBlitz,
          filters = Seq(variant(Variant.wrap(DV.Frisian)), noBot),
          familyChannel = false,
          gameFamily = "draughts"
        )
    case object Frysk
        extends Channel(
          name = VariantKeys.variantName(Variant.wrap(DV.Frysk)),
          icon = DV.Frysk.perfIcon.toString,
          secondsSinceLastMove = freshBlitz,
          filters = Seq(variant(Variant.wrap(DV.Frysk)), noBot),
          familyChannel = false,
          gameFamily = "draughts"
        )
    case object Antidraughts
        extends Channel(
          name = VariantKeys.variantName(Variant.wrap(DV.Antidraughts)),
          icon = DV.Antidraughts.perfIcon.toString,
          secondsSinceLastMove = freshBlitz,
          filters = Seq(variant(Variant.wrap(DV.Antidraughts)), noBot),
          familyChannel = false,
          gameFamily = "draughts"
        )
    case object Breakthrough
        extends Channel(
          name = VariantKeys.variantName(Variant.wrap(DV.Breakthrough)),
          icon = DV.Breakthrough.perfIcon.toString,
          secondsSinceLastMove = freshBlitz,
          filters = Seq(variant(Variant.wrap(DV.Breakthrough)), noBot),
          familyChannel = false,
          gameFamily = "draughts"
        )
    case object Russian
        extends Channel(
          name = VariantKeys.variantName(Variant.wrap(DV.Russian)),
          icon = DV.Russian.perfIcon.toString,
          secondsSinceLastMove = freshBlitz,
          filters = Seq(variant(Variant.wrap(DV.Russian)), noBot),
          familyChannel = false,
          gameFamily = "draughts"
        )
    case object Brazilian
        extends Channel(
          name = VariantKeys.variantName(Variant.wrap(DV.Brazilian)),
          icon = DV.Brazilian.perfIcon.toString,
          secondsSinceLastMove = freshBlitz,
          filters = Seq(variant(Variant.wrap(DV.Brazilian)), noBot),
          familyChannel = false,
          gameFamily = "draughts"
        )
    case object Pool
        extends Channel(
          name = VariantKeys.variantName(Variant.wrap(DV.Pool)),
          icon = DV.Pool.perfIcon.toString,
          secondsSinceLastMove = freshBlitz,
          filters = Seq(variant(Variant.wrap(DV.Pool)), noBot),
          familyChannel = false,
          gameFamily = "draughts"
        )
    case object Portuguese
        extends Channel(
          name = VariantKeys.variantName(Variant.wrap(DV.Portuguese)),
          icon = DV.Portuguese.perfIcon.toString,
          secondsSinceLastMove = freshBlitz,
          filters = Seq(variant(Variant.wrap(DV.Portuguese)), noBot),
          familyChannel = false,
          gameFamily = "draughts"
        )
    case object English
        extends Channel(
          name = VariantKeys.variantName(Variant.wrap(DV.English)),
          icon = DV.English.perfIcon.toString,
          secondsSinceLastMove = freshBlitz,
          filters = Seq(variant(Variant.wrap(DV.English)), noBot),
          familyChannel = false,
          gameFamily = "draughts"
        )
    case object Shogi
        extends Channel(
          name = VariantKeys.variantName(Variant.wrap(FV.Shogi)),
          icon = FV.Shogi.perfIcon.toString,
          secondsSinceLastMove = freshBlitz,
          filters = Seq(variant(Variant.wrap(FV.Shogi)), noBot),
          familyChannel = false,
          gameFamily = "shogi"
        )
    case object MiniShogi
        extends Channel(
          name = VariantKeys.variantName(Variant.wrap(FV.MiniShogi)),
          icon = FV.MiniShogi.perfIcon.toString,
          secondsSinceLastMove = freshBlitz,
          filters = Seq(variant(Variant.wrap(FV.MiniShogi)), noBot),
          familyChannel = false,
          gameFamily = "shogi"
        )
    case object Xiangqi
        extends Channel(
          name = VariantKeys.variantName(Variant.wrap(FV.Xiangqi)),
          icon = FV.Xiangqi.perfIcon.toString,
          secondsSinceLastMove = freshBlitz,
          filters = Seq(variant(Variant.wrap(FV.Xiangqi)), noBot),
          familyChannel = false,
          gameFamily = "xiangqi"
        )
    case object MiniXiangqi
        extends Channel(
          name = VariantKeys.variantName(Variant.wrap(FV.MiniXiangqi)),
          icon = FV.MiniXiangqi.perfIcon.toString,
          secondsSinceLastMove = freshBlitz,
          filters = Seq(variant(Variant.wrap(FV.MiniXiangqi)), noBot),
          familyChannel = false,
          gameFamily = "xiangqi"
        )
    case object Flipello
        extends Channel(
          name = VariantKeys.variantName(Variant.wrap(FV.Flipello)),
          icon = FV.Flipello.perfIcon.toString,
          secondsSinceLastMove = freshBlitz,
          filters = Seq(variant(Variant.wrap(FV.Flipello)), noBot),
          familyChannel = false,
          gameFamily = "flipello"
        )
    case object Flipello10
        extends Channel(
          name = VariantKeys.variantName(Variant.wrap(FV.Flipello10)),
          icon = FV.Flipello10.perfIcon.toString,
          secondsSinceLastMove = freshBlitz,
          filters = Seq(variant(Variant.wrap(FV.Flipello10)), noBot),
          familyChannel = false,
          gameFamily = "flipello"
        )
    case object Amazons
        extends Channel(
          name = VariantKeys.variantName(Variant.wrap(FV.Amazons)),
          icon = FV.Amazons.perfIcon.toString,
          secondsSinceLastMove = freshBlitz,
          filters = Seq(variant(Variant.wrap(FV.Amazons)), noBot),
          familyChannel = true,
          gameFamily = "amazons"
        )
    case object BreakthroughTroyka
        extends Channel(
          name = VariantKeys.variantName(Variant.wrap(FV.BreakthroughTroyka)),
          icon = FV.BreakthroughTroyka.perfIcon.toString,
          secondsSinceLastMove = freshBlitz,
          filters = Seq(variant(Variant.wrap(FV.BreakthroughTroyka)), noBot),
          familyChannel = false,
          gameFamily = "breakthroughtroyka"
        )

    case object MiniBreakthroughTroyka
        extends Channel(
          name = VariantKeys.variantName(Variant.wrap(FV.MiniBreakthroughTroyka)),
          icon = FV.MiniBreakthroughTroyka.perfIcon.toString,
          secondsSinceLastMove = freshBlitz,
          filters = Seq(variant(Variant.wrap(FV.MiniBreakthroughTroyka)), noBot),
          familyChannel = false,
          gameFamily = "breakthroughtroyka"
        )

    case object Oware
        extends Channel(
          name = VariantKeys.variantName(Variant.wrap(MSV.Oware)),
          icon = MSV.Oware.perfIcon.toString,
          secondsSinceLastMove = freshBlitz,
          filters = Seq(variant(Variant.wrap(MSV.Oware)), noBot),
          familyChannel = false,
          gameFamily = "mancala"
        )
    case object Togyzkumalak
        extends Channel(
          name = VariantKeys.variantName(Variant.wrap(MTV.Togyzkumalak)),
          icon = MTV.Togyzkumalak.perfIcon.toString,
          secondsSinceLastMove = freshBlitz,
          filters = Seq(variant(Variant.wrap(MTV.Togyzkumalak)), noBot),
          familyChannel = false,
          gameFamily = "mancala"
        )
    case object Bestemshe
        extends Channel(
          name = VariantKeys.variantName(Variant.wrap(MTV.Bestemshe)),
          icon = MTV.Bestemshe.perfIcon.toString,
          secondsSinceLastMove = freshBlitz,
          filters = Seq(variant(Variant.wrap(MTV.Bestemshe)), noBot),
          familyChannel = false,
          gameFamily = "mancala"
        )
    case object Go9x9
        extends Channel(
          name = VariantKeys.variantName(Variant.wrap(GV.Go9x9)),
          icon = GV.Go9x9.perfIcon.toString,
          secondsSinceLastMove = freshBlitz,
          filters = Seq(variant(Variant.wrap(GV.Go9x9)), noBot),
          familyChannel = false,
          gameFamily = "go"
        )
    case object Go13x13
        extends Channel(
          name = VariantKeys.variantName(Variant.wrap(GV.Go13x13)),
          icon = GV.Go13x13.perfIcon.toString,
          secondsSinceLastMove = freshBlitz,
          filters = Seq(variant(Variant.wrap(GV.Go13x13)), noBot),
          familyChannel = false,
          gameFamily = "go"
        )
    case object Go19x19
        extends Channel(
          name = VariantKeys.variantName(Variant.wrap(GV.Go19x19)),
          icon = GV.Go19x19.perfIcon.toString,
          secondsSinceLastMove = freshBlitz,
          filters = Seq(variant(Variant.wrap(GV.Go19x19)), noBot),
          familyChannel = false,
          gameFamily = "go"
        )
    case object Backgammon
        extends Channel(
          name = VariantKeys.variantName(Variant.wrap(BV.Backgammon)),
          icon = BV.Backgammon.perfIcon.toString,
          secondsSinceLastMove = freshBlitz,
          filters = Seq(variant(Variant.wrap(BV.Backgammon)), noBot),
          familyChannel = false,
          gameFamily = "backgammon"
        )
    case object Hyper
        extends Channel(
          name = VariantKeys.variantName(Variant.wrap(BV.Hyper)),
          icon = BV.Hyper.perfIcon.toString,
          secondsSinceLastMove = freshBlitz,
          filters = Seq(variant(Variant.wrap(BV.Hyper)), noBot),
          familyChannel = false,
          gameFamily = "backgammon"
        )
    case object Nackgammon
        extends Channel(
          name = VariantKeys.variantName(Variant.wrap(BV.Nackgammon)),
          icon = BV.Nackgammon.perfIcon.toString,
          secondsSinceLastMove = freshBlitz,
          filters = Seq(variant(Variant.wrap(BV.Nackgammon)), noBot),
          familyChannel = false,
          gameFamily = "backgammon"
        )
    case object Abalone
        extends Channel(
          name = VariantKeys.variantName(Variant.wrap(AV.Abalone)),
          icon = AV.Abalone.perfIcon.toString,
          secondsSinceLastMove = freshBlitz,
          filters = Seq(variant(Variant.wrap(AV.Abalone)), noBot),
          familyChannel = true,
          gameFamily = "abalone"
        )
    case object Bot
        extends Channel(
          name = "Bot",
          icon = "n",
          secondsSinceLastMove = freshBlitz,
          filters = Seq(hasBot),
          familyChannel = true,
          gameFamily = "other"
        )
    case object Computer
        extends Channel(
          name = "Computer",
          icon = "n",
          secondsSinceLastMove = freshBlitz,
          filters = Seq(computerFromInitialPosition),
          familyChannel = true,
          gameFamily = "other"
        )
    val all = List(
      AllGames,
      //Best,
      ChessFamily,
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
      Monster,
      UltraBullet,
      DraughtsFamily,
      International,
      Frisian,
      Frysk,
      Antidraughts,
      Breakthrough,
      Russian,
      Brazilian,
      Pool,
      Portuguese,
      English,
      LinesOfActionFamily,
      LinesOfAction,
      ScrambledEggs,
      ShogiFamily,
      Shogi,
      MiniShogi,
      XiangqiFamily,
      Xiangqi,
      MiniXiangqi,
      FlipelloFamily,
      Flipello,
      Flipello10,
      Amazons,
      BreakthroughTroykaFamily,
      BreakthroughTroyka,
      MiniBreakthroughTroyka,
      MancalaFamily,
      Oware,
      Togyzkumalak,
      Bestemshe,
      GoFamily,
      Go19x19,
      Go13x13,
      Go9x9,
      BackgammonFamily,
      Backgammon,
      Hyper,
      Nackgammon,
      Abalone,
      Bot,
      Computer
    )
    val byKey = all.map { c =>
      c.key -> c
    }.toMap

  }

  private def rated(min: Int)                           = (c: Candidate) => c.game.rated && hasMinRating(c.game, min)
  private def speed(speed: strategygames.Speed)         = (c: Candidate) => c.game.speed == speed
  private def variant(variant: Variant)                 = (c: Candidate) => c.game.variant == variant
  private def anyVariant(variantList: List[Variant])    = (c: Candidate) => variantList.contains(c.game.variant)
  private val standard                                  = variant(Variant.libStandard(GameLogic.Chess()))
  private val freshBlitz                                = 60 * 2
  private val freshRapid                                = 60 * 5
  private def computerFromInitialPosition(c: Candidate) = c.game.hasAi && !c.game.fromPosition
  private def hasBot(c: Candidate)                      = c.hasBot
  private def noBot(c: Candidate)                       = !c.hasBot

  private def onGoingGame(game: Game): Boolean = {
    !game.finished || (game.finished && !game.olderThan(7))
  }

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
