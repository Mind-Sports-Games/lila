package views.html

import strategygames.GameGroup
import strategygames.variant.Variant

import lila.api.Context
import lila.app.templating.Environment.*
import lila.app.ui.ScalatagsTemplate.*
import lila.i18n.VariantKeys

object favourites {

  val groups: List[(GameGroup, List[Variant])] =
    displayedGameGroups.map(g => g -> g.variants.filterNot(_.fromPositionVariant))

  def groupByKey(key: String): Option[(GameGroup, List[Variant])] = groups.find(_._1.key == key)

  def variantByKey(key: String): Option[Variant] = groups.flatMap(_._2).find(_.key == key)

  private val detailsTag  = tag("details")
  private val summaryTag  = tag("summary")
  private val dataUrl     = attr("data-url")
  private val dataKey     = attr("data-key")
  private val ariaPressed = attr("aria-pressed")

  def apply(favs: Set[String])(implicit ctx: Context) =
    views.html.base.layout(
      title = trans.favouriteGames.txt(),
      robots = false,
      moreCss = cssTag("favourites"),
      moreJs = jsModule("favourites")
    ) {
      main(
        cls                 := "favourites page-small box box-pad",
        attr("data-add")    := trans.addToFavourites.txt(),
        attr("data-remove") := trans.removeFromFavourites.txt()
      )(
        h1(trans.favouriteGames()),
        p(cls := "favourites__intro")(trans.favouriteGamesIntro()),
        groups.map {
          case (g, v :: Nil) =>
            div(cls := "favourites__group")(
              div(cls := "favourites__head")(
                starButton(favs(v.key), some = false, group = false)(
                  dataUrl := routes.Favourites.variant(v.key).url,
                  dataKey := v.key
                ),
                groupName(g),
                span(cls := "favourites__count")(trans.oneGame())
              )
            )
          case (g, variants) =>
            val nb    = variants.count(v => favs(v.key))
            val total = variants.size
            val all   = trans.allNbGames.pluralSameTxt(total)
            detailsTag(cls := "favourites__group", (nb > 0).option(attr("open").empty))(
              summaryTag(cls := "favourites__head")(
                starButton(nb == total, some = nb > 0 && nb < total, group = true)(
                  dataUrl := routes.Favourites.group(g.key).url
                ),
                groupName(g),
                span(
                  cls              := "favourites__count",
                  attr("data-of")  := trans.nbOfNbGames.pluralTxt(total, "%s", total),
                  attr("data-all") := all
                )(if (nb == total) all else trans.nbOfNbGames.pluralTxt(total, nb, total))
              ),
              div(cls := "series-links")(variants.map { v =>
                val on = favs(v.key)
                button(
                  tpe         := "button",
                  cls         := List("text" -> true, "fav" -> on),
                  dataIcon    := v.perfIcon,
                  dataUrl     := routes.Favourites.variant(v.key).url,
                  dataKey     := v.key,
                  ariaPressed := on.toString
                )(VariantKeys.variantName(v))
              })
            )
        }
      )
    }

  private def groupName(g: GameGroup)(implicit ctx: Context) =
    span(cls := "favourites__name text", dataIcon := gameGroupIcon(g))(VariantKeys.gameGroupName(g))

  private def starButton(on: Boolean, some: Boolean, group: Boolean)(mods: Modifier*)(implicit
      ctx: Context
  ) =
    button(mods*)(
      tpe         := "button",
      cls         := List("favourites__star" -> true, "favourites__group-star" -> group),
      ariaPressed := on.toString,
      title       := (if (on) trans.removeFromFavourites.txt() else trans.addToFavourites.txt()),
      favStar(on, some)
    )
}
