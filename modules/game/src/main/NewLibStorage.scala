package lila.game

import strategygames.{ GameFamily, GameLogic }
import strategygames.format.pgn.Binary

import lila.db.ByteArray

sealed trait NewLibStorage

private object NewLibStorage {

  case object OldBin extends NewLibStorage {

    def encode(gf: GameFamily, pgnMoves: PgnMoves) =
      ByteArray {
        monitor(_.game.pgn.encode("ngl.old")) {
          Binary.writeMoves(gf, pgnMoves).get
        }
      }

    //def encodeActions(gf: GameFamily, actions: Actions) = encode(gf, actions.flatten)

    def decode(gl: GameLogic, bytes: ByteArray, plies: Int): Actions =
      monitor(_.game.pgn.decode("ngl.old")) {
        Binary.readMoves(gl, bytes.value.toList, plies).get.toVector.map(Vector(_))
      }

  }

  private def monitor[A](mon: lila.mon.TimerPath)(f: => A): A =
    lila.common.Chronometer.syncMon(mon)(f)
}
