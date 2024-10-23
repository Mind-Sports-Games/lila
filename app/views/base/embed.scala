package views.html.base

import lila.app.templating.Environment._
import lila.app.ui.EmbedConfig
import lila.app.ui.ScalatagsTemplate._
import lila.pref.SoundSet

object embed {

  import EmbedConfig.implicits._

  def apply(title: String, cssModule: String)(body: Modifier*)(implicit config: EmbedConfig) =
    frag(
      layout.bits.doctype,
      layout.bits.htmlTag(config.lang)(
        head(
          layout.bits.charset,
          layout.bits.viewport,
          layout.bits.metaCsp(basicCsp withNonce config.nonce),
          st.headTitle(title),
          config.pieceSets.map(ps => layout.bits.pieceSprite(ps)),
          cssTagWithTheme(cssModule, config.bg),
          views.html.base.layout.playstrategyFontFaceCss
        ),
        st.body(cls := s"base highlight ${config.board}")(
          layout.dataSoundSet := SoundSet.silent.key,
          layout.dataAssetUrl := netConfig.assetBaseUrl,
          layout.dataAssetVersion := assetVersion.value,
          layout.dataTheme := config.bg,
          body
        )
      )
    )
}
