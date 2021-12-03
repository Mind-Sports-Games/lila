package lila.pref
import strategygames.{ GameFamily }

sealed class PieceSet private[pref] (val name: String, 
                                     val pieceFamily: String, 
                                     val displayPiece: String) {

  override def toString = name

  def cssClass = name

}

sealed trait PieceSetObject {

  val all: List[PieceSet]

  val default: PieceSet

  lazy val allByName = all map { c =>
    c.name -> c
  } toMap

  def apply(name: String) = allByName.getOrElse(name, default)

  def contains(name: String) = allByName contains name
}

object PieceSet extends PieceSetObject {

  val default = new PieceSet("cburnett", "chess", "wN")

  val defaults = List(ChessPieceSet.default,
                      DraughtsPieceSet.default,
                      LinesOfActionPieceSet.default,
                      XiangqiPieceSet.default,
                      ShogiPieceSet.default 
                     )                   

  val all = ChessPieceSet.all ::: DraughtsPieceSet.all ::: LinesOfActionPieceSet.all ::: XiangqiPieceSet.all ::: ShogiPieceSet.all
  def allOfFamily(gf: GameFamily) : List[PieceSet] = gf match{
      case GameFamily.Chess() => ChessPieceSet.all
      case GameFamily.Draughts() => DraughtsPieceSet.all
      case GameFamily.LinesOfAction() => LinesOfActionPieceSet.all
      case GameFamily.Shogi() => ShogiPieceSet.all
      case GameFamily.Xiangqi() => XiangqiPieceSet.all
      case _ => List[PieceSet]()
  }
}

object ChessPieceSet extends PieceSetObject {

  val default = new PieceSet("cburnett", "chess", "wN")

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
    new PieceSet(name, "chess", "wN")
  }
}

object DraughtsPieceSet extends PieceSetObject {
  val default = new PieceSet("wide_crown", "draughts", "wM")
  val all = List(default)
}

object LinesOfActionPieceSet extends PieceSetObject {
  val default = new PieceSet("wide_crown", "loa", "wL")
  val all = List(default)
}

object XiangqiPieceSet extends PieceSetObject {
  val default = new PieceSet("2dhanzi", "xiangqi", "RH")
  val all = List(
    default.name,
    "ka"
  ) map { name =>
    new PieceSet(name, "xiangqi", "RH")
  }
}

object ShogiPieceSet extends PieceSetObject {
  val default = new PieceSet("2kanji", "shogi", "0KE")
  val all = List(
    default.name,
    "ctw"
  ) map { name =>
    new PieceSet(name, "shogi", "0KE")
  }
}


object PieceSet3d extends PieceSetObject {

  val default = new PieceSet("Basic", "chess", "wN")

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
    new PieceSet(name, "chess", "wN")
  }
}
