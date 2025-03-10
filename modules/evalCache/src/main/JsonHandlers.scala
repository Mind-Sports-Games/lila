package lila.evalCache

import cats.implicits._
import strategygames.format.{ FEN, Uci }
import strategygames.variant.Variant
import strategygames.{ GameFamily, GameLogic }
import play.api.libs.json._

import lila.common.Json._
import lila.evalCache.EvalCacheEntry._
import lila.tree.Eval._

object JsonHandlers {

  implicit private val cpWriter: Writes[Cp]         = intAnyValWriter[Cp](_.value)
  implicit private val mateWriter: Writes[Mate]     = intAnyValWriter[Mate](_.value)
  implicit private val knodesWriter: Writes[Knodes] = intAnyValWriter[Knodes](_.value)

  def writeEval(e: Eval, fen: FEN) =
    Json.obj(
      "fen"    -> fen.value,
      "knodes" -> e.knodes,
      "depth"  -> e.depth,
      "pvs"    -> e.pvs.toList.map(writePv)
    )

  private def writePv(pv: Pv) =
    Json
      .obj(
        "moves" -> pv.moves.value.toList.map(_.uci).mkString(" ")
      )
      .add("cp", pv.score.cp)
      .add("mate", pv.score.mate)

  private[evalCache] def readPut(trustedUser: TrustedUser, o: JsObject): Option[Input.Candidate] =
    o obj "d" flatMap { readPutData(trustedUser, _) }

  private[evalCache] def readPutData(trustedUser: TrustedUser, d: JsObject): Option[Input.Candidate] =
    for {
      fen    <- d str "fen"
      knodes <- d int "knodes"
      depth  <- d int "depth"
      pvObjs <- d objs "pvs"
      pvs    <- pvObjs.map(parsePv).sequence.flatMap(_.toNel)
      variant = Variant.orDefault(
        //Same defaulting problem/temp fix as below for Game Family.
        //Can resolve both properly at the same time.
        GameLogic.Chess(),
        //GameLogic(d int "lib" match {
        //  case Some(lib) => lib
        //  case None      => sys.error("lib must be provided for readPutData")
        //}),
        ~d.str("variant")
      )
    } yield Input.Candidate(
      variant,
      fen,
      Eval(
        pvs = pvs,
        knodes = Knodes(knodes),
        depth = depth,
        by = trustedUser.user.id,
        trust = trustedUser.trust
      )
    )

  private def parsePv(d: JsObject): Option[Pv] =
    for {
      movesStr <- d str "moves"
      //We can hard code this for now as this is only triggered by in browser analysis
      //for which we only have enabled for Chess. When we extend in browser analysis
      //to other Game Families then we will need to add GameFamily to the JsObject here
      //Currently the GameFamily isn't being passed through, but GameLogic is.
      gameFamily = GameFamily.Chess()
      //gameFamily = GameFamily(d int "gf" match {
      //  case Some(gf) => gf
      //  case None      => sys.error("gf must be provided for parsePv")
      //})
      moves <-
        movesStr
          .split(' ')
          .take(EvalCacheEntry.MAX_PV_SIZE)
          .foldLeft(List.empty[Uci].some) {
            case (Some(ucis), str) => Uci(gameFamily.gameLogic, gameFamily, str) map (_ :: ucis)
            case _                 => None
          }
          .flatMap(_.reverse.toNel) map Moves.apply
      cp   = d int "cp" map Cp.apply
      mate = d int "mate" map Mate.apply
      score <- cp.map(Score.cp) orElse mate.map(Score.mate)
    } yield Pv(score, moves)
}
