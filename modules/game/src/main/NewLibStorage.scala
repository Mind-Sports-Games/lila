package lila.game

import strategygames.{ ActionStrs, GameFamily, GameLogic, VActionStrs }
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

    def encodeActionStrs(gf: GameFamily, actionStrs: ActionStrs) =
      ByteArray {
        monitor(_.game.pgn.encode("ngla")) {
          Binary.writeActionStrs(gf, actionStrs).get
        }
      }

    def decode(gl: GameLogic, bytes: ByteArray, plies: Int): VActionStrs =
      monitor(_.game.pgn.decode("ngla")) {
        Binary
          .readActionStrs(gl, bytes.value.toList, plies)
          .get
          .toVector
          .map(_.toVector)
      }

  }

  private def monitor[A](mon: lila.mon.TimerPath)(f: => A): A =
    lila.common.Chronometer.syncMon(mon)(f)
}
