package lila.app
package templating

import strategygames.{ Status => S, ClockConfig, Mode, Player => PlayerIndex, P2, P1, GameLogic }
import strategygames.variant.Variant
import controllers.routes
import play.api.i18n.Lang

import lila.api.Context
import lila.app.ui.ScalatagsTemplate._
import lila.game.{ Game, Namer, Player, Pov }
import lila.i18n.{ I18nKeys => trans, VariantKeys, defaultLang }
import lila.user.Title

trait GameHelper { self: I18nHelper with UserHelper with AiHelper with StringHelper with ChessgroundHelper =>

  def netBaseUrl: String
  def cdnUrl(path: String): String

  def povOpenGraph(pov: Pov) =
    lila.app.ui.OpenGraph(
      image = cdnUrl(routes.Export.gameThumbnail(pov.gameId).url).some,
      title = titleGame(pov.game),
      url = s"$netBaseUrl${routes.Round.watcher(pov.gameId, pov.playerIndex.name).url}",
      description = describePov(pov)
    )

  def titleGame(g: Game) = {
    val speed = strategygames.Speed(g.clock.map(_.config)).name
    s"$speed ${VariantKeys.variantName(g.variant)} • ${playerText(g.p1Player)} vs ${playerText(g.p2Player)}"
  }

  def describePov(pov: Pov) = {
    import pov._
    val p1 = playerText(player, withRating = true)
    val p2 = playerText(opponent, withRating = true)
    val speedAndClock =
      if (game.imported) "imported"
      else
        game.clock.fold(strategygames.Speed.Correspondence.name) { c =>
          s"${strategygames.Speed(c.config).name} (${c.config.show})"
        }
    val mode = game.mode.name
    val variant =
      if (game.variant.fromPositionVariant) s"position setup ${game.variant.gameLogic.name}"
      else if (game.variant.exotic) VariantKeys.variantName(game.variant)
      else game.variant.gameLogic.name.toLowerCase()
    import strategygames.Status._
    val result = (game.winner, game.loser, game.status, game.variant.gameLogic) match {
      case (
            Some(w),
            _,
            Mate,
            GameLogic.Chess() | GameLogic.FairySF() | GameLogic.Samurai() | GameLogic.Togyzkumalak() |
            GameLogic.Go() | GameLogic.Backgammon() | GameLogic.Abalone()
          ) =>
        s"${playerText(w)} won by checkmate"
      case (Some(w), _, Mate | PerpetualCheck, _) =>
        s"${playerText(w)} won by opponent perpetually checking"
      case (Some(w), _, Stalemate, _) if !game.variant.stalemateIsDraw =>
        s"${playerText(w)} won by stalemate"
      case (_, Some(l), Resign | Timeout | Cheat | NoStart, _) =>
        s"${playerText(l)} resigned"
      case (_, Some(l), Outoftime, _)                  => s"${playerText(l)} forfeits by time"
      case (_, Some(l), OutoftimeGammon, _)            => s"${playerText(l)} forfeits a gammon by time"
      case (_, Some(l), OutoftimeBackgammon, _)        => s"${playerText(l)} forfeits a backgammon by time"
      case (Some(w), _, UnknownFinish, _)              => s"${playerText(w)} won"
      case (_, _, Draw | Stalemate | UnknownFinish, _) => "Game is a draw"
      case (_, _, Aborted, _)                          => "Game has been aborted"
      case (Some(w), _, SingleWin, _)                  => s"${playerText(w)} won"
      case (Some(w), _, GammonWin, _)                  => s"${playerText(w)} won by gammon"
      case (Some(w), _, BackgammonWin, _)              => s"${playerText(w)} won by backgammon"
      case (_, Some(l), ResignGammon, _)               => s"${playerText(l)} resigned a gammon"
      case (_, Some(l), ResignBackgammon, _)           => s"${playerText(l)} resigned a backgammon"
      case (_, Some(l), ResignMatch, _)                => s"${playerText(l)} resigned the match"
      case (_, Some(l), CubeDropped, _)                => s"${playerText(l)} dropped the cube"
      case (Some(w), _, RuleOfGin, _)                  => s"${playerText(w)} won by rule of gin"
      case (Some(w), _, GinGammon, _)                  => s"${playerText(w)} won a gammon by rule of gin"
      case (Some(w), _, GinBackgammon, _)              => s"${playerText(w)} won a backgammon by rule of gin"
      case (_, _, VariantEnd, _)                       => VariantKeys.variantTitle(game.variant)
      case _                                           => "Game is still being played"
    }
    val turns = s"${game.stratGame.fullTurnCount} turns"
    s"$p1 plays $p2 in a $mode $speedAndClock game of $variant. $result after $turns. Click to replay, analyse, and discuss the game!"
  }

