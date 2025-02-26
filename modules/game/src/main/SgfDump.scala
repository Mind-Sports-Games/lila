package lila.game
import org.joda.time.DateTimeZone
import org.joda.time.format.DateTimeFormat
import strategygames.{ Clock, ClockConfig }
import strategygames.format.sgf.{ Dumper, Tag, TagType, Tags }
import strategygames.format.{ FEN, Forsyth }

import strategygames.{ ActionStrs, Centis, Player => PlayerIndex, GameLogic, Status, GameFamily, P1 }
import strategygames.variant.Variant

import lila.common.config.BaseUrl
import lila.common.LightUser
import lila.common.Form
import lila.i18n.VariantKeys

final class SgfDump(
    baseUrl: BaseUrl,
    lightUserApi: lila.user.LightUserApi
)(implicit ec: scala.concurrent.ExecutionContext) {

  import SgfDump._

  def apply(
      game: Game,
      initialFen: Option[FEN],
      isTags: Boolean,
      teams: Option[PlayerIndex.Map[String]] = None,
      hideRatings: Boolean = false
  ): Fu[String] = {
    val tagsFuture =
      if (isTags)
        tags(
          game,
          initialFen,
          withRatings = !hideRatings,
          teams = teams
        )
      else fuccess(Tags(Nil))
    tagsFuture map { tags =>
      if (game.gameRecordFormat == "sgf") {
        format(game, tags, initialFen)
      } else {
        "SGF NOT SUPPORTED"
      }
    }
  }

  def format(game: Game, tags: Tags, initialFen: Option[FEN]): String = {
    "(;" ++ tags.toString ++ "\n\n" ++ Dumper(game.variant, game.actionStrs, initialFen) ++ ")"
  }

  private def gameLightUsers(game: Game): Fu[(Option[LightUser], Option[LightUser])] =
    (game.p1Player.userId ?? lightUserApi.async) zip (game.p2Player.userId ?? lightUserApi.async)

  private def eventOf(game: Game) = {
    val perf = game.perfType.fold("Standard")(_.trans(lila.i18n.defaultLang))
    game.tournamentId.map { id =>
      s"${game.mode} $perf tournament https://playstrategy.org/tournament/$id"
    } orElse game.simulId.map { id =>
      s"$perf simul https://playstrategy.org/simul/$id"
    } getOrElse {
      s"${game.mode} $perf game"
    }
  }

  private def gameUrl(id: String) = s"$baseUrl/$id"

  private def rating(p: Player) = p.rating.fold("?")(_.toString)

  private def player(p: Player, u: Option[LightUser]) =
    p.aiLevel.fold(u.fold(p.name | lila.user.User.anonymous)(_.name))("playstrategy AI level " + _)

  // https://www.red-bean.com/sgf/
  def tags(
      game: Game,
      initialFen: Option[FEN],
      teams: Option[PlayerIndex.Map[String]] = None,
      withProfileName: Boolean = false,
      withRatings: Boolean = true
  ): Fu[Tags] = {
    val isP1Black    = game.variant.gameFamily.playerColors.get(P1) == Some("black")
    val isGo         = game.variant.gameFamily == GameFamily.Go()
    val isBackgammon = game.variant.gameFamily == GameFamily.Backgammon()
    gameLightUsers(game) map { case (p1, p2) =>
      Tags {
        List[Option[Tag]](
          Tag(_.FF, 4).some,
          Tag(_.CA, "UTF-8").some,
          Tag(_.AP, "playstrategy.org").some,
          Tag(_.DT, Tag.DT.format.print(game.createdAt)).some,
          Tag(_.PC, "PlayStrategy: " ++ gameUrl(game.id)).some,
          Tag(_.EV, eventOf(game)).some,
          Tag(_.GN, player(game.p1Player, p1) ++ " vs. " ++ player(game.p2Player, p2)).some,
          isP1Black option Tag(_.PB, player(game.p1Player, p1)),
          !isP1Black option Tag(_.PB, player(game.p2Player, p2)),
          !isP1Black option Tag(_.PW, player(game.p1Player, p1)),
          isP1Black option Tag(_.PW, player(game.p2Player, p2)),
          !isGo option Tag(_.RE, result(game)), //TODO different for backgammon multipoint
          !isGo && withRatings && isP1Black option Tag(_.BR, rating(game.p1Player)),
          !isGo && withRatings && !isP1Black option Tag(_.BR, rating(game.p2Player)),
          !isGo && withRatings && !isP1Black option Tag(_.WR, rating(game.p1Player)),
          !isGo && withRatings && isP1Black option Tag(_.WR, rating(game.p2Player)),
          Tag.timeControl(game.clock.map(_.config)).some,
          teams.flatMap { t => isP1Black option Tag("BT", t.p1) },
          teams.flatMap { t => !isP1Black option Tag("BT", t.p2) },
          teams.flatMap { t => !isP1Black option Tag("WT", t.p1) },
          teams.flatMap { t => isP1Black option Tag("WT", t.p2) },
          if (!isGo && !isBackgammon) { initialFen.map { fen => Tag(_.IP, fen.value) } }
          else None
        ).flatten
      } ++ (game.variant.gameFamily match {
        case GameFamily.LinesOfAction() => //not used yet
          Tags {
            List(
              Tag(_.GM, 9),
              Tag(_.SU, if (game.variant.key == "linesOfAction") "Standard" else "Scrambled-eggs")
            )
          }
        case GameFamily.Shogi() =>
          Tags {
            List(
              Tag(_.GM, 8),
              Tag(_.SZ, game.variant.toFairySF.boardSize.height),
              Tag(_.SU, if (game.variant.key == "shogi") "Standard" else "MiniShogi")
            )
          }
        case GameFamily.Xiangqi() =>
          Tags {
            List(
              Tag(_.GM, 7),
              Tag(_.SZ, game.variant.toFairySF.boardSize.height),
              Tag(_.SU, if (game.variant.key == "xiangqi") "Standard" else "MiniXiangqi")
            )
          }
        case GameFamily.Flipello() =>
          Tags { List(Tag(_.GM, 2), Tag(_.SZ, game.variant.toFairySF.boardSize.height)) }
        case GameFamily.Amazons() => Tags { List(Tag(_.GM, 18)) }
        case GameFamily.BreakthroughTroyka() =>
          Tags {
            List(
              Tag(_.GM, 41),
              Tag(_.SZ, game.variant.toFairySF.boardSize.height),
              Tag(_.SU, if (game.variant.key == "breakthroughtroyka") "Standard" else "MiniBreakthrough")
            )
          }
        case GameFamily.Go() =>
          Tags {
            List(
              Tag(_.GM, 1),
              Tag(_.SZ, game.variant.toGo.boardSize.height),
              Tag(_.KM, game.board.toGo.apiPosition.initialFen.komi),
              Tag(_.HA, game.board.toGo.apiPosition.initialFen.handicap.getOrElse(0)),
              Tag(_.RU, "Chinese")
            )
          }
        case GameFamily.Backgammon() =>
          val isCrawfordGame =
            game.multiPointState.map(_.isCrawfordState).getOrElse(false) && game.board.cubeData.fold(true)(
              _ => false
            )
          val crawfordVariantLine =
            if (game.variant.key == "hyper") ":Hypergammon3"
            else if (game.variant.key == "nackgammon") ":Nackgammon"
            else ""
          val matchInfo = game.multiPointState.fold((1, 0, 0, 0))(mps =>
            (mps.target, game.metadata.multiMatchGameNr.fold(0)(x => x - 1), mps.p1Points, mps.p2Points)
          )
          Tags {
            List(
              Tag(_.GM, 6),
              Tag(_.RU, "Crawford" + (if (isCrawfordGame) ":CrawfordGame" else "") + crawfordVariantLine),
              Tag(_.CV, game.board.cubeData.map(_.value).getOrElse(1)),
              Tag(
                _.CO,
                game.board.cubeData.fold('n')(_.owner.fold('c')(p => if (p == PlayerIndex.P1) 'w' else 'b'))
              ),
              Tag.matchInfo(matchInfo._1, matchInfo._2, matchInfo._3, matchInfo._4)
            )
          }
        case _ => Tags.empty
      })
    }
  }
}

object SgfDump {

  def result(game: Game) =
    if (game.finished) {
      game.variant.key match {
        case "backgammon" | "nackgammon" | "hyper" => backgammonResult(game)
        case _                                     => PlayerIndex.showResult(game.winnerPlayerIndex, false)
      }
    } else "*"

  def backgammonResult(game: Game): String = {
    val isWinnerP1 = game.winnerPlayerIndex == Some(PlayerIndex.P1)
    val winner     = if (isWinnerP1) "W" else "B"
    val maxPoints =
      game.multiPointState.fold(1)(mps => mps.target - (if (isWinnerP1) mps.p1Points else mps.p2Points))
    val points = Math.min(game.pointValue.getOrElse(1), game.multiPointState.fold(1)(_ => maxPoints))
    val resign =
      if (
        (Status.resigned ++ List(Status.Outoftime, Status.OutoftimeGammon, Status.OutoftimeBackgammon))
          .contains(game.status)
      ) "R"
      else ""
    return s"$winner+$points$resign"
  }
}
