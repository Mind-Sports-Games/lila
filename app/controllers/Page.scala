package controllers

import strategygames.variant.Variant
import strategygames.GameLogic

import lila.app._
import lila.i18n.VariantKeys

final class Page(
    env: Env,
    prismicC: Prismic
) extends LilaController(env) {

  val help   = menuPage("help")
  val tos    = menuPage("terms-of-service")
  val master = menuPage("master")

  def page(uid: String, active: Option[String]) =
    Open { implicit ctx =>
      pageHit
      OptionOk(prismicC getPage uid) { case (doc, resolver) =>
        active match {
          case None      => views.html.site.page.lone(doc, resolver)
          case Some(uid) => views.html.site.page.withMenu(uid, doc, resolver)
        }
      }
    }

  def lonePage(uid: String) = page(uid, none)
  def menuPage(uid: String) = page(uid, uid.some)

  def source =
    Open { implicit ctx =>
      pageHit
      OptionOk(prismicC getPage "source") { case (doc, resolver) =>
        views.html.site.page.source(doc, resolver)
      }
    }

  def variantHome =
    Open { implicit ctx =>
      import play.api.libs.json._
      negotiate(
        html = OptionOk(prismicC getPage "variant") { case (doc, resolver) =>
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
      } yield OptionOk(prismicC getPage prismicUid(key)) { case (doc, resolver) =>
        views.html.site.variant.show(doc, resolver, variant)
      }) | notFound
    }

  // The UID field in prismic has to be unique, lowercase, and some are taken by other pages
  def prismicUid(key: String) =
    key match {
      case "breakthroughtroyka" => "breakthrough-troyka"
      case _                    => key.toLowerCase()
    }

}
