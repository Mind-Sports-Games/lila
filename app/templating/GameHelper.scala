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
    val speed   = strategygames.Speed(g.clock.map(_.config)).name
    val variant = g.variant.exotic ?? s" ${VariantKeys.variantName(g.variant)}"
    s"$speed$variant ${g.variant.gameLogic.name} • ${playerText(g.p1Player)} vs ${playerText(g.p2Player)}"
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
            GameLogic.Go()
          ) =>
        s"${playerText(w)} won by checkmate"
      case (Some(w), _, Mate | PerpetualCheck, _) =>
        s"${playerText(w)} won by opponent perpetually checking"
      case (Some(w), _, Stalemate, _) if !game.variant.stalemateIsDraw =>
        s"${playerText(w)} won by stalemate"
      case (_, Some(l), Resign | Timeout | Cheat | NoStart, _) =>
        s"${playerText(l)} resigned"
      case (_, Some(l), Outoftime, _)                  => s"${playerText(l)} forfeits by time"
      case (Some(w), _, UnknownFinish, _)              => s"${playerText(w)} won"
      case (_, _, Draw | Stalemate | UnknownFinish, _) => "Game is a draw"
      case (_, _, Aborted, _)                          => "Game has been aborted"
      case (_, _, VariantEnd, _)                       => VariantKeys.variantTitle(game.variant)
      case _                                           => "Game is still being played"
    }
    val moves = s"${game.chess.fullMoveNumber} moves"
    s"$p1 plays $p2 in a $mode $speedAndClock game of $variant. $result after $moves. Click to replay, analyse, and discuss the game!"
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
              GameLogic.Go() =>
            trans.checkmate.txt()
          case _ => ""
        }
      case S.PerpetualCheck => trans.perpetualCheck.txt()
      case S.Resign =>
        game.loser match {
          case Some(p) if p.playerIndex.p1 => trans.playerIndexResigned(game.playerTrans(P1)).v
          case _                           => trans.playerIndexResigned(game.playerTrans(P2)).v
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
      case S.NoStart =>
        val playerIndex = game.loser.fold(PlayerIndex.p1)(_.playerIndex).name.capitalize
        s"$playerIndex didn't move"
      case S.Cheat => trans.cheatDetected.txt()
      case S.VariantEnd =>
        game.variant match {
          case Variant.Chess(strategygames.chess.variant.KingOfTheHill)      => trans.kingInTheCenter.txt()
          case Variant.Chess(strategygames.chess.variant.ThreeCheck)         => trans.threeChecks.txt()
          case Variant.Chess(strategygames.chess.variant.FiveCheck)          => trans.fiveChecks.txt()
          case Variant.Chess(strategygames.chess.variant.RacingKings)        => trans.raceFinished.txt()
          case Variant.Chess(strategygames.chess.variant.LinesOfAction)      => trans.checkersConnected.txt()
          case Variant.Chess(strategygames.chess.variant.ScrambledEggs)      => trans.checkersConnected.txt()
          case Variant.Draughts(strategygames.draughts.variant.Breakthrough) => trans.promotion.txt()
          case Variant.FairySF(strategygames.fairysf.variant.Flipello)       => trans.gameFinished.txt()
          case Variant.FairySF(strategygames.fairysf.variant.Flipello10)     => trans.gameFinished.txt()
          case Variant.FairySF(strategygames.fairysf.variant.Amazons)        => trans.gameFinished.txt()
          case Variant.Samurai(strategygames.samurai.variant.Oware) =>
            if (game.situation.isRepetition) trans.owareCycle.txt() else trans.gameFinished.txt()
          case Variant.Togyzkumalak(strategygames.togyzkumalak.variant.Togyzkumalak) =>
            trans.gameFinished.txt()
          case Variant.Go(strategygames.go.variant.Go9x9)   => trans.gameFinished.txt()
          case Variant.Go(strategygames.go.variant.Go13x13) => trans.gameFinished.txt()
          case Variant.Go(strategygames.go.variant.Go19x19) => trans.gameFinished.txt()
          case _                                            => trans.variantEnding.txt()
        }
      case _ => ""
    }

  def gameTitle(game: Game, playerIndex: PlayerIndex): String = {
    val u1 = playerText(game player playerIndex, withRating = true)
    val u2 = playerText(game opponent playerIndex, withRating = true)
    val clock = game.clock ?? { c =>
      " • " + c.config.show
    }
    val variant = game.variant.exotic ?? s" • ${VariantKeys.variantName(game.variant)}"
    s"$u1 vs $u2$clock$variant"
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
    val variant = c.variant.exotic ?? s" ${VariantKeys.variantName(c.variant)}"
    val challenger = c.challengerUser.fold(trans.anonymous.txt()) { reg =>
      s"${usernameOrId(reg.id)} (${reg.rating.show})"
    }
    val players =
      if (c.isOpen) "Open challenge"
      else
        c.destUser.fold(s"Challenge from $challenger") { dest =>
          s"$challenger challenges ${usernameOrId(dest.id)} (${dest.rating.show})"
        }
    s"$speed$variant ${c.mode.name} Chess • $players"
  }

  def challengeOpenGraph(c: lila.challenge.Challenge)(implicit lang: Lang) =
    lila.app.ui.OpenGraph(
      title = challengeTitle(c),
      url = s"$netBaseUrl${routes.Round.watcher(c.id, P1.name).url}",
      description = "Join the challenge or watch the game here."
    )
}
