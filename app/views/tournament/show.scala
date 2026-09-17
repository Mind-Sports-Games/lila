package views.html
package tournament

import play.api.libs.json.Json

import lila.api.Context
import lila.app.templating.Environment.*
import lila.app.ui.ScalatagsTemplate.*
import lila.common.String.html.safeJsonValue
import lila.tournament.Tournament
import lila.user.User
import lila.i18n.VariantKeys

object show {

  def apply(
      tour: Tournament,
      verdicts: lila.tournament.Condition.All.WithVerdicts,
      data: play.api.libs.json.JsObject,
      chatOption: Option[lila.chat.UserChat.Mine],
      streamers: List[User.ID],
      shieldOwner: Option[lila.tournament.TournamentShield.OwnerId],
      previous: Option[Tournament] = None
  )(implicit ctx: Context) =
    views.html.base.layout(
      title = s"${tour.name()} • ${showEnglishDate(tour.startsAt)}",
      moreJs = frag(
        jsModule("tournament"),
        eventJsonLd(tour),
        embedJsUnsafeLoadThen(s"""PlayStrategyTournament(${safeJsonValue(
            Json.obj(
              "data"   -> data,
              "i18n"   -> bits.jsI18n,
              "userId" -> ctx.userId,
              "chat"   -> chatOption.map { c =>
                chat.json(
                  c.chat,
                  name = trans.chatRoom.txt(),
                  timeout = c.timeout,
                  public = true,
                  resourceId = lila.chat.Chat.ResourceId(s"tournament/${c.chat.id}"),
                  localMod = ctx.userId.has(tour.createdBy)
                )
              }
            )
          )})""")
      ),
      moreCss = cssTag {
        if (tour.isTeamBattle) "tournament.show.team-battle"
        else "tournament.show"
      },
      chessground = false,
      openGraph = lila.app.ui
        .OpenGraph(
          title = s"${tour.name()} • ${showEnglishDate(tour.startsAt)}: ${VariantKeys
              .variantName(tour.variant)} ${tour.clock.show} ${
              if (tour.handicapped) trans.handicapped.txt()
              else tour.mode.name
            }",
          url = s"$netBaseUrl${routes.Tournament.show(tour.id).url}",
          description =
            s"${tour.nbPlayers} players compete in the ${showEnglishDate(tour.startsAt)} ${tour.name()}. " +
              s"${tour.clock.show} ${
                  if (tour.handicapped) trans.handicapped.txt()
                  else tour.mode.name
                } games are played during ${tour.minutes} minutes. " +
              tour.winnerId.fold("Winner is not yet decided.") { winnerId =>
                s"${usernameOrId(winnerId)} takes the prize home!"
              } +
              previous.so { prev =>
                s" Previous edition: ${showEnglishDate(prev.startsAt)}" +
                  prev.winnerId.so(w => s", won by ${usernameOrId(w)}") + "."
              }
        )
        .some
    )(
      main(cls := s"tour${tour.schedule
          .so { sched =>
            s" tour-sched tour-sched-${sched.freq.name} tour-speed-${sched.speed.name} tour-variant-${sched.variant.key} tour-id-${tour.id}"
          }}")(
        st.aside(cls := "tour__side")(
          tournament.side(tour, verdicts, streamers, shieldOwner, chatOption.isDefined, previous)
        ),
        div(cls := "tour__main")(div(cls := "box")),
        tour.isCreated.option(
          div(cls := "tour__faq")(
            faq(
              tour.mode.rated.some,
              Some(tour.handicapped),
              Some(tour.statusScoring),
              Some(tour.isMedley),
              tour.isPrivate.option(tour.id)
            )
          )
        )
      )
    )

  // tournaments are events: structured data makes them eligible for event rich results
  private def eventJsonLd(tour: Tournament)(implicit ctx: Context) =
    raw(
      s"""<script type="application/ld+json">${safeJsonValue(
          Json.obj(
            "@context"            -> "https://schema.org",
            "@type"               -> "Event",
            "name"                -> s"${tour.name()} (${VariantKeys.variantName(tour.variant)})",
            "startDate"           -> tour.startsAt.toString,
            "endDate"             -> tour.finishesAt.toString,
            "eventAttendanceMode" -> "https://schema.org/OnlineEventAttendanceMode",
            "eventStatus"         -> "https://schema.org/EventScheduled",
            "location"            -> Json.obj(
              "@type" -> "VirtualLocation",
              "url"   -> s"$netBaseUrl${routes.Tournament.show(tour.id).url}"
            ),
            "organizer" -> Json.obj(
              "@type" -> "Organization",
              "name"  -> "PlayStrategy",
              "url"   -> netBaseUrl
            ),
            "description" -> tour.description.orElse(tour.spotlight.map(_.description)).getOrElse(
              s"${VariantKeys.variantName(tour.variant)} ${tour.clock.show} arena tournament on PlayStrategy"
            ),
            "url"       -> s"$netBaseUrl${routes.Tournament.show(tour.id).url}",
            "isAccessibleForFree" -> true
          )
        )}</script>"""
    )
}
