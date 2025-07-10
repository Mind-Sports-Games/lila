package views
package html.site

import controllers.routes
import lila.api.Context
import lila.app.templating.Environment._
import lila.app.ui.ScalatagsTemplate._
import lila.i18n.{ I18nKeys => trans, VariantKeys }
import play.api.i18n.Lang

import strategygames.variant.Variant

object variants {

  def show(
      variant: Variant
  )(implicit ctx: Context) =
    views.html.base.layout(
      title = s"${VariantKeys.variantName(variant)} • ${VariantKeys.variantTitle(variant)}",
      moreCss = cssTag("variants"),
      //moreJs = jsModule("variants"),
      openGraph = lila.app.ui
        .OpenGraph(
          title = "Variants",
          url = s"$netBaseUrl${routes.Variants.home.url}",
          description = s"Play ${VariantKeys.variantTitle(variant)} on PlayStrategy."
        )
        .some,
      zoomable = true
    )(
      main(
        id := "variants-section",
        cls := "variants-all"
      )(
        h1(cls := "variants-title color-choice", dataIcon := variant.perfIcon)(
          s"${VariantKeys.variantName(variant)}"
        )
      )
    )

  def home(implicit ctx: Context) =
    views.html.base.layout(
      title = "Variants",
      moreCss = cssTag("variants"),
      moreJs = jsModule("variantsHome"),
      openGraph = lila.app.ui
        .OpenGraph(
          title = "Variants",
          url = s"$netBaseUrl${routes.Variants.home.url}",
          description = "Games you can play on PlayStrategy."
        )
        .some,
      zoomable = true
    )(
      main(
        id := "variants-section",
        cls := "variants-all"
      )(
        h1(cls := "variants-title color-choice")("Games"),
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
              href := routes.Page.variant(variantKey(id)) //TODO routes.Variants.variant(variantKey(id))
            )(name))
          })
        )
      )
    )

}
