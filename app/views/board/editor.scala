package views.html.board

import strategygames.format.FEN
import strategygames.Situation

import lila.api.Context
import lila.app.templating.Environment.*
import lila.app.ui.ScalatagsTemplate.*
import lila.common.String.html.safeJsonValue

object editor {

  def apply(
      sit: Situation,
      fen: FEN,
      variant: strategygames.variant.Variant,
      positionsJson: String,
      positionsByVariantJson: String,
      orientation: Option[String] = None
  )(implicit ctx: Context) =
    views.html.base.layout(
      title = trans.boardEditorTitle.txt(),
      boardFamily = variant.gameFamily.key.some,
      moreJs = frag(
        jsModule("editor"),
        embedJsUnsafeLoadThen(
          s"""const data=${safeJsonValue(bits.jsData(sit, fen, variant))};data.positions=$positionsJson;data.positionsByVariant=$positionsByVariantJson;
          data.options = data.options || {};
          ${orientation.fold("")(o => s"data.options.orientation='${o}';\n")}
      PlayStrategyEditor(document.getElementById('board-editor'), data);"""
        )
      ),
      moreCss = cssTag("editor"),
      zoomable = true,
      openGraph = lila.app.ui
        .OpenGraph(
          title = trans.boardEditorTitle.txt(),
          url = s"$netBaseUrl${routes.Editor.index.url}",
          description = trans.boardEditorDescription.txt()
        )
        .some,
      // one URL, whatever position or ?variant= the visitor arrived with
      canonicalPath = routes.Editor.index.url.some
    )(
      main(id := "board-editor")(
        div(cls := s"board-editor variant-${variant.key}")(
          div(cls := "spare"),
          div(cls := "main-board")(chessgroundBoard),
          div(cls := "spare")
        )
      )
    )
}
