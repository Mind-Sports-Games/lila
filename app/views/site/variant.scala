package views
package html.site

import lila.api.Context
import lila.app.templating.Environment.*
import lila.app.ui.ScalatagsTemplate.*
import lila.i18n.VariantKeys

import strategygames.variant.Variant
import views.html.library.bits as libraryBits

object variant {

  def show(
      doc: io.prismic.Document,
      resolver: io.prismic.DocumentLinkResolver,
      variant: Variant
  )(implicit ctx: Context) =
    layout(
      active = variant.some,
      title = libraryBits.rulesTitle(variant),
      klass = "box-pad page variant",
      openGraph = lila.app.ui
        .OpenGraph(
          title = libraryBits.rulesTitle(variant),
          url = s"$netBaseUrl${routes.Page.variant(variant.key).url}",
          description = libraryBits.rulesDescription(variant)
        )
        .some
    )(
      div(cls := "variant__head")(
        h1(cls := "text", dataIcon := variant.perfIcon)(VariantKeys.variantName(variant)),
        a(cls := "button", href := s"/?variant=${variant.key}#game")(trans.createAGame())
      ),
      div(cls := "variant__intro")(
        h2(cls := "headline")(VariantKeys.variantTitle(variant)),
        libraryBits.parentVariant(variant).map { parent =>
          p(
            trans.variantOf(
              libraryBits.searchName(variant),
              a(href := routes.Page.variant(parent.key))(libraryBits.searchName(parent))
            )
          )
        }
      ),
      div(cls := "body")(raw(~doc.getHtml("pages.content", resolver))),
      libraryBits.hasLibraryPages(variant).option(
        p(cls := "variant__about")(
          trans.playVariantOnlineFree(libraryBits.nameWithAlias(variant)),
          " ",
          VariantKeys.variantTitle(variant),
          ". ",
          a(href := routes.Library.variant(variant.key))(
            trans.allAboutX(libraryBits.searchName(variant)),
            " »"
          )
        )
      )
    )

  def home(
      doc: io.prismic.Document,
      resolver: io.prismic.DocumentLinkResolver
  )(implicit ctx: Context) =
    layout(
      title = "Board game rules — how to play every game on PlayStrategy",
      klass = "variants",
      openGraph = page
        .bodyDescription(doc, resolver)
        .map(description =>
          lila.app.ui.OpenGraph(
            title = "Board game rules — how to play every game on PlayStrategy",
            url = s"$netBaseUrl${routes.Page.variantHome.url}",
            description = description
          )
        )
    )(
      h1("PlayStrategy Games"),
      div(cls := "body box__pad")(raw(~doc.getHtml("pages.content", resolver))),
      div(cls := "variants")(
        Variant.all.filterNot(_.fromPositionVariant) map { v =>
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
      openGraph: Option[lila.app.ui.OpenGraph]
  )(body: Modifier*)(implicit ctx: Context) =
    views.html.base.layout(
      title = title,
      moreCss = cssTag("variant"),
      openGraph = openGraph
    )(
      main(cls := "page-menu")(
        st.aside(cls := "page-menu__menu subnav")(
          Variant.all.filterNot(_.fromPositionVariant) map { v =>
            a(
              cls      := List("text" -> true, "active" -> active.contains(v)),
              href     := routes.Page.variant(v.key),
              dataIcon := v.perfIcon
            )(VariantKeys.variantName(v))
          }
        ),
        div(cls := s"page-menu__content box $klass")(body)
      )
    )
}
