package views.html
package base

import play.api.libs.json.{ JsObject, Json }
import strategygames.GameGroup
import strategygames.variant.Variant

import lila.api.Context
import lila.app.templating.Environment.*
import lila.app.ui.ScalatagsTemplate.*
import lila.i18n.VariantKeys

object gamePicker {

  case class Choice(key: String, name: String, icon: String, variant: Variant, href: String)

  def chips(choices: List[Choice], chosen: String)(implicit ctx: Context): Frag = {
    val favs   = favVariants
    val groups = displayedGameGroups.flatMap { g =>
      val offered = choices.filter(c => g.variants.contains(c.variant))
      offered.headOption.map(first => (g, offered, first))
    }
    val open = groups.find(_._2.exists(_.key == chosen))
    frag(
      div(cls := "series-links series-links__groups")(
        groups.map { case (g, _, first) =>
          a(
            cls := List(
              "text"   -> true,
              "group"  -> true,
              "active" -> open.exists(_._1 == g)
            ) ::: favMark(g.variants, favs),
            dataIcon := gameGroupIcon(g),
            href     := first.href
          )(VariantKeys.gameGroupName(g))
        }
      ),
      open.collect {
        case (_, offered, _) if offered.sizeIs > 1 =>
          div(cls := "series-links series-links__variants")(
            offered.map { c =>
              a(
                cls      := List("text" -> true, "active" -> (c.key == chosen), "fav" -> favs(c.variant.key)),
                dataIcon := c.icon,
                href     := c.href
              )(c.name)
            }
          )
      }
    )
  }

  private def slots(variants: List[Variant], everySlot: Boolean): List[(GameGroup, List[(Variant, Boolean)])] = {
    val offered = variants.toSet
    gamesByGroup.flatMap { case (g, all) =>
      val games = all.map(v => v -> offered(v))
      if (everySlot) (g -> games).some
      else games.filter(_._2).some.filter(_.nonEmpty).map(g -> _)
    }
  }

  private lazy val gamesByGroup: List[(GameGroup, List[Variant])] =
    displayedGameGroups.map { g =>
      val inGroup = g.variants.toSet
      g -> variantsInSetupOrder.filter(v => inGroup(v) && !v.fromPositionVariant)
    }

  def json(variants: List[Variant], everySlot: Boolean, offUnsupported: Boolean = true)(implicit
      ctx: Context
  ): JsObject =
    Json.obj(
      "labels" -> Json
        .obj("group" -> trans.gameGroup.txt(), "variant" -> trans.variant.txt())
        .add("off" -> offUnsupported.option(trans.notSupportedYet.txt())),
      "groups" -> slots(variants, everySlot).map { case (g, games) =>
        Json.obj(
          "key"         -> g.key,
          "name"        -> VariantKeys.gameGroupName(g),
          "icon"        -> gameGroupIcon(g),
          "variantKeys" -> g.variants.filterNot(_.fromPositionVariant).map(_.key),
          "games" -> games.map { case (v, offered) =>
            Json
              .obj("key" -> v.key, "name" -> VariantKeys.variantName(v), "icon" -> v.perfIcon.toString)
              .add("off" -> !offered)
          }
        )
      }
    )

  def grid(
      variants: List[Variant],
      chosen: Variant,
      link: Variant => String,
      everySlot: Boolean,
      groupsOpen: Boolean
  )(implicit ctx: Context): Frag = {
    val favs        = favVariants
    val groups      = slots(variants, everySlot)
    val chosenGroup = groups.collectFirst { case (g, games) if games.contains(chosen -> true) => g }
    def row(kind: String, open: Boolean, label: Frag, value: Option[Frag]) =
      button(tpe := "button", cls := List(s"game-grid__row game-grid__row--$kind" -> true, "open" -> open))(
        span(cls := "game-grid__label")(label),
        value
      )
    def label(name: String)             = name.replace("/", "/\u200b")
    def off(icon: String, name: String) =
      span(cls := "game-grid__tile disabled", dataIcon := icon, title := trans.notSupportedYet.txt())(name)
    div(cls := "game-grid game-grid--static")(
      row(
        "group",
        groupsOpen,
        trans.gameGroup(),
        chosenGroup.map { g =>
          span(cls := ("game-grid__value" -> true) :: favMark(g.variants, favs), dataIcon := gameGroupIcon(g))(
            VariantKeys.gameGroupName(g)
          )
        }
      ),
      div(cls := List("game-grid__panel" -> true, "open" -> groupsOpen))(
        div(cls := "game-grid__tiles")(
          groups.map { case (g, games) =>
            val name = label(VariantKeys.gameGroupName(g))
            val mods: Seq[Modifier] = Seq(
              cls := List("game-grid__tile" -> true, "open" -> chosenGroup.contains(g)) ::: favMark(g.variants, favs),
              dataIcon := gameGroupIcon(g)
            )
            games match {
              case _ if !games.exists(_._2) => off(gameGroupIcon(g), name)
              case List((sole, _))         => a(href := link(sole))(mods*)(name)
              case _ => button(tpe := "button", attr("data-group") := g.key)(mods*)(name)
            }
          }
        )
      ),
      row(
        "game",
        true,
        trans.variant(),
        chosenGroup.map { _ =>
          span(cls := List("game-grid__value" -> true, "fav" -> favs(chosen.key)), dataIcon := chosen.perfIcon.toString)(
            VariantKeys.variantName(chosen)
          )
        }
      ),
      div(cls := "game-grid__panel open")(
        groups.map { case (g, games) =>
          div(
            cls                := List("game-grid__tiles game-grid__games" -> true, "open" -> chosenGroup.contains(g)),
            attr("data-group") := g.key
          )(
            games.map {
              case (v, true) =>
                a(
                  cls      := List("game-grid__tile" -> true, "chosen" -> (v == chosen), "fav" -> favs(v.key)),
                  dataIcon := v.perfIcon.toString,
                  href     := link(v)
                )(label(VariantKeys.variantName(v)))
              case (v, false) => off(v.perfIcon.toString, label(VariantKeys.variantName(v)))
            }
          )
        }
      )
    )
  }
}
