package lila.i18n

import play.api.i18n.Lang

import strategygames.variant.Variant
import strategygames.{ GameFamily, GameGroup }

object VariantKeys {

  def variantName(variant: Variant)(implicit lang: Lang = defaultLang) =
    new I18nKey(s"variantName:${variant.key}").txt()

  def variantShortName(variant: Variant)(implicit lang: Lang = defaultLang) =
    new I18nKey(s"variantShortName:${variant.key}").txt()

  def variantTitle(variant: Variant)(implicit lang: Lang = defaultLang) =
    new I18nKey(s"variantTitle:${variant.key}").txt()

  def gameFamilyName(gameFamily: GameFamily)(implicit lang: Lang = defaultLang) =
    new I18nKey(s"variantName:${gameFamily.key match {
      case "loa" => "linesOfAction"
      case key   => key
    }}").txt()

  def gameGroupName(gameGroup: GameGroup)(implicit lang: Lang = defaultLang) =
    new I18nKey(s"variantName:${gameGroup.key match {
      case "loa" => "linesOfAction"
      case key   => key
    }}").txt()

}
