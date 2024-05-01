package views
package html.tournament

import controllers.routes

import strategygames.variant.Variant
import strategygames.format.FEN

import lila.api.Context
import lila.app.templating.Environment._
import lila.app.ui.ScalatagsTemplate._
import lila.common.String.html.markdownLinksOrRichText
import lila.tournament.{ TeamBattle, Tournament, TournamentShield }
import lila.i18n.VariantKeys

object side {

  private val separator = " • "

  def apply(
      tour: Tournament,
      verdicts: lila.tournament.Condition.All.WithVerdicts,
      streamers: List[lila.user.User.ID],
      shieldOwner: Option[TournamentShield.OwnerId],
      chat: Boolean
  )(implicit ctx: Context) =
    frag(
      div(cls := "tour__meta")(
        st.section(dataIcon := (tour.iconChar.toString))(
          div(
            p(
              a(
                title := "Clock info",
                href := s"${routes.Page.loneBookmark("clocks")}",
                target := "_blank"
              )(tour.clock.show),
              separator,
              if (tour.isMedley) {
                views.html.game.bits.medleyLink
              } else if (tour.variant.exotic) {
                views.html.game.bits.variantLink(
                  tour.variant,
                  if (tour.variant == Variant.Chess(strategygames.chess.variant.KingOfTheHill))
                    VariantKeys.variantShortName(tour.variant)
                  else VariantKeys.variantName(tour.variant)
                )
              } else tour.perfType.trans,
              tour.position.isDefined ?? s"$separator${trans.thematic.txt()}",
              separator,
              tour.durationString
            ),
            tour.mode.fold(trans.casualTournament, trans.ratedTournament)(),
            separator,
            "Arena",
            (isGranted(_.ManageTournament) || (ctx.userId
              .has(tour.createdBy) && !tour.isFinished)) option frag(
              " ",
              a(href := routes.Tournament.edit(tour.id), title := "Edit tournament")(iconTag("%"))
            )
          )
        ),
        tour.teamBattle map teamBattle(tour),
        tour.spotlight map { s =>
          st.section(
            markdownLinksOrRichText(s.description),
            shieldOwner map { owner =>
              p(cls := "defender", dataIcon := "5")(
                "Defender:",
                userIdLink(owner.value.some)
              )
            }
          )
        },
        //tour.medleyVariants.map { medleyVariants =>
        //  views.html.tournament.bits.medleyGames(
        //    medleyVariants,
        //    tour.minutes,
        //    tour.medleyMinutes.getOrElse(0),
        //    tour.pairingsClosedSeconds.toDouble / 60
        //  )
        //},
        tour.description map { d =>
          st.section(cls := "description")(markdownLinksOrRichText(d))
        },
        tour.looksLikePrize option bits.userPrizeDisclaimer(tour.createdBy),
        verdicts.relevant option st.section(
          dataIcon := (if (ctx.isAuth && verdicts.accepted) "E"
                       else "L"),
          cls := List(
            "conditions" -> true,
            "accepted"   -> (ctx.isAuth && verdicts.accepted),
            "refused"    -> (ctx.isAuth && !verdicts.accepted)
          )
        )(
          div(
            verdicts.list.sizeIs < 2 option p(trans.conditionOfEntry()),
            verdicts.list map { v =>
              p(
                cls := List(
                  "condition" -> true,
                  "accepted"  -> (ctx.isAuth && v.verdict.accepted),
                  "refused"   -> (ctx.isAuth && !v.verdict.accepted)
                ),
                title := v.verdict.reason.map(_(ctx.lang))
              )(v.condition match {
                case lila.tournament.Condition.TeamMember(teamId, teamName) =>
                  trans.mustBeInTeam(teamLink(teamId, teamName, withIcon = false))
                case c => c.name
              })
            }
          )
        ),
        (!tour.noBerserk && tour.clock.berserkable) option div(cls := "text", dataIcon := "`")(
          "Berserk Clock: ",
          a(
            title := "Clock info",
            href := s"${routes.Page.loneBookmark("clocks")}",
            target := "_blank"
          )(tour.clock.showBerserk)
        ),
        tour.noBerserk option div(cls := "text", dataIcon := "`")("No Berserk allowed"),
        tour.noStreak option div(cls := "text", dataIcon := "Q")("No Arena streaks"),
        !tour.isFinished option tour.trophy1st.map { trophy1st =>
          table(cls := "trophyPreview")(
            tr(
              td(
                img(cls := "customTrophy", src := assetUrl(s"images/trophy/${trophy1st}.png"))
              ),
              tour.trophy2nd.map { trophy2nd =>
                td(
                  img(cls := "customTrophy", src := assetUrl(s"images/trophy/${trophy2nd}.png"))
                )
              },
              tour.trophy3rd.map { trophy3rd =>
                td(
                  img(cls := "customTrophy", src := assetUrl(s"images/trophy/${trophy3rd}.png"))
                )
              }
            ),
            tr(
              td("1st Place"),
              tour.trophy2nd.map { _ => td("2nd Place") },
              tour.trophy3rd.map { _ => td("3rd Place") }
            )
          )
        },
        !tour.isScheduled && tour.description.isEmpty option frag(
          trans.by(userIdLink(tour.createdBy.some)),
          br
        ),
        (!tour.isStarted || (tour.isScheduled && tour.position.isDefined)) option absClientDateTime(
          tour.startsAt
        ),
        tour.startingPosition.map { pos =>
          p(
            a(targetBlank, href := pos.url)(strong(pos.eco), " ", pos.name),
            separator,
            views.html.base.bits.fenAnalysisLink(FEN.Chess(pos.fen))
          )
        } orElse tour.position.map { fen =>
          p(
            "Custom position",
            separator,
            views.html.base.bits.fenAnalysisLink(fen)
          )
        }
      ),
      streamers.nonEmpty option div(cls := "context-streamers")(
        streamers map views.html.streamer.bits.contextual
      ),
      chat option views.html.chat.frag
    )

  private def teamBattle(tour: Tournament)(battle: TeamBattle)(implicit ctx: Context) =
    st.section(cls := "team-battle")(
      p(cls := "team-battle__title text", dataIcon := "f")(
        s"Battle of ${battle.teams.size} teams and ${battle.nbLeaders} leaders",
        (ctx.userId.has(tour.createdBy) || isGranted(_.ManageTournament)) option
          a(href := routes.Tournament.teamBattleEdit(tour.id), title := "Edit team battle")(iconTag("%"))
      )
    )
}
