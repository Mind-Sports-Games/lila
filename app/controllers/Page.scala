package controllers

import strategygames.variant.Variant
import strategygames.GameLogic

import lila.app._
import lila.i18n.VariantKeys

final class Page(
    env: Env,
    prismicC: Prismic
) extends LilaController(env) {

  val help   = menuBookmark("help")
  val tos    = menuBookmark("tos")
  val master = menuBookmark("master")

  def bookmark(name: String, active: Option[String]) =
    Open { implicit ctx =>
      pageHit
      OptionOk(prismicC getBookmark name) { case (doc, resolver) =>
        active match {
          case None       => views.html.site.page.lone(doc, resolver)
          case Some(name) => views.html.site.page.withMenu(name, doc, resolver)
        }
      }
    }

  def loneBookmark(name: String) = bookmark(name, none)
  def menuBookmark(name: String) = bookmark(name, name.some)

  def source =
    Open { implicit ctx =>
      pageHit
      OptionOk(prismicC getBookmark "source") { case (doc, resolver) =>
        views.html.site.page.source(doc, resolver)
      }
    }

  def variantHome =
    Open { implicit ctx =>
      import play.api.libs.json._
      negotiate(
        html = OptionOk(prismicC getBookmark "variant") { case (doc, resolver) =>
          views.html.site.variant.home(doc, resolver)
        },
        api = _ =>
          Ok(JsArray((Variant.all).map { v =>
            Json.obj(
              "id"   -> v.id,
              "key"  -> v.key,
              "name" -> VariantKeys.variantName(v)
            )
          })).fuccess
      )
    }

  def variant(key: String) =
    Open { implicit ctx =>
      (for {
        variant <- (Variant.all).map { v =>
          (v.key, v)
        }.toMap get key
      } yield OptionOk(prismicC getBookmark key) { case (doc, resolver) =>
        views.html.site.variant.show(doc, resolver, variant)
      }) | notFound
    }
}
