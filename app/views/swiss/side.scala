package views
package html.swiss

import controllers.routes

import strategygames.variant.Variant
import strategygames.format.FEN

import lila.api.Context
import lila.app.templating.Environment._
import lila.app.ui.ScalatagsTemplate._
import lila.common.String.html.markdownLinksOrRichText
import lila.swiss.{ Swiss, SwissCondition }
import lila.common.Form
import lila.i18n.VariantKeys

object side {

  private val separator = " • "

  def apply(
      s: Swiss,
      verdicts: SwissCondition.All.WithVerdicts,
      streamers: List[lila.user.User.ID],
      chat: Boolean
  )(implicit
      ctx: Context
  ) =
    frag(
      div(cls := "swiss__meta")(
        st.section(dataIcon := (if (s.isMedley) "5" else s.perfType.iconChar.toString))(
          div(
            p(
              s.clock.show,
              separator,
              if (s.isMedley) {
                views.html.game.bits.medleyLink
              } else if (s.variant.exotic) {
                views.html.game.bits.variantLink(
                  s.variant,
                  if (s.variant == Variant.Chess(strategygames.chess.variant.KingOfTheHill))
                    VariantKeys.variantShortName(s.variant)
                  else VariantKeys.variantName(s.variant)
                )
              } else s.perfType.trans,
              separator,
              if (s.settings.usingDrawTables) trans.swiss.usingDrawTables(),
              if (s.settings.usingDrawTables) separator,
              if (s.settings.rated) trans.ratedTournament() else trans.casualTournament(),
              separator,
              a(href := routes.Swiss.home)("Swiss")
            ),
            p(
              span(cls := "swiss__meta__round")(
                s"${s.round}/${s.settings.nbRounds}"
              ),
              span(cls := "swiss__meta__rounds")(
                " rounds",
                if (s.settings.isBestOfX) {
                  s" (best of ${s.settings.nbGamesPerRound} games"
                } else if (s.settings.isPlayX) {
                  s" (${s.settings.nbGamesPerRound} games per round"
                },
                if (s.settings.isMatchScore)
                  a(href := s"${routes.Swiss.home}#faqMatchScore")(" using match score"),
                if (s.settings.isBestOfX || s.settings.isPlayX) ")"
                else ""
              ),
              (isGranted(_.ManageTournament) || (ctx.userId.has(s.createdBy) && !s.isFinished)) option frag(
                " ",
                a(href := routes.Swiss.edit(s.id.value), title := "Edit tournament")(iconTag("%"))
              )
            ),
            bits.showInterval(s),
            p(bits.showHalfwayBreak(s))
          )
        ),
        s.isMedley option views.html.swiss.bits.medleyGames(
          s.medleyGameGroupsString.getOrElse(""),
          s.settings.medleyVariants.getOrElse(List[Variant]()),
          s.isCreated,
          s.isFinished,
          s.settings.nbRounds
        ),
        s.settings.description map { d =>
          st.section(cls := "description")(markdownLinksOrRichText(d))
        },
        s.looksLikePrize option views.html.tournament.bits.userPrizeDisclaimer(s.createdBy),
        s.settings.position.flatMap(lila.tournament.Thematic.byFen) map { pos =>
          div(
            a(targetBlank, href := pos.url)(strong(pos.eco), " ", pos.name),
            " • ",
            views.html.base.bits.fenAnalysisLink(FEN.Chess(pos.fen))
          )
        } orElse s.settings.position.map { fen =>
          div(
            "Custom position • ",
            views.html.base.bits.fenAnalysisLink(fen)
          )
        },
        !s.isFinished option s.trophy1st.map { trophy1st =>
          table(cls := "trophyPreview")(
            tr(
              td(
                img(cls := "customTrophy", src := assetUrl(s"images/trophy/${trophy1st}.png"))
              ),
              s.trophy2nd.map { trophy2nd =>
                td(
                  img(cls := "customTrophy", src := assetUrl(s"images/trophy/${trophy2nd}.png"))
                )
              },
              s.trophy3rd.map { trophy3rd =>
                td(
                  img(cls := "customTrophy", src := assetUrl(s"images/trophy/${trophy3rd}.png"))
                )
              }
            ),
            tr(
              td("1st Place"),
              s.trophy2nd.map { _ => td("2nd Place") },
              s.trophy3rd.map { _ => td("3rd Place") }
            )
          )
        },
        teamLink(s.teamId),
        if (verdicts.relevant)
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
              verdicts.list.sizeIs < 2 option p(trans.conditionOfEntry()),
              verdicts.list map { v =>
                p(
                  cls := List(
                    "condition" -> true,
                    "accepted"  -> (ctx.isAuth && v.verdict.accepted),
                    "refused"   -> (ctx.isAuth && !v.verdict.accepted)
                  ),
                  title := v.verdict.reason.map(_(ctx.lang))
                )(v.condition.name(s.perfType))
              }
            )
          )
        else br,
        absClientDateTime(s.startsAt)
      ),
      streamers.nonEmpty option div(cls := "context-streamers")(
        streamers map views.html.streamer.bits.contextual
      ),
      chat option views.html.chat.frag
    )
}
