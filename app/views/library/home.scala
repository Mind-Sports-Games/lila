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

object home {

  def apply(data: List[(String, String, Long)])(implicit ctx: Context) =
    views.html.base.layout(
      title = "Library of Games",
      moreCss = cssTag("library"),
      moreJs = frag(
        jsModule("library"),
        jsTag("chart/library.js"),
        embedJsUnsafeLoadThen(s"""window.libraryChartData = ${safeJsonValue(
          Json.obj(
            "freq" -> bits.transformData(data),
            "i18n" -> i18nJsObject(bits.i18nKeys),
            "variantNames" -> Json.obj(
              Variant.all.map(v =>
                s"${v.gameFamily.id}_${v.id}" -> Json.toJsFieldJsValueWrapper(VariantKeys.variantName(v))
              ): _*
            )
          )
        )};"""),
        embedJsUnsafeLoadThen(s"""playstrategy.libraryChart(window.libraryChartData)""")
      ),
      openGraph = lila.app.ui
        .OpenGraph(
          title = "Library of Games",
          url = s"$netBaseUrl${routes.Library.home.url}",
          description = "Games you can play on PlayStrategy."
        )
        .some,
      zoomable = true
    )(
      main(
        id := "library-section",
        cls := "library-all"
      )(
        h1(cls := "library-title color-choice")("Library of Games"),
        div(cls := "gamegroup-choice")(
          div(cls := "section-title")("Game Group"),
          div(cls := "gamegroup-icons")(translatedGameGroupIconChoices map { case (id, icon, hint) =>
            (button(cls := "gamegroup", value := id, dataIcon := icon)(hint))
          })
        ),
        div(cls := "variants-choice")(
          div(cls := "section-title")("Variant"),
          div(cls := "variants-icons")(translatedVariantIconChoices.filter { case (id, _, _) =>
            id != "0_3" //from position
          } map { case (id, icon, name) =>
            (button(
              cls := "variant",
              dataIcon := icon,
              value := id,
              href := routes.Library.variant(variantKey(id))
            )(name))
          })
        ),
        div(id := "library_chart_area")(
          div(id := "library_chart")(spinner)
        )
      )
    )

}
