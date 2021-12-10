package lila.pref
import strategygames.{ GameFamily }

import play.api.libs.json._
import play.api.libs.functional.syntax._

sealed class PieceSet private[pref] (val name: String, 
                                     val gameFamily: Int) {

  override def toString = name

  def cssClass = name

  def gameFamilyName = PieceSet.gamePieceSet(gameFamily).gameFamilyName
  
  def displayPiece = PieceSet.gamePieceSet(gameFamily).displayPiece
}


sealed trait PieceSetObject {

  val all: List[PieceSet]

  val default: PieceSet

  val displayPiece: String
  val gameFamilyName : String

  lazy val allByName = all map { c =>
    c.name -> c
  } toMap

  def applyThemeOnly(name: String) = allByName.getOrElse(name, default)

  def apply(name: String, gameFamily: Int) = new PieceSet(name, gameFamily)
  def unapply(full: PieceSet): Some[(String, Int)] = Some((full.name, full.gameFamily))

  def contains(name: String) = allByName contains name

}

object PieceSet extends PieceSetObject {

  val default = new PieceSet("cburnett", 0)
  val displayPiece = "wN"
  val gameFamilyName = "chess"

  val defaults = List(ChessPieceSet.default,
                      DraughtsPieceSet.default,
                      LinesOfActionPieceSet.default,
                      ShogiPieceSet.default,
                      XiangqiPieceSet.default
                     )                   

  def updatePieceSet(currentPieceSets : List[PieceSet], theme: String) : List[PieceSet] = {
    val newPieceSet = applyThemeOnly(theme)
    currentPieceSets.map{ x => 
                            x.gameFamily match {
                                case newPieceSet.gameFamily => newPieceSet
                                case _ => x
                            } 
                        }
  }

  val all = ChessPieceSet.all ::: DraughtsPieceSet.all ::: LinesOfActionPieceSet.all ::: XiangqiPieceSet.all ::: ShogiPieceSet.all
  def allOfFamily(gf: GameFamily) : List[PieceSet] = gf match{
      case GameFamily.Chess() => ChessPieceSet.all
      case GameFamily.Draughts() => DraughtsPieceSet.all
      case GameFamily.LinesOfAction() => LinesOfActionPieceSet.all
      case GameFamily.Shogi() => ShogiPieceSet.all
      case GameFamily.Xiangqi() => XiangqiPieceSet.all
      case _ => List[PieceSet]()
  }

  def gamePieceSet(gameFamily:Int): PieceSetObject = gameFamily match {
      case 1 => DraughtsPieceSet
      case 2 => LinesOfActionPieceSet
      case 3 => ShogiPieceSet
      case 4 => XiangqiPieceSet
      case _ => ChessPieceSet
  }
}

object ChessPieceSet extends PieceSetObject {

  val default = new PieceSet("cburnett", 0)
  val displayPiece = "wN"
  val gameFamilyName = "chess"
  val all = List(
    default.name,
    "merida",
    "alpha",
    "california",
    "cardinal",
    "chess7",
    "chessnut",
    "companion",
    "dubrovny",
    "fantasy",
    "fresca",
    "gioco",
    "governor",
    "horsey",
    "icpieces",
    "kosal",
    "leipzig",
    "letter",
    "maestro",
    "pirouetti",
    "pixel",
    "reillycraig",
    "riohacha",
    "shapes",
    "spatial",
    "staunty",
    "tatiana"
  ) map { name =>
    new PieceSet(name, 0)
  }
}

object DraughtsPieceSet extends PieceSetObject {
  val default = new PieceSet("wide_crown", 1)
  val displayPiece = "wM"
  val gameFamilyName = "draughts"
  val all = List(default)
}

object LinesOfActionPieceSet extends PieceSetObject {
  val default = new PieceSet("wide", 2)
  val displayPiece = "wL"
  val gameFamilyName = "loa"
  val all = List(default)
}

object ShogiPieceSet extends PieceSetObject {
  val default = new PieceSet("2kanji", 3)
  val displayPiece = "0KE"
  val gameFamilyName = "shogi"
  val all = List(
    default.name,
    "ctw"
  ) map { name =>
    new PieceSet(name, 3)
  }
}

object XiangqiPieceSet extends PieceSetObject {
  val default = new PieceSet("2dhanzi", 4)
  val displayPiece = "RH"
  val gameFamilyName = "xiangqi"
  val all = List(
    default.name,
    "ka"
  ) map { name =>
    new PieceSet(name, 4)
  }
}

object PieceSet3d extends PieceSetObject {

  val default = new PieceSet("Basic", 0)
  val displayPiece = "wN"
val gameFamilyName = "chess"
  val all = List(
    default.name,
    "Wood",
    "Metal",
    "RedVBlue",
    "ModernJade",
    "ModernWood",
    "Glass",
    "Trimmed",
    "Experimental",
    "Staunton",
    "CubesAndPi"
  ) map { name =>
    new PieceSet(name, 0)
  }
}
