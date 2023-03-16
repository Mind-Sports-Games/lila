package lila.round

import akka.actor._
import akka.stream.scaladsl._
import strategygames.format.Forsyth
import play.api.libs.json._

import lila.common.Bus
import lila.common.LightUser
import lila.game.actorApi.MoveGameEvent
import lila.game.Game
import lila.socket.Socket

final private class TvBroadcast(
    userJsonView: lila.user.JsonView,
    lightUserSync: LightUser.GetterSync
) extends Actor {

  import TvBroadcast._

  private var clients = Set.empty[Client]

  private var featured = none[Featured]

  Bus.subscribe(self, "changeFeaturedGame")

  implicit def system = context.dispatcher

  override def postStop() = {
    super.postStop()
    unsubscribeFromFeaturedId()
  }

  def receive = {

    case TvBroadcast.Connect(compat) =>
      sender() ! Source
        .queue[JsValue](8, akka.stream.OverflowStrategy.dropHead)
        .mapMaterializedValue { queue =>
          val client = Client(queue, compat)
          self ! Add(client)
          queue.watchCompletion().foreach { _ =>
            self ! Remove(client)
          }
          featured ifFalse compat foreach { f =>
            client.queue.offer(Socket.makeMessage("featured", f.dataWithFen))
          }
        }

    case Add(client)    => clients = clients + client
    case Remove(client) => clients = clients - client

    case ChangeFeatured(pov, msg) =>
      unsubscribeFromFeaturedId()
      Bus.subscribe(self, MoveGameEvent makeChan pov.gameId)
      val feat = Featured(
        pov.gameId,
        Json.obj(
          "id"          -> pov.gameId,
          "orientation" -> pov.playerIndex.name,
          "players" -> pov.game.players.map { p =>
            val user = p.userId.flatMap(lightUserSync)
            Json
              .obj("playerIndex" -> p.playerIndex.name)
              .add("user" -> user.map(LightUser.lightUserWrites.writes))
              .add("ai" -> p.aiLevel)
              .add("rating" -> p.rating)
          }
        ),
        fen = Forsyth.exportBoard(pov.game.variant.gameLogic, pov.game.chess.board)
      )
      clients.foreach { client =>
        client.queue offer {
          if (client.fromPlayStrategy) msg
          else feat.socketMsg
        }
      }
      featured = feat.some

    case MoveGameEvent(game, fen, move) =>
      val msg = Socket.makeMessage(
        "fen",
        Json
          .obj(
            "fen" -> s"$fen ${game.turnPlayerIndex.letter}",
            "lm"  -> move
          )
          .add("p1" -> game.clock.map(_.remainingTime(strategygames.P1).roundSeconds))
          .add("p2" -> game.clock.map(_.remainingTime(strategygames.P2).roundSeconds)) // TODO: byoyomi periods here?!
      )
      clients.foreach(_.queue offer msg)
      featured foreach { f =>
        featured = f.copy(fen = fen).some
      }
  }

  def unsubscribeFromFeaturedId() =
    featured foreach { previous =>
      Bus.unsubscribe(self, MoveGameEvent makeChan previous.id)
    }
}

object TvBroadcast {

  type SourceType = Source[JsValue, _]
  type Queue      = SourceQueueWithComplete[JsValue]

  case class Featured(id: Game.ID, data: JsObject, fen: String) {
    def dataWithFen = data ++ Json.obj("fen" -> fen)
    def socketMsg   = Socket.makeMessage("featured", dataWithFen)
  }

  case class Connect(fromPlayStrategy: Boolean)
  case class Client(queue: Queue, fromPlayStrategy: Boolean)

  case class Add(c: Client)
  case class Remove(c: Client)
}
