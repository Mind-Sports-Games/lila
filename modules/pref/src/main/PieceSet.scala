package lila.pref
import strategygames.{ GameFamily }

import play.api.libs.json._
import play.api.libs.functional.syntax._

sealed class PieceSet private[pref] (val name: String, 
                                     val gameFamily: Int) {

  override def toString = name

  def cssClass = name

  def gameFamilyName = GameFamily(gameFamily).shortName.toLowerCase()

  def displayPiece = GameFamily(gameFamily).displayPiece
}


sealed trait PieceSetObject {

  val all: List[PieceSet]
  val default: PieceSet

  lazy val allByName = all map { c =>
    c.name -> c
  } toMap

  def applyThemeOnly(name: String) = allByName.getOrElse(name, default)

  def apply(name: String, gameFamily: Int = 0) = new PieceSet(name, gameFamily)
  def unapply(full: PieceSet): Some[(String, Int)] = Some((full.name, full.gameFamily))

  def contains(name: String) = allByName contains name

}

object PieceSet extends PieceSetObject {
  val default = new PieceSet("cburnett", 0)

  val defaults = GameFamily.all.map(gf => new PieceSet(gf.pieceSetDefault, gf.id))                 

  def updatePieceSet(currentPieceSets : List[PieceSet], theme: String) : List[PieceSet] = {
    val newPieceSet = applyThemeOnly(theme)
    addMissingDefaultsIfAny(currentPieceSets).map{ x => 
                            x.gameFamily match {
                                case newPieceSet.gameFamily => newPieceSet
                                case _ => x
                            } 
                        }
  }

  def addMissingDefaultsIfAny(currentPieceSets: List[PieceSet]): List[PieceSet] = {
    defaults.map{
      x => if( currentPieceSets.filter(ps => ps.gameFamily == x.gameFamily).size == 1 ){
        currentPieceSets.filter(ps => ps.gameFamily == x.gameFamily)(0)
      } else{
        x
      }
    }
  }
  val all : List[PieceSet] = GameFamily.all.map(gf => gf.pieceSetThemes.map(t => new PieceSet(t, gf.id))).flatten
  
  def allOfFamily(gf: GameFamily) : List[PieceSet] = gf.pieceSetThemes.map(t => new PieceSet(t, gf.id))

}

object PieceSet3d extends PieceSetObject {

  val default = new PieceSet("Basic", 0)

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
