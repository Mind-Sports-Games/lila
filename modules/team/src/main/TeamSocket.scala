package lila.team

import lila.room.RoomSocket.{ Protocol as RP, * }
import lila.socket.RemoteSocket.{ Protocol as P, * }

final private class TeamSocket(
    remoteSocketApi: lila.socket.RemoteSocket,
    chat: lila.chat.ChatApi,
    cached: Cached
)(implicit
    ec: scala.concurrent.ExecutionContext,
    mode: play.api.Mode
) {

  lazy val rooms = makeRoomMap(send)

  subscribeChat(rooms, _.Team)

  private lazy val handler: Handler =
    roomHandler(
      rooms,
      chat,
      logger,
      roomId => _.Team(roomId.value).some,
      localTimeout = Some { (roomId, modId, suspectId) =>
        cached.isLeader(roomId.value, modId) >>& cached.isLeader(roomId.value, suspectId).not
      },
      chatBusChan = _.Team
    )

  private lazy val send: String => Unit = remoteSocketApi.makeSender("team-out").apply

  remoteSocketApi
    .subscribe("team-in", RP.In.reader)(
      handler orElse remoteSocketApi.baseHandler
    )
    .andDo(send(P.Out.boot))
}
