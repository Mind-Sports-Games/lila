package lila.fishnet

import strategygames.GameLogic
import strategygames.variant.Variant

// Decides which game logics a fishnet analysis client may be assigned work for,
// from the optional `variants` it declares on acquire (JsonApi.Request.Fishnet).
//
// A client that declares nothing keeps the historical behaviour: chess + the
// chess-like fairysf variants only, and never backgammon. Backgammon is analysed
// exclusively by the gnubg-backed mindcube worker, which opts in with
// `variants = ["backgammon"]`.
private[fishnet] object FishnetVariants {

  // Game logics that a client must explicitly opt into; they are excluded from the
  // default routing so existing stockfish clients are never handed this work.
  val optInLogicIds: Set[Int] = Set(GameLogic.Backgammon().id)

  // Default routing: every analysable game logic except the opt-in ones. Derived
  // from `hasFishnet` so a newly-enabled chess/fairysf variant is picked up
  // automatically, while backgammon stays opt-in.
  lazy val defaultLogicIds: Set[Int] =
    Variant.all.filter(_.hasFishnet).map(_.gameLogic.id).toSet -- optInLogicIds

  // Resolve declared variant- or family-keys (e.g. "backgammon") to the set of
  // game-logic ids the client may be assigned. Unknown keys contribute nothing.
  def allowedLogicIds(declared: Option[List[String]]): Set[Int] =
    declared.map(_.filter(_.nonEmpty)) match {
      case None | Some(Nil) => defaultLogicIds
      case Some(keys)       =>
        val wanted = keys.map(_.toLowerCase).toSet
        Variant.all
          .filter(v => wanted(v.key.toLowerCase) || wanted(v.gameFamily.key.toLowerCase))
          .map(_.gameLogic.id)
          .toSet
    }
}
