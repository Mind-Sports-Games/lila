package lila.pref

sealed class Theme private[pref] (val name: String, val sgPlayers: Theme.HexSGPlayers) {

  override def toString = name

  def cssClass = name

  def light = sgPlayers._1
  def dark  = sgPlayers._2
}

sealed trait ThemeObject {

  val all: List[Theme]

  val default: Theme

  lazy val allByName = all map { c =>
    c.name -> c
  } toMap

  def apply(name: String) = allByName.getOrElse(name, default)

  def contains(name: String) = allByName contains name
}

object Theme extends ThemeObject {

  case class HexSGPlayer(value: String) extends AnyVal with StringValue
  type HexSGPlayers = (HexSGPlayer, HexSGPlayer)

  private[pref] val defaultHexSGPlayers = (HexSGPlayer("b0b0b0"), HexSGPlayer("909090"))

  private val sgPlayers: Map[String, HexSGPlayers] = Map(
    "blue"   -> (HexSGPlayer("dee3e6") -> HexSGPlayer("8ca2ad")),
    "brown"  -> (HexSGPlayer("f0d9b5") -> HexSGPlayer("b58863")),
    "green"  -> (HexSGPlayer("ffffdd") -> HexSGPlayer("86a666")),
    "purple" -> (HexSGPlayer("9f90b0") -> HexSGPlayer("7d4a8d")),
    "ic"     -> (HexSGPlayer("ececec") -> HexSGPlayer("c1c18e")),
    "horsey" -> (HexSGPlayer("f1d9b6") -> HexSGPlayer("8e6547"))
  )

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
    new Theme(name, sgPlayers.getOrElse(name, defaultHexSGPlayers))
  }

  lazy val default = allByName get "brown" err "Can't find default theme D:"
}

object Theme3d extends ThemeObject {

  val all = List(
    "P2-P1-Aluminium",
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
    "Purple-P2",
    "Rosewood",
    "Wood-Glass",
    "Marble",
    "Wax",
    "Jade",
    "Woodi"
  ) map { name =>
    new Theme(name, Theme.defaultHexSGPlayers)
  }

  lazy val default = allByName get "Woodi" err "Can't find default theme D:"
}
