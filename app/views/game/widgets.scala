package views.html
package game

import lila.api.Context
import lila.app.templating.Environment._
import lila.app.ui.ScalatagsTemplate._
import lila.game.{ Game, Player, Pov }
import lila.i18n.VariantKeys

object widgets {

  private val separator = " • "

  def apply(
      games: Seq[Game],
      user: Option[lila.user.User] = None,
      ownerLink: Boolean = false
  )(implicit ctx: Context): Frag =
    games map { g =>
      val fromPlayer  = user flatMap g.player
      val firstPlayer = fromPlayer | g.player(g.naturalOrientation)
      st.article(cls := "game-row paginated")(
        a(cls := "game-row__overlay", href := gameLink(g, firstPlayer.playerIndex, ownerLink)),
        div(cls := "game-row__board")(
          views.html.board.bits.mini(Pov(g, firstPlayer))(span)
        ),
        div(cls := "game-row__infos")(
          div(cls := "header", dataIcon := bits.gameIcon(g))(
            div(cls := "header__text")(
              strong(
                if (g.imported)
                  frag(
                    span("IMPORT"),
                    g.pgnImport.flatMap(_.user).map { user =>
                      frag(" ", trans.by(userIdLink(user.some, None, withOnline = false)))
                    },
                    separator,
                    if (g.variant.exotic)
                      bits.variantLink(g.variant, VariantKeys.variantName(g.variant).toUpperCase)
                    else VariantKeys.variantName(g.variant).toUpperCase
                  )
                else
                  frag(
                    showClock(g),
                    separator,
                    g.perfType.fold(strategygames.chess.variant.FromPosition.name)(_.trans),
                    separator,
                    if (g.fromHandicappedTournament) trans.handicapped.txt()
                    else if (g.rated) trans.rated.txt()
                    else trans.casual.txt()
                  )
              ),
              g.pgnImport.flatMap(_.date).fold[Frag](momentFromNowWithPreload(g.createdAt))(frag(_)),
              g.tournamentId.map { tourId =>
                frag(separator, tournamentLink(tourId))
              } orElse
                g.simulId.map { simulId =>
                  frag(separator, views.html.simul.bits.link(simulId))
                } orElse
                g.swissId.map { swissId =>
                  frag(separator, views.html.swiss.bits.link(lila.swiss.Swiss.Id(swissId)))
                },
              g.metadata.multiMatchGameNr map { gameNr =>
                frag(separator, trans.multiMatchGameX(gameNr))
              }
            )
          ),
          div(cls := "versus")(
            gamePlayer(g.p1Player),
            div(cls := "swords", dataIcon := "U"),
            gamePlayer(g.p2Player)
          ),
          div(cls := "result")(
            if (g.isBeingPlayed) trans.playingRightNow()
            else {
              if (g.finishedOrAborted)
                span(cls := g.winner.flatMap(w => fromPlayer.map(p => if (p == w) "win" else "loss")))(
                  gameEndStatus(g),
                  g.winner.map { winner =>
                    frag(
                      (gameEndStatus(g) != "").option(", "),
                      trans.playerIndexIsVictorious(g.playerTrans(winner.playerIndex))
                    )
                  }
                )
              else trans.playerIndexPlays(g.playerTrans(g.turnPlayerIndex))
            }
          ),
          if (g.actionStrs.length > 0)
            div(cls := "opening")(
              (!g.fromPosition ?? g.opening) map { opening =>
                strong(opening.opening.toString())
              },
              div(cls := "pgn")(
                g.actionStrs.take(6).map(_.mkString(",")).grouped(2).zipWithIndex map {
                  case (Vector(p1, p2), i) => s"${i + 1}. $p1 $p2"
                  case (Vector(p1), i)     => s"${i + 1}. $p1"
                  case _                   => ""
                } mkString " ",
                g.actionStrs.length > 6 option s" ... ${1 + (g.actionStrs.length - 1) / 2} turns "
              )
            )
          else frag(br, br),
          g.metadata.analysed option
            div(cls := "metadata text", dataIcon := "")(trans.computerAnalysisAvailable()),
          g.pgnImport.flatMap(_.user).map { user =>
            div(cls := "metadata")("PGN import by ", userIdLink(user.some))
          }
        )
      )
    }

  def showClock(game: Game)(implicit ctx: Context) =
    game.clock.map { clock =>
      frag(clock.config.show)
    } getOrElse {
      game.daysPerTurn
        .map { days =>
          span(title := trans.correspondence.txt())(
            if (days == 1) trans.oneDay()
            else trans.nbDays.pluralSame(days)
          )
        }
        .getOrElse {
          span(title := trans.unlimited.txt())("∞")
        }
    }

  private lazy val anonSpan = span(cls := "anon")(lila.user.User.anonymous)

  private def gamePlayer(player: Player)(implicit ctx: Context) =
    div(cls := s"player ${player.playerIndex.name}")(
      player.playerUser map { playerUser =>
        frag(
          userIdLink(playerUser.id.some, withOnline = false),
          br,
          player.berserk option berserkIconSpan,
          playerUser.rating,
          player.provisional option "?",
          playerUser.ratingDiff map { d =>
            frag(" ", showRatingDiff(d))
          }
        )
      } getOrElse {
        player.aiLevel map { level =>
          frag(
            span(aiName(level, withRating = false)),
            br,
            aiRating(level)
          )
        } getOrElse {
          (player.nameSplit.fold[Frag](anonSpan) { case (name, rating) =>
            frag(
              span(name),
              rating.map { r =>
                frag(br, r)
              }
            )
          })
        }
      }
    )
}
