package views
package html.site

import controllers.routes
import lila.api.Context
import lila.app.templating.Environment._
import lila.app.ui.ScalatagsTemplate._
import lila.i18n.VariantKeys

import strategygames.variant.Variant

object variant {

  def show(
      doc: io.prismic.Document,
      resolver: io.prismic.DocumentLinkResolver,
      variant: Variant
  )(implicit ctx: Context) =
    layout(
      active = variant.some,
      title = s"${VariantKeys.variantName(variant)} • ${VariantKeys.variantTitle(variant)}",
      klass = "box-pad page variant"
    )(
      h1(cls := "text", dataIcon := variant.perfIcon)(VariantKeys.variantName(variant)),
      h2(cls := "headline")(VariantKeys.variantTitle(variant)),
      div(cls := "body")(raw(~doc.getHtml("pages.content", resolver)))
    )

  def home(
      doc: io.prismic.Document,
      resolver: io.prismic.DocumentLinkResolver
  )(implicit ctx: Context) =
    layout(
      title = "PlayStrategy Games",
      klass = "variants"
    )(
      h1("PlayStrategy Games"),
      div(cls := "body box__pad")(raw(~doc.getHtml("pages.content", resolver))),
      div(cls := "variants")(
        Variant.all.filterNot(_.fromPosition) map { v =>
          a(cls := "variant text box__pad", href := routes.Page.variant(v.key), dataIcon := v.perfIcon)(
            span(
              h2(VariantKeys.variantName(v)),
              h3(cls := "headline")(VariantKeys.variantTitle(v))
            )
          )
        }
      )
    )

  private def layout(
      title: String,
      klass: String,
      active: Option[Variant] = None,
      openGraph: Option[lila.app.ui.OpenGraph] = None
  )(body: Modifier*)(implicit ctx: Context) =
    views.html.base.layout(
      title = title,
      moreCss = cssTag("variant"),
      openGraph = openGraph
    )(
      main(cls := "page-menu")(
        st.aside(cls := "page-menu__menu subnav")(
          Variant.all.filterNot(_.fromPosition) map { v =>
            a(
              cls := List("text" -> true, "active" -> active.has(v)),
              href := routes.Page.variant(v.key),
              dataIcon := v.perfIcon
            )(v.name)
          }
        ),
        div(cls := s"page-menu__content box $klass")(body)
      )
    )
}
