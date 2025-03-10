package lila.simul

import org.joda.time.DateTime
import reactivemongo.api.bson._

import strategygames.{ GameLogic, Player => PlayerIndex, P1, P2, Status }
import strategygames.variant.Variant
import lila.db.BSON
import lila.db.BSON.{ Reader, Writer }
import lila.db.BSON.BSONJodaDateTimeHandler
import lila.db.dsl._
import lila.user.User

final private[simul] class SimulRepo(val coll: Coll)(implicit ec: scala.concurrent.ExecutionContext) {

  implicit private val SimulStatusBSONHandler: BSONHandler[SimulStatus] = tryHandler[SimulStatus](
    { case BSONInteger(v) => SimulStatus(v) toTry s"No such simul status: $v" },
    x => BSONInteger(x.id)
  )
  implicit private val ChessStatusBSONHandler: BSONHandler[Status] = lila.game.BSONHandlers.StatusBSONHandler

  implicit val VariantBSONHandler: BSON[Variant] = new BSON[Variant] {
    def reads(r: Reader) = Variant(GameLogic(r.intD("gl")), r.int("v")) match {
      case Some(v) => v
      case None    => sys.error(s"No such variant: ${r.intD("v")} for gamelogic: ${r.intD("gl")}")
    }
    def writes(w: Writer, v: Variant) = $doc("gl" -> v.gameLogic.id, "v" -> v.id)
  }

  import strategygames.ClockConfig
  implicit private val clockHandler: BSONHandler[ClockConfig]              = clockConfigHandler
  implicit private val ClockBSONHandler: BSONDocumentHandler[SimulClock]   = Macros.handler[SimulClock]
  implicit private val PlayerBSONHandler: BSONDocumentHandler[SimulPlayer] = Macros.handler[SimulPlayer]
  implicit private val ApplicantBSONHandler: BSONDocumentHandler[SimulApplicant] =
    Macros.handler[SimulApplicant]
  implicit private val SimulPairingBSONHandler: BSON[SimulPairing] = new BSON[SimulPairing] {
    def reads(r: BSON.Reader) =
      SimulPairing(
        player = r.get[SimulPlayer]("player"),
        gameId = r str "gameId",
        status = r.get[Status]("status"),
        wins = r boolO "wins",
        hostPlayerIndex = r.strO("hostPlayerIndex").flatMap(PlayerIndex.fromName) | P1
      )
    def writes(w: BSON.Writer, o: SimulPairing) =
      $doc(
        "player"          -> o.player,
        "gameId"          -> o.gameId,
        "status"          -> o.status,
        "wins"            -> o.wins,
        "hostPlayerIndex" -> o.hostPlayerIndex.name
      )
  }

  implicit private val SimulBSONHandler: BSONDocumentHandler[Simul] = Macros.handler[Simul]

  private val createdSelect  = $doc("status" -> SimulStatus.Created.id)
  private val startedSelect  = $doc("status" -> SimulStatus.Started.id)
  private val finishedSelect = $doc("status" -> SimulStatus.Finished.id)
  private val createdSort    = $sort desc "createdAt"

  def find(id: Simul.ID): Fu[Option[Simul]] =
    coll.byId[Simul](id)

  def byIds(ids: List[Simul.ID]): Fu[List[Simul]] =
    coll.byIds[Simul](ids)

  def exists(id: Simul.ID): Fu[Boolean] =
    coll.exists($id(id))

  def findStarted(id: Simul.ID): Fu[Option[Simul]] =
    find(id) map (_ filter (_.isStarted))

  def findCreated(id: Simul.ID): Fu[Option[Simul]] =
    find(id) map (_ filter (_.isCreated))

  def findPending(hostId: User.ID): Fu[List[Simul]] =
    coll.list[Simul](createdSelect ++ $doc("hostId" -> hostId))

  def byTeamLeaders(teamId: String, hostIds: Seq[User.ID]): Fu[List[Simul]] =
    coll
      .find(
        createdSelect ++
          $doc("hostId" $in hostIds, "team" $in List(BSONString(teamId)))
      )
      .hint(coll hint $doc("hostId" -> 1))
      .cursor[Simul]()
      .list()

  def hostId(id: Simul.ID): Fu[Option[User.ID]] =
    coll.primitiveOne[User.ID]($id(id), "hostId")

  private val featurableSelect = $doc("featurable" -> true)

  def allCreatedFeaturable: Fu[List[Simul]] =
    coll
      .find(
        // hits partial index hostSeenAt_-1
        createdSelect ++ featurableSelect ++ $doc(
          //"hostSeenAt" $gte DateTime.now.minusSeconds(12),
          "createdAt" $gte DateTime.now.minusDays(30)
        )
      )
      .sort(createdSort)
      .hint(coll hint $doc("hostSeenAt" -> -1))
      .cursor[Simul]()
      .list() map {
      _.foldLeft(List.empty[Simul]) {
        case (acc, sim) if acc.exists(_.hostId == sim.hostId) => acc
        case (acc, sim)                                       => sim :: acc
      }.reverse
    }

  def allCreatedRecently: Fu[List[Simul]] =
    coll
      .find(
        createdSelect ++ $doc(
          "createdAt" $gte DateTime.now.minusDays(30)
        )
      )
      .sort(createdSort)
      .cursor[Simul]()
      .list()

  def allStarted: Fu[List[Simul]] =
    coll
      .find(startedSelect)
      .sort(createdSort)
      .cursor[Simul]()
      .list()

  def allFinishedFeaturable(max: Int): Fu[List[Simul]] =
    coll
      .find(finishedSelect ++ featurableSelect)
      .sort($sort desc "finishedAt")
      .cursor[Simul]()
      .list(max)

  def allNotFinished =
    coll.list[Simul]($doc("status" $ne SimulStatus.Finished.id))

  def create(simul: Simul): Funit =
    coll.insert one {
      SimulBSONHandler.writeTry(simul).get
    } void

  def update(simul: Simul) =
    coll.update
      .one(
        $id(simul.id),
        $set(SimulBSONHandler writeTry simul get) ++
          simul.estimatedStartAt.isEmpty ?? ($unset("estimatedStartAt"))
      )
      .void

  def remove(simul: Simul) =
    coll.delete.one($id(simul.id)).void

  def setHostGameId(simul: Simul, gameId: String) =
    coll.update
      .one(
        $id(simul.id),
        $set("hostGameId" -> gameId)
      )
      .void

  def setHostSeenNow(simul: Simul) =
    coll.update
      .one(
        $id(simul.id),
        $set("hostSeenAt" -> DateTime.now)
      )
      .void

  def setText(simul: Simul, text: String) =
    coll.update
      .one(
        $id(simul.id),
        $set("text" -> text)
      )
      .void

  def cleanup =
    coll.delete.one(
      createdSelect ++ $doc(
        "createdAt" -> $doc("$lt" -> (DateTime.now minusMinutes 60))
      )
    )
}
