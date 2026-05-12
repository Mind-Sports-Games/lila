package lila.relay

import scala.concurrent.ExecutionContext

import lila.game.{ GameRepo, PgnDump }

@annotation.nowarn("msg=unused")
final private class GameIdsUpstream(
    _gameRepo: GameRepo,
    _pgnDump: PgnDump
)(implicit _ec: ExecutionContext) {}
