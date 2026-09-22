package views.html.study

import strategygames.variant.Variant

import lila.api.Context
import lila.app.templating.Environment.*
import lila.app.ui.ScalatagsTemplate.*
import lila.common.paginator.Paginator
import lila.study.Order
import lila.study.Study.WithChaptersAndLiked

object variant {

  // the studies with a chapter in the variant, linked from the game's library page
  def show(variant: Variant, pag: Paginator[WithChaptersAndLiked], order: Order)(implicit ctx: Context) = {
    val title = trans.variantStudies.txt(views.html.library.bits.searchName(variant))
    views.html.base.layout(
      title = title,
      robots = pag.currentPage == 1,
      moreCss = cssTag("study.index"),
      wrapClass = "full-screen-force",
      moreJs = infiniteScrollTag
    ) {
      val active = s"variant:${variant.key}"
      val url    = (o: String) => routes.Study.byVariant(variant.key, o)
      main(cls := "page-menu")(
        views.html.study.list.menu(active, order),
        main(cls := "page-menu__content study-index box")(
          div(cls := "box__top")(
            h1(title),
            bits.orderSelect(order, active, url),
            bits.newForm()
          ),
          views.html.study.list.paginate(pag, url(order.key))
        )
      )
    }
  }
}
