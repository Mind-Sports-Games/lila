package lila.pref
import strategygames.{ GameFamily }

sealed class Theme private[pref] (val name: String, val colors: Theme.HexColors, val gameFamily: Int) {

  override def toString = name

  def cssClass = name

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

  def contains(name: String, gameFamily: Int = 0) = allByName(gameFamily) contains name

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

  val defaults = List(ChessTheme.default,
                      DraughtsTheme.default,
                      LinesOfActionTheme.default,
                      ShogiTheme.default,
                      XiangqiTheme.default)
  
  def updateBoardTheme(currentThemes : List[Theme], theme: String) : List[Theme] = {
    val newTheme = apply(theme)
    currentThemes.map{ x => x.gameFamily match {
                                case newTheme.gameFamily => newTheme
                                case _ => x
                     }}
  }
  val all = ChessTheme.all ::: DraughtsTheme.all ::: LinesOfActionTheme.all ::: ShogiTheme.all ::: XiangqiTheme.all
 
  def allOfFamily(gf: GameFamily) : List[Theme] = gf match {
    case GameFamily.Chess() => ChessTheme.all
    case GameFamily.Draughts() => DraughtsTheme.all
    case GameFamily.LinesOfAction() => LinesOfActionTheme.all
    case GameFamily.Shogi() => ShogiTheme.all
    case GameFamily.Xiangqi() => XiangqiTheme.all
  }

  lazy val default = allByName(0) get "brown" err "Can't find default theme D:"
}

object ChessTheme extends ThemeObject {

  val all = List(
    "blue",
    "blue2",
    "blue3",
    "blue-marble",
    "canvas",
    "wood",
    "wood2",
    "wood3",
    "wood4",
    "maple",
    "maple2",
    "brown",
    "leather",
    "green",
    "marble",
    "green-plastic",
    "grey",
    "metal",
    "olive",
    "newspaper",
    "purple",
    "purple-diag",
    "pink",
    "ic",
    "horsey"
  ) map { name =>
    new Theme(name, Theme.colors.getOrElse(name, Theme.defaultHexColors), 0)
  }

  lazy val default = allByName(0) get "brown" err "Can't find default theme D:"
}

object DraughtsTheme extends ThemeObject {

  val all = List(
    "blue",
    "blue2",
    "blue3",
    "canvas",
    "wood",
    "wood2",
    "wood3",
    "maple",
    "brown",
    "leather",
    "green",
    "marble",
    "grey",
    "metal",
    "olive",
    "purple"
  ) map { name =>
    new Theme(name, Theme.colors.getOrElse(name, Theme.defaultHexColors), 1)
  }

  lazy val default = allByName(1) get "brown" err "Can't find default theme D:"
}

object LinesOfActionTheme extends ThemeObject {

  val all = List(
    "blue",
    "blue2",
    "blue3",
    "blue-marble",
    "canvas",
    "wood",
    "wood2",
    "wood3",
    "wood4",
    "maple",
    "maple2",
    "brown",
    "leather",
    "green",
    "marble",
    "green-plastic",
    "grey",
    "metal",
    "olive",
    "newspaper",
    "purple",
    "purple-diag",
    "pink",
    "ic",
    "horsey"
  ) map { name =>
    new Theme(name, Theme.colors.getOrElse(name, Theme.defaultHexColors), 2)
  }

  lazy val default = allByName(2) get "brown" err "Can't find default theme D:"
}

object ShogiTheme extends ThemeObject {

  val all = List(
    "shogi",
    "shogi_clear"
  ) map { name =>
    new Theme(name, Theme.defaultHexColors, 3)
  }

  lazy val default = allByName(3) get "shogi" err "Can't find default theme D:"
}

object XiangqiTheme extends ThemeObject {

  val all = List(
    "xiangqi",
    "xiangqic"
  ) map { name =>
    new Theme(name, Theme.defaultHexColors, 4)
  }

  lazy val default = allByName(4) get "xiangqi" err "Can't find default theme D:"
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
