package controllers

import play.api.mvc.Result
import strategygames.variant.Variant

import lila.api.Context
import lila.app.{ *, given }
import lila.memo.CacheApi.*
import lila.puzzle.Puzzle
import views.html.library.bits as libraryBits

final class Library(env: Env) extends LilaController(env) {

  def home =
    Open { implicit ctx =>
      {
        for {
          monthlyGameData <- env.game.cached.monthlyGames
          clockRates      <- env.game.libraryStats.gameClockRates
          // botOrHumanGames <- env.game.libraryStats.botOrHumanGames
        } yield Ok(views.html.library.home(monthlyGameData, clockRates))
      }
    }

  def variant(key: String) =
    Open { implicit ctx =>
      libraryBits.canonicalVariantKey(key) match {
        case Some(canonical) => MovedPermanently(routes.Library.variant(canonical).url).fuccess
        case None            => showVariant(key)
      }
    }

  def langVariant(lang: String, key: String) =
    libraryBits.canonicalVariantKey(key) match {
      case Some(canonical) =>
        Action(MovedPermanently(routes.Library.langVariant(lang, canonical).url))
      case None =>
        LangPage(routes.Library.variant(key).url)(ctx => showVariant(key)(using ctx))(lang)
    }

  // finished tournaments rarely change, and the swiss query is a regex no index serves
  private val grandPrixCache =
    env.memo.cacheApi[String, (List[lila.tournament.Tournament], List[lila.swiss.Swiss])](
      64,
      "library.grandPrix"
    ) {
      _.refreshAfterWrite(1.hour)
        .maximumSize(128)
        .buildAsyncFuture { key =>
          Variant.byKey.get(key).fold(fuccess(Nil -> Nil)) { variant =>
            env.tournament.tournamentRepo
              .finishedSeriesOfTeam(
                libraryBits.msoTeamId,
                List(lila.common.Freq.MSOGP),
                // the 2021 online edition, before the Grand Prix series began: created by PlayStrategy
                // itself, for no team
                List(lila.common.Freq.MSO21),
                variant
              ) zip
              env.swiss.api.finishedNamed(
                libraryBits.msoTeamId,
                libraryBits.msoGrandPrixName,
                variant
              )
          }
        }
    }

  private def showVariant(key: String)(implicit ctx: Context): Fu[Result] =
    Variant.byKey.get(key) match {
      case Some(variant) => {
        val tvChannel = lila.tv.Tv.Channel.find(variant.key)
        for {
          monthlyGameData <- env.game.cached.monthlyGames
          winRates        <- env.game.cached.gameWinRates
          leaderboards    <- env.user.cached.top10.get {}
          leaderboard = leaderboards.forVariant(variant)
          tours <- env.tournament.cached.onLibraryPage.getUnit.recoverDefault
          filteredTours = tours.filter(_.variant.key == variant.key)
          featuredGame <- tvChannel
            .map(env.tv.tv.getGame)
            .getOrElse(fuccess(none))
            .orElse(env.game.gameRepo.randomByVariant(variant))
          dailyPuzzle <- Puzzle.puzzleVariants
            .exists(_.key == variant.key)
            .so(env.puzzle.daily.getForVariant(variant))
          studies <- env.study.notable.libraryTiles(variant, ctx.me).recoverDefault
          (gpArenas, gpSwisses) <- grandPrixCache get variant.key
        } yield Ok(
          views.html.library
            .show(
              variant,
              monthlyGameData,
              winRates,
              leaderboard,
              filteredTours,
              featuredGame,
              dailyPuzzle,
              studies,
              libraryBits.grandPrixEditions(gpArenas, gpSwisses)
            )
        )
      }
      case None => NotFound("Variant not found").fuccess
    }

}
