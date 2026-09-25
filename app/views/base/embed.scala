package views.html.base

import lila.app.templating.Environment.*
import lila.app.ui.EmbedConfig
import lila.app.ui.ScalatagsTemplate.*
import lila.pref.SoundSet

object embed {

  import EmbedConfig.implicits.*

  // boardFamily: the family this embed draws, whose sprite blocks the paint. The other
  // thirteen are 89% of an embed's render-blocking CSS and match nothing on it.
  def apply(title: String, cssModule: String, boardFamily: Option[String] = None)(
      body: Modifier*
  )(implicit config: EmbedConfig) =
    frag(
      layout.bits.doctype,
      layout.bits.htmlTag(using config.lang)(
        head(
          layout.bits.charset,
          layout.bits.viewport,
          layout.bits.metaCsp(basicCsp.withNonce(config.nonce)),
          st.headTitle(title),
          config.pieceSets.map(ps => layout.bits.pieceSprite(ps, boardFamily.contains(ps.gameFamilyName))),
          layout.bits.lazyPieceScript(config.nonce),
          cssTagWithTheme(cssModule, config.bg),
          views.html.base.layout.playstrategyFontFaceCss
        ),
        st.body(cls := s"base highlight ${config.board}")(
          layout.dataSoundSet     := SoundSet.silent.key,
          layout.dataAssetUrl     := netConfig.assetBaseUrl,
          layout.dataAssetVersion := assetVersion.value,
          layout.dataTheme        := config.bg,
          body
        )
      )
    )
}
