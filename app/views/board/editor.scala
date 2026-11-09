package views.html.board

import strategygames.format.FEN
import strategygames.Situation
import strategygames.variant.Variant

import play.api.libs.json.{ JsArray, Json }

import lila.api.Context
import lila.i18n.VariantKeys
import lila.app.templating.Environment.*
import lila.app.ui.ScalatagsTemplate.*
import lila.common.String.html.safeJsonValue
import views.html.library.bits as libraryBits

object editor {

  // the games the editor handles: its select, in this order, and a page of its own for each
  val handledVariants: List[Variant] = List(
    "standard",
    "antichess",
    "atomic",
    "crazyhouse",
    "horde",
    "kingOfTheHill",
    "racingKings",
    "threeCheck",
    "fiveCheck",
    "monster",
    "minibreakthroughtroyka",
    "breakthroughtroyka",
    "linesOfAction",
    "scrambledEggs",
    "flipello",
    "flipello10",
    "antiflipello",
    "octagonflipello",
    "xiangqi",
    "minixiangqi",
    "amazons",
    "abalone",
    "grandabalone"
  ).flatMap(Variant.byKey.get)

  val handledKeys: Set[String] = handledVariants.map(_.key).toSet

  def apply(
      sit: Situation,
      fen: FEN,
      variant: Variant,
      positionsJson: String,
      positionsByVariantJson: String,
      orientation: Option[String] = None
  )(implicit ctx: Context) = {
    // only a variant the select offers has a page of its own; any other (?variant=) is /editor's
    val (pageTitle, pageDescription, canonical) =
      if (variant.key != "standard" && handledKeys(variant.key)) {
        val name = VariantKeys.variantName(variant)
        (
          trans.variantBoardEditorTitle.txt(name),
          trans.variantBoardEditorDescription.txt(name),
          routes.Editor.variant(variant.key).url
        )
      } else (trans.boardEditorTitle.txt(), trans.boardEditorDescription.txt(), routes.Editor.index.url)
    val handledVariantsJson = safeJsonValue(
      JsArray(handledVariants.map(v => Json.obj("key" -> v.key, "name" -> VariantKeys.variantName(v))))
    )
    views.html.base.layout(
      title = pageTitle,
      boardFamily = variant.gameFamily.key.some,
      soleBoardFamily = true,
      namePieceSets = true,
      moreJs = frag(
        jsModule("editor"),
        embedJsUnsafeLoadThen(
          s"""const data=${safeJsonValue(bits.jsData(sit, fen, variant))};data.positions=$positionsJson;data.positionsByVariant=$positionsByVariantJson;data.handledVariants=$handledVariantsJson;
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
        h1(cls := "offscreen")(pageTitle),
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

  def about(variant: Variant)(implicit ctx: Context): Frag =
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
