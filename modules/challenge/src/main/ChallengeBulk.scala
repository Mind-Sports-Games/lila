package lila.challenge

import akka.actor.ActorSystem
import akka.stream.scaladsl._
import org.joda.time.DateTime
import reactivemongo.api.bson.Macros
import scala.concurrent.duration._

import strategygames.{ GameLogic, Situation, Speed }
import strategygames.Player.{ P1, P2 }

import lila.common.Bus
import lila.common.LilaStream
import lila.common.Template
import lila.db.dsl._
import lila.game.{ Game, Player }
import lila.hub.actorApi.map.TellMany
import lila.hub.DuctSequencers
import lila.rating.PerfType
import lila.setup.SetupBulk.{ ScheduledBulk, ScheduledGame }
import lila.user.User

final class ChallengeBulkApi(
    colls: ChallengeColls,
    msgApi: ChallengeMsg,
    gameRepo: lila.game.GameRepo,
    userRepo: lila.user.UserRepo,
    onStart: lila.round.OnStart
)(implicit
    ec: scala.concurrent.ExecutionContext,
    mat: akka.stream.Materializer,
    system: ActorSystem,
    mode: play.api.Mode
) {

  implicit private val gameHandler         = Macros.handler[ScheduledGame]
  implicit private val variantHandler      = variantByKeyHandler
  implicit private val stratVariantHandler = stratVariantByKeyHandler
  implicit private val clockHandler        = clockConfigHandler
  implicit private val messageHandler      = stringAnyValHandler[Template](_.value, Template.apply)
  implicit private val bulkHandler         = Macros.handler[ScheduledBulk]

  private val coll = colls.bulk

  private val workQueue =
    new DuctSequencers(maxSize = 16, expiration = 10 minutes, timeout = 10 seconds, name = "challenge.bulk")

  def scheduledBy(me: User): Fu[List[ScheduledBulk]] =
    coll.list[ScheduledBulk]($doc("by" -> me.id))

  def deleteBy(id: String, me: User): Fu[Boolean] =
    coll.delete.one($doc("_id" -> id, "by" -> me.id)).map(_.n == 1)

  def startClocks(id: String, me: User): Fu[Boolean] =
    coll
      .updateField($doc("_id" -> id, "by" -> me.id, "pairedAt" $exists true), "startClocksAt", DateTime.now)
      .map(_.n == 1)

  def schedule(bulk: ScheduledBulk): Fu[Either[String, ScheduledBulk]] = workQueue(bulk.by) {
    coll.list[ScheduledBulk]($doc("by" -> bulk.by, "pairedAt" $exists false)) flatMap { bulks =>
      val nbGames = bulks.map(_.games.size).sum
      if (bulks.sizeIs >= 10) fuccess(Left("Already too many bulks queued"))
      else if (bulks.map(_.games.size).sum >= 1000) fuccess(Left("Already too many games queued"))
      else if (bulks.exists(_ collidesWith bulk))
        fuccess(Left("A bulk containing the same players is scheduled at the same time"))
      else coll.insert.one(bulk) inject Right(bulk)
    }
  }

  private[challenge] def tick: Funit =
    checkForPairing >> checkForClocks

  private def checkForPairing: Funit =
    coll.one[ScheduledBulk]($doc("pairAt" $lte DateTime.now, "pairedAt" $exists false)) flatMap {
      _ ?? { bulk =>
        workQueue(bulk.by) {
          makePairings(bulk).void
        }
      }
    }

  private def checkForClocks: Funit =
    coll.one[ScheduledBulk]($doc("startClocksAt" $lte DateTime.now, "pairedAt" $exists true)) flatMap {
      _ ?? { bulk =>
        workQueue(bulk.by) {
          startClocksNow(bulk)
        }
      }
    }

  private def startClocksNow(bulk: ScheduledBulk): Funit = {
    Bus.publish(TellMany(bulk.games.map(_.id), lila.round.actorApi.round.StartClock), "roundSocket")
    coll.delete.one($id(bulk._id)).void
  }

  private def makePairings(bulk: ScheduledBulk): Funit = {
    val perfType = PerfType(bulk.variant, Speed(bulk.clock))
    Source(bulk.games)
      .mapAsyncUnordered(8) { game =>
        userRepo.pair(game.p1, game.p2) map2 { case (p1, p2) =>
          (game.id, p1, p2)
        }
      }
      .mapConcat(_.toList)
      .map { case (id, p1, p2) =>
        val game = Game
          .make(
            stratGame = strategygames.Game(
              bulk.variant.gameLogic,
              situation = Situation(bulk.variant.gameLogic, bulk.variant),
              clock = bulk.clock.toClock.some
            ),
            p1Player = Player.make(P1, p1.some, _(perfType)),
            p2Player = Player.make(P2, p2.some, _(perfType)),
            mode = bulk.mode,
            source = lila.game.Source.Api,
            pgnImport = None
          )
          .withId(id)
          .start
        (game, p1, p2)
      }
      .mapAsyncUnordered(8) { case (game, p1, p2) =>
        gameRepo.insertDenormalized(game) >>- onStart(game.id) inject {
          (game, p1, p2)
        }
      }
      .mapAsyncUnordered(8) { case (game, p1, p2) =>
        msgApi.onApiPair(game.id, p1.light, p2.light)(bulk.by, bulk.message)
      }
      .toMat(LilaStream.sinkCount)(Keep.right)
      .run()
      .addEffect { nb =>
        lila.mon.api.challenge.bulk.createNb(bulk.by).increment(nb).unit
      } >> {
      if (bulk.startClocksAt.isDefined)
        coll.updateField($id(bulk._id), "pairedAt", DateTime.now)
      else coll.delete.one($id(bulk._id))
    }.void
  }
}
