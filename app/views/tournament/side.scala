package views
package html.tournament

import strategygames.variant.Variant
import strategygames.format.FEN

import lila.api.Context
import lila.app.templating.Environment.*
import lila.app.ui.ScalatagsTemplate.*
import lila.common.String.html.markdownLinksOrRichText
import lila.tournament.{ Schedule, TeamBattle, Tournament, TournamentShield }
import lila.i18n.VariantKeys

object side {

  private val separator = " • "

  def apply(
      tour: Tournament,
      verdicts: lila.tournament.Condition.All.WithVerdicts,
      streamers: List[lila.user.User.ID],
      shieldOwner: Option[TournamentShield.OwnerId],
      chat: Boolean,
      previous: Option[Tournament] = None
  )(implicit ctx: Context) =
    frag(
      div(cls := "tour__meta")(
        st.section(dataIcon := (tour.iconChar.toString))(
          div(
            p(
              a(
                title  := "Clock info",
                href   := s"${routes.Page.lonePage("clocks")}",
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
              tour.position.isDefined so s"$separator${trans.thematic.txt()}",
              separator,
              tour.durationString
            ),
            if (tour.handicapped)
              a(href := routes.Page.lonePage("handicaps"), target := "_blank")(
                trans.handicappedTournament()
              )
            else tour.mode.fold(trans.casualTournament, trans.ratedTournament)(),
            separator,
            "Arena",
            (isGranted(_.ManageTournament) || (ctx.userId
              .has(tour.createdBy) && !tour.isFinished)).option(
              frag(
                " ",
                a(href := routes.Tournament.edit(tour.id), title := "Edit tournament")(iconTag("%"))
              )
            )
          )
        ),
        tour.teamBattle map teamBattle(tour),
        tour.spotlight map { s =>
          st.section(cls := "spotlight")(
            markdownLinksOrRichText(s.description),
            // the defender is whoever won the previous edition: the label leads there (date in the
            // tooltip), the name stays a normal user link. Without a previous edition on record,
            // fall back to the shield holder from the cache
            previous.filter(_ => isSeries(tour)).flatMap(prev => prev.winnerId.map(prev -> _)) match {
              case Some((prev, winner)) =>
                p(cls := "defender", dataIcon := "5")(
                  a(
                    cls   := "defender__edition",
                    href  := routes.Tournament.show(prev.id),
                    title := s"Previous edition: ${showDate(prev.startsAt)}"
                  )(holderLabel(tour)),
                  userIdLink(winner.some)
                )
              case None =>
                shieldOwner map { owner =>
                  p(cls := "defender", dataIcon := "5")(holderLabel(tour), userIdLink(owner.value.some))
                }
            },
            seriesNav(tour)
          )
        },
        // tour.medleyVariants.map { medleyVariants =>
        //  views.html.tournament.bits.medleyGames(
        //    medleyVariants,
        //    tour.minutes,
        //    tour.medleyMinutes.getOrElse(0),
        //    tour.pairingsClosedSeconds.toDouble / 60
        //  )
        // },
        tour.description map { d =>
          st.section(cls := "description")(markdownLinksOrRichText(d))
        },
        tour.looksLikePrize.option(bits.userPrizeDisclaimer(tour.createdBy)),
        verdicts.relevant.option(
          st.section(
            dataIcon := (if (ctx.isAuth && verdicts.accepted) "E"
                         else "L"),
            cls := List(
              "conditions" -> true,
              "accepted"   -> (ctx.isAuth && verdicts.accepted),
              "refused"    -> (ctx.isAuth && !verdicts.accepted)
            )
          )(
            div(
              (verdicts.list.sizeIs < 2).option(p(trans.conditionOfEntry())),
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
          )
        ),
        (!tour.noBerserk && tour.clock.berserkable).option(
          div(cls := "text", dataIcon := "`")(
            "Berserk Clock: ",
            a(
              title  := "Clock info",
              href   := s"${routes.Page.lonePage("clocks")}",
              target := "_blank"
            )(tour.clock.showBerserk)
          )
        ),
        tour.noBerserk.option(div(cls := "text", dataIcon := "`")("No Berserk allowed")),
        tour.noStreak.option(div(cls := "text", dataIcon := "Q")("No Arena streaks")),
        tour.statusScoring.option(
          div(cls := "text", dataIcon := "g")(
            "Extra points: +1 Gammon, +2 Backgammon."
          )
        ),
        (!tour.isFinished).option(tour.trophy1st.map { trophy1st =>
          table(cls := "trophyPreview")(
            tr(
              td(
                img(cls := "customTrophy", src := staticAssetUrl(s"images/trophy/${trophy1st}.png"))
              ),
              tour.trophy2nd.map { trophy2nd =>
                td(
                  img(cls := "customTrophy", src := staticAssetUrl(s"images/trophy/${trophy2nd}.png"))
                )
              },
              tour.trophy3rd.map { trophy3rd =>
                td(
                  img(cls := "customTrophy", src := staticAssetUrl(s"images/trophy/${trophy3rd}.png"))
                )
              }
            ),
            tr(
              td("1st Place"),
              tour.trophy2nd.map { _ => td("2nd Place") },
              tour.trophy3rd.map { _ => td("3rd Place") }
            )
          )
        }),
        (!tour.isScheduled && tour.description.isEmpty).option(
          frag(
            trans.by(userIdLink(tour.createdBy.some)),
            br
          )
        ),
        (!tour.isStarted || (tour.isScheduled && tour.position.isDefined)).option(
          absClientDateTime(
            tour.startsAt
          )
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
      streamers.nonEmpty.option(
        div(cls := "context-streamers")(
          streamers map views.html.streamer.bits.contextual
        )
      ),
      chat.option(views.html.chat.frag)
    )

  private def teamBattle(tour: Tournament)(battle: TeamBattle)(implicit ctx: Context) =
    st.section(cls := "team-battle")(
      p(cls := "team-battle__title text", dataIcon := "f")(
        s"Battle of ${battle.teams.size} teams and ${battle.nbLeaders} leaders",
        (ctx.userId.has(tour.createdBy) || isGranted(_.ManageTournament)).option(
          a(href := routes.Tournament.teamBattleEdit(tour.id), title := "Edit team battle")(iconTag("%"))
        )
      )
    )

  // series worth following edition to edition; hourlies, dailies and one-offs are not
  private def isSeries(tour: Tournament) =
    tour.schedule.exists { s =>
      !Set[Schedule.Freq](Schedule.Freq.Hourly, Schedule.Freq.Daily, Schedule.Freq.Unique).contains(s.freq)
    }

  private def holderLabel(tour: Tournament) =
    if (tour.isShield) "Defender:"
    else if (tour.schedule.exists(_.freq == Schedule.Freq.Yearly)) "Reigning champion:"
    else "Last winner:"

  // the series page, styled like the defender line above it
  private def seriesNav(tour: Tournament)(implicit ctx: Context): Option[Frag] =
    tour.schedule.filter(_ => isSeries(tour)).map { sched =>
      val variantName = VariantKeys.variantName(tour.variant)
      val (seriesUrl, seriesLabel) =
        if (tour.isShield && TournamentShield.Category.byKey(tour.variant.key).isDefined)
          routes.Tournament.categShields(tour.variant.key) -> s"$variantName Shield: Leaderboard"
        else if (sched.freq == Schedule.Freq.Yearly)
          routes.Tournament.history(sched.freq.name, 1, tour.variant.key.some) -> s"${sched.freq.display} $variantName: Leaderboard"
        else
          routes.Tournament.history(sched.freq.name, 1, tour.variant.key.some) -> s"${sched.freq.display} $variantName: all editions"
      p(cls := "series-nav", dataIcon := "g")(a(href := seriesUrl)(seriesLabel))
    }
}
