package lila.socket

import strategygames.format.{ FEN, Uci }
import strategygames.{ Player => PlayerIndex, GameLogic, Pocket, PocketData, Pos, Role }
import play.api.libs.json._

case class Step(
    ply: Int,
    move: Option[Step.Move],
    fen: FEN,
    check: Boolean,
    // None when not computed yet
    dests: Option[Map[Pos, List[Pos]]],
    drops: Option[List[Pos]],
    pocketData: Option[PocketData],
    captLen: Option[Int]
) {

  // who's playerIndex plays next
  // TODO: wrong for Amazons
  def playerIndex = PlayerIndex.fromPly(ply)

  def toJson = Step.stepJsonWriter writes this
}

object Step {

  case class Move(uci: Uci, san: String) {
    def uciString      = uci.uci
    def shortUciString = uci.shortUci
  }

  // TODO copied from lila.game
  // put all that shit somewhere else
  implicit private val pocketWriter: OWrites[Pocket] = OWrites { v =>
    JsObject(
      Role
        .storable(v.roles.headOption match {
          case Some(r) =>
            r match {
              case Role.ChessRole(_)   => GameLogic.Chess()
              case Role.FairySFRole(_) => GameLogic.FairySF()
              case _                   => sys.error("Pocket not implemented for GameLogic")
            }
          case None => GameLogic.Chess()
        })
        .flatMap { role =>
          Some(v.roles.count(role ==)).filter(0 <).map { count =>
            role.groundName -> JsNumber(count)
          }
        }
    )
  }
  implicit private val pocketDataWriter: OWrites[PocketData] = OWrites { v =>
    Json.obj("pockets" -> List(v.pockets.p1, v.pockets.p2))
  }

  implicit val stepJsonWriter: Writes[Step] = Writes { step =>
    import lila.common.Json._
    import step._
    Json
      .obj(
        "ply"           -> ply,
        "uci"           -> move.map(_.shortUciString),
        "lidraughtsUci" -> move.map(_.uciString),
        "san"           -> move.map(_.san),
        "fen"           -> fen.value,
        "captLen"       -> ~captLen
      )
      .add("check", check)
      .add(
        "dests",
        dests.map {
          _.map { case (orig, dests) =>
            s"${orig.piotr}${dests.map(_.piotr).mkString}"
          }.mkString(" ")
        }
      )
      .add(
        "drops",
        drops.map { drops =>
          JsString(drops.map(_.key).mkString)
        }
      )
      .add("crazy", pocketData)
  }
}
