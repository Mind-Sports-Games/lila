package views
package html.site

import controllers.routes
import lila.api.Context
import lila.app.templating.Environment._
import lila.app.ui.ScalatagsTemplate._
import lila.i18n.VariantKeys

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
        cls := "variants-all init"
      )(
        h1(cls := "variants-title")(
          s"${VariantKeys.variantName(variant)}"
        )
      )
    )

  def home(implicit ctx: Context) =
    views.html.base.layout(
      title = "Variants",
      moreCss = cssTag("variants"),
      //moreJs = jsModule("variants"),
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
        cls := "variants-all init"
      )(
        h1(cls := "variants-title")("Games on PlayStrategy"),
        div(cls := "variants-grid")(
          Variant.all.filterNot(_.fromPositionVariant) map { v =>
            a(cls := "variant", href := routes.Variants.variant(v.key), dataIcon := v.perfIcon)(
              div(cls := "variant-name")(VariantKeys.variantName(v))
            )
          }
        )
      )
    )

}
