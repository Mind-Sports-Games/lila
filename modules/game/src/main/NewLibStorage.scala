package lila.game

import strategygames.{ Actions, GameFamily, GameLogic, VActions }
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

    def encodeActions(gf: GameFamily, actions: Actions) =
      ByteArray {
        monitor(_.game.pgn.encode("ngla")) {
          Binary.writeActions(gf, actions).get
        }
      }

    def decode(gl: GameLogic, bytes: ByteArray, plies: Int): VActions =
      monitor(_.game.pgn.decode("ngla")) {
        Binary
          .readActions(gl, bytes.value.toList, plies)
          .get
          .toVector
          .map(_.toVector)
      }

  }

  private def monitor[A](mon: lila.mon.TimerPath)(f: => A): A =
    lila.common.Chronometer.syncMon(mon)(f)
}