  def variantName(variant: Variant)(implicit lang: Lang) =
    VariantKeys.variantName(variant)

  def variantNameNoCtx(variant: Variant) = variantName(variant)(defaultLang)

  def shortClockName(clock: Option[ClockConfig])(implicit lang: Lang): Frag =
    clock.fold[Frag](trans.unlimited())(shortClockName)

  def shortClockName(clock: ClockConfig): Frag = raw(clock.show)

  def shortClockName(game: Game)(implicit lang: Lang): Frag =
    game.correspondenceClock
      .map(c => trans.nbDays(c.daysPerTurn)) orElse
      game.clock.map(_.config).map(shortClockName) getOrElse
      trans.unlimited()

  def modeName(mode: Mode)(implicit lang: Lang): String =
    mode match {
      case Mode.Casual => trans.casual.txt()
      case Mode.Rated  => trans.rated.txt()
    }

  def modeNameNoCtx(mode: Mode): String = modeName(mode)(defaultLang)

  def playerUsername(player: Player, withRating: Boolean = true, withTitle: Boolean = true)(implicit
      lang: Lang
  ): Frag =
    player.aiLevel.fold[Frag](
      player.userId.flatMap(lightUser).fold[Frag](trans.anonymous.txt()) { user =>
        frag(
          titleTag(user.title ifTrue withTitle map Title.apply),
          if (withRating) s"${user.name} (${lila.game.Namer ratingString player})"
          else user.name
        )
      }
    ) { level =>
      frag(aiName(level))
    }

  def playerText(player: Player, withRating: Boolean = false) =
    Namer.playerTextBlocking(player, withRating)(lightUser)

  def gameVsText(game: Game, withRatings: Boolean = false): String =
    Namer.gameVsTextBlocking(game, withRatings)(lightUser)

  val berserkIconSpan = iconTag("`")

  def playerLink(
      player: Player,
      cssClass: Option[String] = None,
      withOnline: Boolean = true,
      withRating: Boolean = true,
      withDiff: Boolean = true,
      engine: Boolean = false,
      withBerserk: Boolean = false,
      mod: Boolean = false,
      link: Boolean = true
  )(implicit lang: Lang): Frag = {
    val statusIcon = (withBerserk && player.berserk) option berserkIconSpan
    player.userId.flatMap(lightUser) match {
      case None =>
        val klass = cssClass.??(" " + _)
        span(cls := s"user-link$klass")(
          (player.aiLevel, player.name) match {
            case (Some(level), _) => aiNameFrag(level, withRating)
            case (_, Some(name))  => name
            case _                => trans.anonymous.txt()
          },
          player.rating.ifTrue(withRating) map { rating => s" ($rating)" },
          statusIcon
        )
      case Some(user) =>
        frag(
          (if (link) a else span)(
            cls := userClass(user.id, cssClass, withOnline),
            href := s"${routes.User show user.name}${if (mod) "?mod" else ""}"
          )(
            withOnline option frag(lineIcon(user), " "),
            playerUsername(player, withRating),
            (player.ratingDiff ifTrue withDiff) map { d =>
              frag(" ", showRatingDiff(d))
            },
            engine option span(
              cls := "tos_violation",
              title := trans.thisAccountViolatedTos.txt()
            )
          ),
          statusIcon
        )
    }
  }

