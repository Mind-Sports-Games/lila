package views.html.board

import play.api.libs.json.{ JsObject, Json }

import strategygames.variant.Variant
import strategygames.GameLogic

import lila.api.Context
import lila.app.templating.Environment._
import lila.app.ui.ScalatagsTemplate._
import lila.common.String.html.safeJsonValue
import lila.rating.PerfType.iconByVariant
import lila.i18n.VariantKeys

import controllers.routes

object userAnalysis {

  def noAnalysisVariants = List(
    Variant.Chess(strategygames.chess.variant.FromPosition),
    Variant.FairySF(strategygames.fairysf.variant.Amazons)
  )

  def analysisVariants =
    (
      Variant.all(GameLogic.Chess()) ++
        Variant.all(GameLogic.FairySF()) ++
        Variant.all(GameLogic.Samurai()) ++
        Variant.all(GameLogic.Togyzkumalak())
    )
      .filterNot(noAnalysisVariants.contains(_))

  def apply(data: JsObject, pov: lila.game.Pov, withForecast: Boolean = false)(implicit ctx: Context) =
    views.html.base.layout(
      title = trans.analysis.txt(),
      moreCss = frag(
        cssTag("analyse.free"),
        (pov.game.variant.dropsVariant && !pov.game.variant.onlyDropsVariant) option cssTag("analyse.zh"),
        withForecast option cssTag("analyse.forecast"),
        ctx.blind option cssTag("round.nvui")
      ),
      moreJs = frag(
        analyseTag,
        analyseNvuiTag,
        embedJsUnsafe(s"""playstrategy.userAnalysis=${safeJsonValue(
          Json.obj(
            "data" -> data,
            "i18n" -> userAnalysisI18n(withForecast = withForecast),
            "explorer" -> Json.obj(
              "endpoint"          -> explorerEndpoint,
              "tablebaseEndpoint" -> tablebaseEndpoint
            )
          )
        )}""")
      ),
      csp = defaultCsp.withWebAssembly.some,
      chessground = false,
      openGraph = lila.app.ui
        .OpenGraph(
          title = "Strategy games analysis board",
          url = s"$netBaseUrl${routes.UserAnalysis.index.url}",
          description = "Analyse strategy game positions and variations on an interactive board"
        )
        .some,
      zoomable = true
    ) {
      main(cls := "analyse")(
        pov.game.synthetic option st.aside(cls := "analyse__side")(
          views.html.base.bits.mselect(
            "analyse-variant",
            span(cls := "text", dataIcon := iconByVariant(pov.game.variant))(
              VariantKeys.variantName(pov.game.variant)
            ),
            analysisVariants.map { v =>
              a(
                dataIcon := iconByVariant(v),
                cls := (pov.game.variant == v).option("current"),
                href := routes.UserAnalysis.parseArg(v.key)
              )(VariantKeys.variantName(v))
            }
          )
        ),
        div(cls := "analyse__board main-board")(chessgroundBoard),
        div(cls := "analyse__tools"),
        div(cls := "analyse__controls")
      )
    }
}
