package views
package html.site

import controllers.routes
import lila.api.Context
import lila.app.templating.Environment._
import lila.app.ui.ScalatagsTemplate._
import lila.i18n.{ I18nKeys => trans, VariantKeys }
import play.api.i18n.Lang

import strategygames.variant.Variant
import strategygames.GameLogic

object library {

  def show(
      variant: Variant
  )(implicit ctx: Context) =
    views.html.base.layout(
      title = s"${VariantKeys.variantName(variant)} • ${VariantKeys.variantTitle(variant)}",
      moreCss = cssTag("library"),
      //moreJs = jsModule("library"),
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
        h1(cls := "library-title color-choice", dataIcon := variant.perfIcon)(
          s"${VariantKeys.variantName(variant)}"
        )
      )
    )

  def home(data: List[(String, String, Long)])(implicit ctx: Context) =
    views.html.base.layout(
      title = "Library of Games",
      moreCss = cssTag("library"),
      moreJs = jsModule("libraryHome"),
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
              href := routes.Page.variant(variantKey(id)) //TODO routes.Library.variant(variantKey(id))
            )(name))
          })
        ),
        div(cls := "data")(
          table(cls := "variant-table")(
            thead(
              tr(
                th("Year"),
                th("Month"),
                th("Variant"),
                th("Count")
              )
            ),
            tbody(
              data.map { case (ym, lib_var, count) =>
                val Array(year, month) = ym.split("-")
                val Array(lib, varId)  = lib_var.split("_")
                tr(
                  td(year),
                  td(month),
                  td(Variant.orDefault(GameLogic(lib.toInt), varId.toInt).name),
                  td(count.toString)
                )
              }
            )
          )
        )
      )
    )

}
