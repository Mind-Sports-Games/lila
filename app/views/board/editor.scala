package views.html.board

import strategygames.format.FEN
import strategygames.Situation

import lila.api.Context
import lila.app.templating.Environment.*
import lila.app.ui.ScalatagsTemplate.*
import lila.common.String.html.safeJsonValue
import views.html.library.bits as libraryBits

object editor {

  def apply(
      sit: Situation,
      fen: FEN,
      variant: strategygames.variant.Variant,
      positionsJson: String,
      positionsByVariantJson: String,
      orientation: Option[String] = None
  )(implicit ctx: Context) = {
    val variantName = lila.i18n.VariantKeys.variantName(variant)
    val isStandard  = variant.key == "standard"
    val pageTitle   =
      if (isStandard) trans.boardEditorTitle.txt() else trans.variantBoardEditorTitle.txt(variantName)
    val pageDescription =
      if (isStandard) trans.boardEditorDescription.txt()
      else trans.variantBoardEditorDescription.txt(variantName)
    val canonical =
      if (isStandard) routes.Editor.index.url else routes.Editor.variant(variant.key).url
    views.html.base.layout(
      title = pageTitle,
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
          title = pageTitle,
          url = s"$netBaseUrl$canonical",
          description = pageDescription
        )
        .some,
      canonicalPath = canonical.some
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
  }

  def about(variant: strategygames.variant.Variant)(implicit ctx: Context): Frag =
    frag(
      h2(
        a(href := routes.Library.variant(variant.key))(
          trans.aboutX(libraryBits.searchName(variant))
        )
      ),
      p(libraryBits.objectiveSentence(variant)),
      p(libraryBits.editorDescription(variant)),
      libraryBits.hasLibraryPages(variant).option(
        p(cls := "editor__about__links")(
          a(cls := "button", href := s"/?variant=${variant.key}#game")(trans.createAGame())
        )
      )
    )
}
