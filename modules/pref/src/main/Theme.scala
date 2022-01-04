package lila.pref
import strategygames.{ GameFamily }

sealed class Theme private[pref] (val name: String, val colors: Theme.HexColors, val gameFamily: Int) {

  override def toString = name

  def cssClass = s"${gameFamilyName}-${name}"

  def light = colors._1
  def dark  = colors._2
  def gameFamilyName = GameFamily(gameFamily).shortName.toLowerCase()
}

sealed trait ThemeObject {

  val all: List[Theme]

  val default: Theme

  def allByName(gf:Int) = all filter(t => t.gameFamily == gf) map { c =>
    c.name -> c
  } toMap

  def apply(name: String, gameFamily: Int = 0) = allByName(gameFamily).getOrElse(name, default)

  def unapply(full: Theme): Some[(String, Int)] = Some((full.name, full.gameFamily))

  def contains(name: String) = all map(t => t.name) contains name

}

object Theme extends ThemeObject {

  case class HexColor(value: String) extends AnyVal with StringValue
  type HexColors = (HexColor, HexColor)
  private[pref] val defaultHexColors = (HexColor("b0b0b0"), HexColor("909090"))

  val colors: Map[String, HexColors] = Map(
    "blue"   -> (HexColor("dee3e6") -> HexColor("8ca2ad")),
    "brown"  -> (HexColor("f0d9b5") -> HexColor("b58863")),
    "green"  -> (HexColor("ffffdd") -> HexColor("86a666")),
    "purple" -> (HexColor("9f90b0") -> HexColor("7d4a8d")),
    "ic"     -> (HexColor("ececec") -> HexColor("c1c18e")),
    "horsey" -> (HexColor("f1d9b6") -> HexColor("8e6547"))
  )

  lazy val default = allByName(0) get "maple" err "Can't find default theme D:"

  val defaults = GameFamily.all.map(gf => new Theme(gf.boardThemeDefault, Theme.colors.getOrElse(gf.boardThemeDefault, Theme.defaultHexColors), gf.id))

  def updateBoardTheme(currentThemes : List[Theme], theme: String, gameFamily: Int) : List[Theme] = {
    val newTheme = apply(theme, gameFamily)
    currentThemes.map{ x => x.gameFamily match {
                                case newTheme.gameFamily => newTheme
                                case _ => x
                     }}
  }

  def updateBoardTheme(currentThemes : List[Theme], theme: String, gameFamily: String) : List[Theme] = {
    val gf_id = GameFamily.all.filter(gf => gf.shortName.toLowerCase() == gameFamily)(0).id
    val newTheme = apply(theme, gf_id)
    currentThemes.map{ x => x.gameFamily match {
                                case newTheme.gameFamily => newTheme
                                case _ => x
                     }}
  }

  val all: List[Theme] = GameFamily.all.map(gf => gf.boardThemes.map(t => new Theme(t, Theme.colors.getOrElse(t, Theme.defaultHexColors), gf.id))).flatten
 
  def allOfFamily(gf: GameFamily) : List[Theme] = gf.boardThemes.map(t => new Theme(t, Theme.colors.getOrElse(t, Theme.defaultHexColors), gf.id))

}

object Theme3d extends ThemeObject {

  val all = List(
    "Black-White-Aluminium",
    "Brushed-Aluminium",
    "China-Blue",
    "China-Green",
    "China-Grey",
    "China-Scarlet",
    "China-Yellow",
    "Classic-Blue",
    "Gold-Silver",
    "Green-Glass",
    "Light-Wood",
    "Power-Coated",
    "Purple-Black",
    "Rosewood",
    "Wood-Glass",
    "Marble",
    "Wax",
    "Jade",
    "Woodi"
  ) map { name =>
    new Theme(name, Theme.defaultHexColors, 0)
  }

  lazy val default = allByName(0) get "Woodi" err "Can't find default theme D:"
}
