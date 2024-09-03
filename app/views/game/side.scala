package views.html
package game

import strategygames.{ P1, P2 }
import strategygames.format.FEN
import strategygames.GameFamily
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
      bookmarked: Boolean,
      swissPairingGames: Option[lila.swiss.SwissPairingGames]
  )(implicit ctx: Context): Option[Frag] =
    ctx.noBlind option frag(
      meta(pov, initialFen, tour, simul, userTv, bookmarked, swissPairingGames),
      pov.game.userIds.filter(isStreaming) map views.html.streamer.bits.contextual
    )

  def meta(
      pov: lila.game.Pov,
      initialFen: Option[FEN],
      tour: Option[lila.tournament.TourAndTeamVs],
      simul: Option[lila.simul.Simul],
      userTv: Option[lila.user.User] = None,
      bookmarked: Boolean,
      swissPairingGames: Option[lila.swiss.SwissPairingGames]
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
                      a(
                        cls := "remove_color",
                        title := "Clock info",
                        href := s"${routes.Page.loneBookmark("clocks")}",
                        target := "_blank"
                      )(widgets showClock game),
                      separator,
                      if (game.fromHandicappedTournament) {
                        a(
                          cls := "remove_color",
                          title := "Handicap info",
                          href := s"${routes.Page.loneBookmark("handicaps")}",
                          target := "_blank"
                        )(trans.handicapped.txt())
                      } else if (game.rated) trans.rated.txt()
                      else trans.casual.txt(),
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
          .ifTrue(
            game.variant.key == "chess960" || game.variant.gameFamily == GameFamily
              .Draughts() || game.variant.gameFamily == GameFamily.Go()
          )
          .flatMap { fen =>
            (fen, game.variant) match {
              case (FEN.Chess(fen), _) =>
                strategygames.chess.variant.Chess960.positionNumber(fen).map(_.toString)
              case (FEN.Draughts(fen), Variant.Draughts(variant)) => variant.drawTableInfo(fen)
              case (FEN.Go(fen), Variant.Go(variant))             => variant.setupInfo(fen)
              case _                                              => sys.error("Mismatched fen gamelogic")
            }
          }
          .map { info =>
            st.section(cls := "starting-position")(
              game.variant match {
                case Variant.Chess(_)    => "Chess960 start position: "
                case Variant.Draughts(_) => info
                case Variant.Go(_)       => info
                case _                   => ""
              },
              game.variant match {
                case Variant.Chess(_) => strong(info)
                case _                => ""
              }
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
            if (t.tour.isMedley && !t.tour.finalMedleyVariant) {
              div(cls := "medley-interval")(
                span(cls := "clock", dataTime := t.tour.secondsToFinish)(t.tour.clockStatus),
                span(cls := "text medley-text")(" ("),
                span(cls := "clock", dataTime := t.tour.meldeySecondsToFinishInterval)(
                  t.tour.medleyClockStatus
                ),
                span(cls := "text medley-text")(")")
              )
            } else { div(cls := "clock", dataTime := t.tour.secondsToFinish)(t.tour.clockStatus) }
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
        swissPairingGames.flatMap { spg =>
          if (spg.nbGamesPerRound > 1) {
            Some(
              st.section(cls := "game__multi-match")(
                frag(
                  trans.multiMatch(),
                  if (spg.isBestOfX) s" (best of ${spg.nbGamesPerRound})"
                  else if (spg.isPlayX) s" (play ${spg.nbGamesPerRound} games)"
                  else "",
                  s" : ${spg.game.p1Player.userId.getOrElse("?")} (${spg.strResultOf(P1)}) vs ${spg.game.p2Player.userId
                    .getOrElse("?")} (${spg.strResultOf(P2)}) : ",
                  spg.multiMatchGames
                    .foldLeft(List(spg.game))(_ ++ _)
                    .zipWithIndex
                    .map {
                      case (mmGame, index) => {
                        val current = if (mmGame.id == game.id) " current" else ""
                        a(
                          cls := s"text glpt${current} mm_game_link",
                          href := routes.Round.watcher(mmGame.id, (!pov.playerIndex).name)
                        )(
                          trans.gameNumberX(index + 1)
                        )
                      }
                    }
                )
              )
            )
          } else None
        }
      )
    }
}