  def gameEndStatus(game: Game)(implicit lang: Lang): String =
    game.status match {
      case S.Aborted => trans.gameAborted.txt()
      case S.Mate =>
        game.variant.gameLogic match {
          case GameLogic.Chess() | GameLogic.FairySF() | GameLogic.Samurai() | GameLogic.Togyzkumalak() |
              GameLogic.Go() | GameLogic.Backgammon() | GameLogic.Abalone() =>
            trans.checkmate.txt()
          case _ => ""
        }
      case S.PerpetualCheck => trans.perpetualCheck.txt()
      case S.Resign =>
        game.loser match {
          case Some(p) if p.playerIndex.p1 => trans.playerIndexResigned(game.playerTrans(P1)).v
          case _                           => trans.playerIndexResigned(game.playerTrans(P2)).v
        }
      case S.ResignGammon =>
        game.loser match {
          case Some(p) if p.playerIndex.p1 => trans.playerIndexResignedGammon(game.playerTrans(P1)).v
          case _                           => trans.playerIndexResignedGammon(game.playerTrans(P2)).v
        }
      case S.ResignBackgammon =>
        game.loser match {
          case Some(p) if p.playerIndex.p1 => trans.playerIndexResignedBackgammon(game.playerTrans(P1)).v
          case _                           => trans.playerIndexResignedBackgammon(game.playerTrans(P2)).v
        }
      case S.ResignMatch =>
        game.loser match {
          case Some(p) if p.playerIndex.p1 => trans.playerIndexResignedMatch(game.playerTrans(P1)).v
          case _                           => trans.playerIndexResignedMatch(game.playerTrans(P2)).v
        }
      case S.CubeDropped =>
        game.loser match {
          case Some(p) if p.playerIndex.p1 => trans.playerIndexDroppedTheCube(game.playerTrans(P1)).v
          case _                           => trans.playerIndexDroppedTheCube(game.playerTrans(P2)).v
        }
      case S.UnknownFinish => trans.finished.txt()
      case S.Stalemate     => trans.stalemate.txt()
      case S.Timeout =>
        game.loser match {
          case Some(p) if p.playerIndex.p1 => trans.playerIndexLeftTheGame(game.playerTrans(P1)).v
          case Some(_)                     => trans.playerIndexLeftTheGame(game.playerTrans(P2)).v
          case None                        => trans.draw.txt()
        }
      case S.Draw => trans.draw.txt()
      case S.Outoftime =>
        (game.turnPlayerIndex, game.loser) match {
          case (P1, Some(_)) => trans.playerIndexTimeOut(game.playerTrans(P1)).v
          case (P1, None)    => trans.playerIndexTimeOut(game.playerTrans(P1)).v + " • " + trans.draw.txt()
          case (P2, Some(_)) => trans.playerIndexTimeOut(game.playerTrans(P2)).v
          case (P2, None)    => trans.playerIndexTimeOut(game.playerTrans(P2)).v + " • " + trans.draw.txt()
        }
      case S.OutoftimeGammon =>
        game.loser match {
          case Some(p) if p.playerIndex.p1 => trans.playerIndexLosesByGammonTimeOut(game.playerTrans(P1)).v
          case _                           => trans.playerIndexLosesByGammonTimeOut(game.playerTrans(P2)).v
        }
      case S.OutoftimeBackgammon =>
        game.loser match {
          case Some(p) if p.playerIndex.p1 =>
            trans.playerIndexLosesByBackgammonTimeOut(game.playerTrans(P1)).v
          case _ => trans.playerIndexLosesByBackgammonTimeOut(game.playerTrans(P2)).v
        }
      case S.RuleOfGin =>
        game.winner match {
          case Some(p) if p.playerIndex.p1 => trans.playerIndexWinsByRuleOfGin(game.playerTrans(P1)).v
          case _                           => trans.playerIndexWinsByRuleOfGin(game.playerTrans(P2)).v
        }
      case S.GinGammon =>
        game.winner match {
          case Some(p) if p.playerIndex.p1 => trans.playerIndexWinsByGinGammon(game.playerTrans(P1)).v
          case _                           => trans.playerIndexWinsByGinGammon(game.playerTrans(P2)).v
        }
      case S.GinBackgammon =>
        game.winner match {
          case Some(p) if p.playerIndex.p1 => trans.playerIndexWinsByGinBackgammon(game.playerTrans(P1)).v
          case _                           => trans.playerIndexWinsByGinBackgammon(game.playerTrans(P2)).v
        }
      case S.NoStart =>
        val playerIndex = game.loser.fold(PlayerIndex.p1)(_.playerIndex).name.capitalize
        s"$playerIndex didn't move"
      case S.Cheat         => trans.cheatDetected.txt()
      case S.SingleWin     => trans.backgammonSingleWin.txt()
      case S.GammonWin     => trans.backgammonGammonWin.txt()
      case S.BackgammonWin => trans.backgammonBackgammonWin.txt()
      case S.VariantEnd =>
        game.variant match {
          case Variant.Chess(strategygames.chess.variant.KingOfTheHill)          => trans.kingInTheCenter.txt()
          case Variant.Chess(strategygames.chess.variant.ThreeCheck)             => trans.threeChecks.txt()
          case Variant.Chess(strategygames.chess.variant.FiveCheck)              => trans.fiveChecks.txt()
          case Variant.Chess(strategygames.chess.variant.RacingKings)            => trans.raceFinished.txt()
          case Variant.Chess(strategygames.chess.variant.LinesOfAction)          => trans.checkersConnected.txt()
          case Variant.Chess(strategygames.chess.variant.ScrambledEggs)          => trans.checkersConnected.txt()
          case Variant.Draughts(strategygames.draughts.variant.Breakthrough)     => trans.promotion.txt()
          case Variant.FairySF(strategygames.fairysf.variant.Flipello)           => trans.gameFinished.txt()
          case Variant.FairySF(strategygames.fairysf.variant.Flipello10)         => trans.gameFinished.txt()
          case Variant.FairySF(strategygames.fairysf.variant.Amazons)            => trans.gameFinished.txt()
          case Variant.FairySF(strategygames.fairysf.variant.BreakthroughTroyka) => trans.raceFinished.txt()
          case Variant.FairySF(strategygames.fairysf.variant.MiniBreakthroughTroyka) =>
            trans.raceFinished.txt()
          case Variant.Samurai(strategygames.samurai.variant.Oware) =>
            if (game.situation.isRepetition) trans.gameFinishedRepetition.txt() else trans.gameFinished.txt()
          case Variant.Togyzkumalak(strategygames.togyzkumalak.variant.Togyzkumalak) =>
            trans.gameFinished.txt()
          case Variant.Togyzkumalak(strategygames.togyzkumalak.variant.Bestemshe) =>
            trans.gameFinished.txt()
          case Variant.Go(strategygames.go.variant.Go9x9) =>
            if (game.situation.isRepetition) trans.gameFinishedRepetition.txt() else trans.gameFinished.txt()
          case Variant.Go(strategygames.go.variant.Go13x13) =>
            if (game.situation.isRepetition) trans.gameFinishedRepetition.txt() else trans.gameFinished.txt()
          case Variant.Go(strategygames.go.variant.Go19x19) =>
            if (game.situation.isRepetition) trans.gameFinishedRepetition.txt() else trans.gameFinished.txt()
          case Variant.Backgammon(strategygames.backgammon.variant.Backgammon) =>
            trans.gameFinished.txt()
          case Variant.Backgammon(strategygames.backgammon.variant.Hyper) => trans.gameFinished.txt()
          case Variant.Backgammon(strategygames.backgammon.variant.Nackgammon) =>
            trans.gameFinished.txt()
          case Variant.Abalone(strategygames.abalone.variant.Abalone) =>
            trans.gameFinished.txt()
          case _ => trans.variantEnding.txt()
        }
      case _ => ""
    }

