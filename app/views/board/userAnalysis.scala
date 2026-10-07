package views.html.board

import play.api.libs.json.{ JsObject, Json }

import strategygames.variant.Variant
import strategygames.GameFamily
import strategygames.GameLogic

import lila.api.Context
import lila.app.templating.Environment.*
import lila.app.ui.ScalatagsTemplate.*
import lila.common.String.html.safeJsonValue
import lila.rating.PerfType.iconByVariant
import lila.i18n.VariantKeys

object userAnalysis {

  def noAnalysisVariants = List(
    Variant.Chess(strategygames.chess.variant.FromPosition)
  )

  def analysisVariants =
    (
      Variant.all(GameLogic.Chess()) ++
        Variant.all(GameLogic.Dameo()) ++
        Variant.all(GameLogic.FairySF()) ++
        Variant.all(GameLogic.Samurai()) ++
        Variant.all(GameLogic.Togyzkumalak()) ++
        Variant.all(GameLogic.Go()) ++
        Variant.all(GameLogic.Abalone()) ++
        Variant.all(GameLogic.Backgammon())
    )
      .filterNot(noAnalysisVariants.contains(_))

  def apply(data: JsObject, pov: lila.game.Pov, withForecast: Boolean = false)(implicit ctx: Context) = {
    val variant = pov.game.variant
    // one URL per game, whatever position the visitor pasted
    val canonical =
      if (variant == Variant.libStandard(GameLogic.Chess())) routes.UserAnalysis.index.url
      else routes.UserAnalysis.parseArg(variant.key).url
    views.html.base.layout(
      title = views.html.library.bits.analysisTitle(variant),
      boardFamily = variant.gameFamily.key.some,
      soleBoardFamily = true,
      moreCss = frag(
        cssTag("analyse.free"),
        pov.game.variant.hasDetachedPocket.option(
          cssTag(
            "analyse.zh"
          )
        ),
        (pov.game.variant.gameFamily == GameFamily.Backgammon()).option(cssTag(
          "analyse.backgammon"
        )),
        withForecast.option(cssTag("analyse.forecast")),
        ctx.blind.option(cssTag("round.nvui"))
      ),
      moreJs = frag(
        analyseTag,
        analyseNvuiTag,
        embedJsUnsafe(s"""playstrategy.userAnalysis=${safeJsonValue(
            Json.obj(
              "data"     -> data,
              "i18n"     -> userAnalysisI18n(withForecast = withForecast),
              "explorer" -> Json.obj(
                "endpoint"          -> explorerEndpoint,
                "tablebaseEndpoint" -> tablebaseEndpoint
              )
            )
          )}""")
      ),
      csp = defaultCsp.withWebAssembly.some,
      openGraph = lila.app.ui
        .OpenGraph(
          title = views.html.library.bits.analysisTitle(variant),
          url = s"$netBaseUrl$canonical",
          description = views.html.library.bits.analysisDescription(variant)
        )
        .some,
      zoomable = true,
      canonicalPath = canonical.some,
      alternates = true
    ) {
      frag(
        // outside main: the analyse app empties main.analyse on mount, so a heading in there
        // would be gone before a rendering crawler saw it
        h1(cls := "offscreen")(views.html.library.bits.analysisTitle(variant)),
        main(cls := s"analyse variant-${variant.key}")(
        pov.game.synthetic.option(
          st.aside(cls := "analyse__side")(
            views.html.base.bits.mselect(
              "analyse-variant",
              span(cls := "text", dataIcon := iconByVariant(pov.game.variant))(
                VariantKeys.variantName(pov.game.variant)
              ),
              analysisVariants.map { v =>
                a(
                  dataIcon := iconByVariant(v),
                  cls      := (pov.game.variant == v).option("current"),
                  href     := routes.UserAnalysis.parseArg(v.key)
                )(VariantKeys.variantName(v))
              }
            )
          )
        ),
          div(cls := "analyse__board main-board")(chessgroundBoard),
          div(cls := "analyse__tools"),
          div(cls := "analyse__controls"),
          // the 400-odd analysis pages are otherwise a bare board, near identical to one another.
          // The analyse app empties this element on mount: it lifts the section into its grid first.
          st.section(cls := "analyse__about")(
            h2(trans.aboutX(views.html.library.bits.searchName(variant))),
            p(views.html.library.bits.objectiveSentence(variant)),
            p(views.html.library.bits.analysisDescription(variant)),
            views.html.library.bits.hasLibraryPages(variant).option(
              p(cls := "analyse__about__links")(
                // the same link as the library's and the rules page's button: the lobby's game form
                a(cls := "button", href := s"/?variant=${variant.key}#game")(trans.createAGame()),
                a(cls := "analyse__about__rules", href := routes.Page.variant(variant.key))("Rules")
              )
            )
          )
        )
      )
    }
  }
}
