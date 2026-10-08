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
      soleBoardFamily = true,
      namePieceSets = true,
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
      frag(
        // outside the element the editor empties on mount
        h1(cls := "offscreen")(trans.boardEditor()),
        main(id := "board-editor")(
          div(cls := s"board-editor variant-${variant.key}")(
            div(cls := "spare"),
            div(cls := "main-board")(chessgroundBoard),
            div(cls := "spare")
          ),
          // the editor lifts this into its grid, and fetches the text again when the variant changes
          st.section(cls := "editor__about")(about(variant))
        )
      )
    )

  def about(variant: strategygames.variant.Variant)(implicit ctx: Context): Frag =
    frag(
      h2(trans.aboutX(views.html.library.bits.searchName(variant))),
      p(views.html.library.bits.objectiveSentence(variant)),
      p(views.html.library.bits.editorDescription(variant)),
      views.html.library.bits.hasLibraryPages(variant).option(
        p(cls := "editor__about__links")(
          a(cls := "button", href := s"/?variant=${variant.key}#game")(trans.createAGame()),
          a(cls := "editor__about__library", href := routes.Library.variant(variant.key))(
            trans.aboutX(views.html.library.bits.searchName(variant))
          )
        )
      )
    )
}