  // p1Username 1-0 p2Username
  def gameSummary(p1UserId: String, p2UserId: String, finished: Boolean, result: Option[Boolean]) = {
    val res = if (finished) PlayerIndex.showResult(result map PlayerIndex.fromP1) else "*"
    s"${usernameOrId(p1UserId)} $res ${usernameOrId(p2UserId)}"
  }

  def gameResult(game: Game) =
    if (game.finished) PlayerIndex.showResult(game.winnerPlayerIndex)
    else "*"

  def gameLink(
      game: Game,
      playerIndex: PlayerIndex,
      ownerLink: Boolean = false,
      tv: Boolean = false
  )(implicit ctx: Context): String = {
    val owner = ownerLink ?? ctx.me.flatMap(game.player)
    if (tv) routes.Tv.index
    else
      owner.fold(routes.Round.watcher(game.id, playerIndex.name)) { o =>
        routes.Round.player(game fullIdOf o.playerIndex)
      }
  }.toString

  def gameLink(pov: Pov)(implicit ctx: Context): String = gameLink(pov.game, pov.playerIndex)

  def challengeTitle(c: lila.challenge.Challenge)(implicit lang: Lang) = {
    val speed = c.clock.map(_.config).fold(strategygames.Speed.Correspondence.name) { clock =>
      s"${strategygames.Speed(clock).name} (${clock.show})"
    }
    val challenger = c.challengerUser.fold(trans.anonymous.txt()) { reg =>
      s"${usernameOrId(reg.id)} (${reg.rating.show})"
    }
    val players =
      if (c.isOpen) "Open challenge"
      else
        c.destUser.fold(s"Challenge from $challenger") { dest =>
          s"$challenger challenges ${usernameOrId(dest.id)} (${dest.rating.show})"
        }
    s"$speed ${VariantKeys.variantName(c.variant)} ${c.mode.name} • $players"
  }

  def challengeOpenGraph(c: lila.challenge.Challenge)(implicit lang: Lang) =
    lila.app.ui.OpenGraph(
      title = challengeTitle(c),
      url = s"$netBaseUrl${routes.Round.watcher(c.id, P1.name).url}",
      description = "Join the challenge or watch the game here."
    )
}
