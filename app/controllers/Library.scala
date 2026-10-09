package controllers

import play.api.mvc.Result
import strategygames.variant.Variant

import lila.api.Context
import lila.app.{ *, given }
import lila.memo.CacheApi.*
import lila.puzzle.Puzzle

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
      views.html.library.bits.canonicalVariantKey(key) match {
        case Some(canonical) => MovedPermanently(routes.Library.variant(canonical).url).fuccess
        case None            => showVariant(key)
      }
    }

  def langVariant(lang: String, key: String) =
    views.html.library.bits.canonicalVariantKey(key) match {
      case Some(canonical) =>
        Action(MovedPermanently(routes.Library.langVariant(lang, canonical).url))
      case None =>
        LangPage(routes.Library.variant(key).url)(ctx => showVariant(key)(using ctx))(lang)
    }

  // Both halves are finished tournaments, so they change a few times a year, and the swiss one
  // matches its name with a case-insensitive regex that no index can serve. Uncached they ran on
  // every view of all 47 hubs, which is exactly the traffic these pages are built to attract.
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
                views.html.library.bits.msoTeamId,
                List(lila.common.Freq.MSOGP),
                // the 2021 online edition, before the Grand Prix series began: created by PlayStrategy
                // itself, for no team
                List(lila.common.Freq.MSO21),
                variant
              ) zip
              env.swiss.api.finishedNamed(
                views.html.library.bits.msoTeamId,
                views.html.library.bits.msoGrandPrixName,
                variant
              )
          }
        }
    }

  private def showVariant(key: String)(implicit ctx: Context): Fu[Result] =
    Variant.all.find(_.key == key) match {
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
          // one more than the page shows: it leaves out the tutorial, linked above
          notable <- env.study.notable.byVariant(variant, 7)
          studies <- env.study.pager
            .byIds(notable.map(_.id), ctx.me)
            // the cache is up to a day old: a study made private, unfeatured or kept off since is dropped
            .map(_.filter { s =>
              s.study.isPublic && s.study.onLibraryPages
            })
            .recoverDefault
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
              views.html.library.bits.grandPrixEditions(gpArenas, gpSwisses)
            )
        )
      }
      case None => NotFound("Variant not found").fuccess
    }

}
