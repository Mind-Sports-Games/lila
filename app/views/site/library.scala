package views
package html.site

import play.api.libs.json.Json

import controllers.routes
import lila.api.Context
import lila.app.templating.Environment._
import lila.app.ui.ScalatagsTemplate._
import lila.common.String.html.safeJsonValue
import lila.i18n.{ I18nKeys => trans, VariantKeys }
import play.api.i18n.Lang

import strategygames.variant.Variant
import strategygames.GameLogic

object library {

  def show(
      variant: Variant,
      data: List[(String, String, Long)],
      playban: Boolean
  )(implicit ctx: Context) =
    views.html.base.layout(
      title = s"${VariantKeys.variantName(variant)} • ${VariantKeys.variantTitle(variant)}",
      moreCss = cssTag("library"),
      moreJs = frag(
        //jsModule("library"),
        jsTag("chart/library.js"),
        embedJsUnsafeLoadThen(s"""playstrategy.libraryChart(${safeJsonValue(
          Json.obj(
            "freq" -> transformData(data).filter(_._2 == s"${variant.gameFamily.id}_${variant.id}"),
            "i18n" -> i18nJsObject(i18nKeys),
            "variantNames" -> Json.obj(
              Variant.all.map(v =>
                s"${v.gameFamily.id}_${v.id}" -> Json.toJsFieldJsValueWrapper(VariantKeys.variantName(v))
              ): _*
            )
          )
        )})""")
      ),
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
        div(cls := "library-header color-choice")(
          h1(cls := "library-title", dataIcon := variant.perfIcon)(
            s"${VariantKeys.variantName(variant)}"
          ),
          div(cls := "library-links")(
            a(cls := "library-rules", href := s"${routes.Page.variant(variant.key)}", target := "_blank")(
              "Rules"
            ),
            studyLink(variant).map { studyId =>
              a(cls := "library-tutorial", href := s"${routes.Study.show(studyId)}", target := "_blank")(
                "Tutorial"
              )
            },
            a(cls := "library-editor", href := s"${routes.Editor.index}?variant=${variant.key}")(
              "Editor"
            ),
            a(cls := "library-analysis", href := routes.UserAnalysis.parseArg(variant.key))(
              "Analysis"
            ),
            ctx.userId.map(user =>
              a(
                cls := "library-mystats",
                href := routes.User.perfStat(user, variant.key),
                target := "_blank"
              )(
                "My Stats"
              )
            ),
            a(href := routes.Library.home.url, cls := "library-back")("Library")
          )
        ),
        div(cls := "start")(
          a(
            href := routes.Setup.hookForm,
            cls := List(
              "button button-color-choice config_hook" -> true,
              "disabled"                               -> (playban || ctx.isBot)
              //"disabled"                               -> (playban.isDefined || currentGame.isDefined || ctx.isBot)
            ),
            trans.createAGame()
          )
        ),
        div(id := "library_chart_area")(
          div(id := "library_chart")(spinner)
        )
      )
    )

  def home(data: List[(String, String, Long)])(implicit ctx: Context) =
    views.html.base.layout(
      title = "Library of Games",
      moreCss = cssTag("library"),
      moreJs = frag(
        jsModule("libraryHome"),
        jsTag("chart/library.js"),
        embedJsUnsafeLoadThen(s"""window.libraryChartData = ${safeJsonValue(
          Json.obj(
            "freq" -> transformData(data),
            "i18n" -> i18nJsObject(i18nKeys),
            "variantNames" -> Json.obj(
              Variant.all.map(v =>
                s"${v.gameFamily.id}_${v.id}" -> Json.toJsFieldJsValueWrapper(VariantKeys.variantName(v))
              ): _*
            )
          )
        )};"""),
        embedJsUnsafeLoadThen(s"""playstrategy.libraryChart(window.libraryChartData)""")
      ),
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
              href := routes.Library.variant(variantKey(id))
            )(name))
          })
        ),
        div(id := "library_chart_area")(
          div(id := "library_chart")(spinner)
        )
      )
    )

  private def transformData(data: List[(String, String, Long)]): List[(String, String, Long)] =
    data.map { case (ym, lib_var, count) => (ym, getVaraintKey(lib_var), count) }

  private def getVaraintKey(lib_var: String) =
    lib_var.split('_') match {
      case Array(lib, id) => {
        val variant = Variant.orDefault(GameLogic(lib.toInt), id.toInt)
        s"${variant.gameFamily.id}_${variant.id}"
      }
      case _ => "0_1" //standard chess
    }

  private def studyLink(variant: Variant): Option[String] = {
    variant.key match {
      case "abalone"       => Some("AbaloneS")
      case "linesOfAction" => Some("LinesOfA")
      case _               => None
    }
  }

  private val i18nKeys =
    List(
      trans.players,
      trans.cumulative
    ).map(_.key)
}
