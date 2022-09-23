package views.html
package game

import strategygames.format.FEN
import strategygames.variant.Variant

import lila.api.Context
import lila.app.templating.Environment._
import lila.app.ui.ScalatagsTemplate._
import lila.i18n.VariantKeys

import controllers.routes

object side {

  private val separator  = " • "
  private val dataUserTv = attr("data-user-tv")
  private val dataTime   = attr("data-time")

  def apply(
      pov: lila.game.Pov,
      initialFen: Option[FEN],
      tour: Option[lila.tournament.TourAndTeamVs],
      simul: Option[lila.simul.Simul],
      userTv: Option[lila.user.User] = None,
      bookmarked: Boolean
  )(implicit ctx: Context): Option[Frag] =
    ctx.noBlind option frag(
      meta(pov, initialFen, tour, simul, userTv, bookmarked),
      pov.game.userIds.filter(isStreaming) map views.html.streamer.bits.contextual
    )

  def meta(
      pov: lila.game.Pov,
      initialFen: Option[FEN],
      tour: Option[lila.tournament.TourAndTeamVs],
      simul: Option[lila.simul.Simul],
      userTv: Option[lila.user.User] = None,
      bookmarked: Boolean
  )(implicit ctx: Context): Option[Frag] =
    ctx.noBlind option {
      import pov._
      div(cls := "game__meta")(
        st.section(
          div(cls := "game__meta__infos", dataIcon := bits.gameIcon(game))(
            div(
              div(cls := "header")(
                div(cls := "setup")(
                  views.html.bookmark.toggle(game, bookmarked),
                  if (game.imported)
                    div(
                      a(href := routes.Importer.importGame, title := trans.importGame.txt())("IMPORT"),
                      separator,
                      if (game.variant.exotic)
                        bits.variantLink(
                          game.variant,
                          (if (game.variant == Variant.Chess(strategygames.chess.variant.KingOfTheHill))
                             VariantKeys.variantShortName(game.variant)
                           else VariantKeys.variantName(game.variant)).toUpperCase,
                          initialFen = initialFen
                        )
                      else
                        VariantKeys.variantName(game.variant).toUpperCase
                    )
                  else
                    frag(
                      widgets showClock game,
                      separator,
                      (if (game.rated) trans.rated else trans.casual).txt(),
                      separator,
                      if (game.variant.exotic)
                        bits.variantLink(
                          game.variant,
                          (if (game.variant == Variant.Chess(strategygames.chess.variant.KingOfTheHill))
                             VariantKeys.variantShortName(game.variant)
                           else VariantKeys.variantName(game.variant)).toUpperCase,
                          initialFen = initialFen
                        )
                      else
                        game.perfType.map { pt =>
                          span(title := pt.desc)(pt.trans)
                        }
                    )
                ),
                game.pgnImport.flatMap(_.date).map(frag(_)) getOrElse {
                  frag(
                    if (game.isBeingPlayed) trans.playingRightNow()
                    else momentFromNowWithPreload(game.createdAt)
                  )
                }
              ),
              game.pgnImport.exists(_.date.isDefined) option small(
                "Imported ",
                game.pgnImport.flatMap(_.user).map { user =>
                  trans.by(userIdLink(user.some, None, withOnline = false))
                }
              )
            )
          ),
          div(cls := "game__meta__players")(
            game.players.map { p =>
              frag(
                div(cls := s"player playerIndex-icon is ${game.variant.playerColors(p.playerIndex)} text")(
                  playerLink(p, withOnline = false, withDiff = true, withBerserk = true)
                ),
                tour.flatMap(_.teamVs).map(_.teams(p.playerIndex)) map {
                  teamLink(_, withIcon = false)(cls := "team")
                }
              )
            }
          )
        ),
        game.finishedOrAborted option {
          st.section(cls := "status")(
            gameEndStatus(game),
            game.winner.map { winner =>
              frag(
                separator,
                trans.playerIndexIsVictorious(game.playerTrans(winner.playerIndex))
              )
            }
          )
        },
        initialFen
          .ifTrue(game.variant.chess960)
          .flatMap { fen =>
            fen match {
              case FEN.Chess(fen) => strategygames.chess.variant.Chess960.positionNumber(fen)
              case _              => sys.error("Mismatched fen gamelogic")
            }
          }
          .map { number =>
            st.section(
              "Chess960 start position: ",
              strong(number)
            )
          },
        userTv.map { u =>
          st.section(cls := "game__tv")(
            h2(cls := "top user-tv text", dataUserTv := u.id, dataIcon := "1")(u.titleUsername)
          )
        },
        tour.map { t =>
          st.section(cls := "game__tournament")(
            a(cls := "text", dataIcon := "g", href := routes.Tournament.show(t.tour.id))(t.tour.name()),
            div(cls := "clock", dataTime := t.tour.secondsToFinish)(t.tour.clockStatus)
          )
        } orElse game.tournamentId.map { tourId =>
          st.section(cls := "game__tournament-link")(tournamentLink(tourId))
        } orElse game.swissId.map { swissId =>
          st.section(cls := "game__tournament-link")(
            views.html.swiss.bits.link(lila.swiss.Swiss.Id(swissId))
          )
        } orElse simul.map { sim =>
          st.section(cls := "game__simul-link")(
            a(href := routes.Simul.show(sim.id))(sim.fullName)
          )
        },
        game.metadata.multiMatch map { m =>
          st.section(cls := "game__multi-match")(
            if (m.matches("[1-9]:.*")) {
              val gamenb        = m.take(1).toInt;
              val nbGames       = if (game.metadata.isLastMultiMatchGame) gamenb + 1 else gamenb;
              val currentGameNb = if (game.metadata.isLastMultiMatchGame) gamenb + 1 else gamenb - 1;
              frag(
                trans.multiMatch(),
                ": ",
                (1 to nbGames).map(i =>
                  if (i == gamenb) {
                    a(cls := "text", href := routes.Round.watcher(m.drop(2), (!pov.playerIndex).name))(
                      trans.gameNumberX(gamenb)
                    )
                  } else if (i == currentGameNb) {
                    span(cls := "current")(trans.gameNumberX(i))
                  } else {
                    span(cls := "")(trans.gameNumberX(i))
                  }
                )
              )
            } else trans.multiMatchGameX(1)
          )
        }
      )
    }
}
