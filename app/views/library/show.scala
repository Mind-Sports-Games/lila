package views.html.library

import play.api.libs.json.Json

import controllers.routes
import lila.api.Context
import lila.app.templating.Environment._
import lila.app.ui.ScalatagsTemplate._
import lila.common.String.html.safeJsonValue
import lila.i18n.{ I18nKeys => trans, VariantKeys }
import play.api.i18n.Lang

import strategygames.variant.Variant
import strategygames.GameLogic

object show {

  def apply(
      variant: Variant,
      data: List[(String, String, Long)]
  )(implicit ctx: Context) =
    views.html.base.layout(
      title = s"${VariantKeys.variantName(variant)} • ${VariantKeys.variantTitle(variant)}",
      moreCss = cssTag("library"),
      moreJs = frag(
        //jsModule("library"),
        jsTag("chart/library.js"),
        embedJsUnsafeLoadThen(s"""playstrategy.libraryChart(${safeJsonValue(
          Json.obj(
            "freq" -> bits.transformData(data).filter(_._2 == s"${variant.gameFamily.id}_${variant.id}"),
            "i18n" -> i18nJsObject(bits.i18nKeys),
            "variantNames" -> Json.obj(
              Variant.all.map(v =>
                s"${v.gameFamily.id}_${v.id}" -> Json.toJsFieldJsValueWrapper(VariantKeys.variantName(v))
              ): _*
            )
          )
        )})""")
      ),
      openGraph = lila.app.ui
        .OpenGraph(
          title = "Library of Games",
          url = s"$netBaseUrl${routes.Library.home.url}",
          description = s"Play ${VariantKeys.variantTitle(variant)} on PlayStrategy."
        )
        .some,
      zoomable = true
    )(
      main(
        id := "library-section",
        cls := "library-all"
      )(
        div(cls := "library-header color-choice")(
          h1(cls := "library-title", dataIcon := variant.perfIcon)(
            span(s"${VariantKeys.variantName(variant)}")
          ),
          div(cls := "library-links")(
            a(cls := "library-rules", href := s"${routes.Page.variant(variant.key)}", target := "_blank")(
              "Rules"
            ),
            bits.studyLink(variant).map { studyId =>
              a(cls := "library-tutorial", href := s"${routes.Study.show(studyId)}", target := "_blank")(
                "Tutorial"
              )
            },
            a(cls := "library-editor", href := s"${routes.Editor.index}?variant=${variant.key}")(
              "Editor"
            ),
            a(cls := "library-analysis", href := routes.UserAnalysis.parseArg(variant.key))(
              "Analysis"
            ),
            ctx.userId.map(user =>
              a(
                cls := "library-mystats",
                href := routes.User.perfStat(user, variant.key),
                target := "_blank"
              )(
                "My Stats"
              )
            ),
            a(href := routes.Library.home.url, cls := "library-back")("Library")
          )
        ),
        div(cls := "start")(
          a(
            href := routes.Setup.gameForm(none),
            cls := List(
              "button button-color-choice config_game" -> true
              //"disabled"                               -> currentGame.isDefined
            ),
            trans.createAGame()
          )
        ),
        div(id := "library_chart_area")(
          div(id := "library_chart")(spinner)
        )
      )
    )
}
