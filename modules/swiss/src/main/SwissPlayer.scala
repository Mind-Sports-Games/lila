package lila.swiss

import lila.common.LightUser
import lila.rating.PerfType
import lila.user.User

case class SwissPlayer(
    id: SwissPlayer.Id, // swissId:userId
    swissId: Swiss.Id,
    userId: User.ID,
    rating: Int,
    inputRating: Option[Int],
    provisional: Boolean,
    points: Swiss.Points,
    sbTieBreak: Swiss.SonnenbornBerger,
    bhTieBreak: Option[Swiss.Buchholz],
    performance: Option[Swiss.Performance],
    score: Swiss.Score,
    absent: Boolean,
    byes: Set[SwissRound.Number], // byes granted by the pairing system - the player was here
    disqualified: Boolean
) {
  def is(uid: User.ID): Boolean       = uid == userId
  def is(user: User): Boolean         = is(user.id)
  def is(other: SwissPlayer): Boolean = is(other.userId)
  def present                         = !absent
  val actualRating: Int               = inputRating.getOrElse(rating)

  // If bhTieBreak is None, then we'll use sb, otherwise, we'll use
  val tieBreak = bhTieBreak.fold(sbTieBreak.value)(_.value)
  // If bhTieBreak is None, then there is no secondary tb, else its sb
  val tieBreak2 = bhTieBreak.map(_ => sbTieBreak.value)

  def recomputeScore =
    copy(
      score = Swiss.makeScore(
        points,
        ~bhTieBreak,
        sbTieBreak,
        performance | Swiss.Performance(rating.toFloat)
      )
    )
}

object SwissPlayer {

  case class Id(value: String) extends AnyVal with StringValue

  def makeId(swissId: Swiss.Id, userId: User.ID) = Id(s"$swissId:$userId")

  private[swiss] def make(
      swissId: Swiss.Id,
      user: User,
      perf: PerfType,
      inputRating: Option[Int]
  ): SwissPlayer =
    new SwissPlayer(
      id = makeId(swissId, user.id),
      swissId = swissId,
      userId = user.id,
      rating = user.perfs(perf).intRating,
      inputRating = inputRating,
      provisional = user.perfs(perf).provisional,
      points = Swiss.Points(0),
      sbTieBreak = Swiss.SonnenbornBerger(0),
      bhTieBreak = Some(Swiss.Buchholz(0)),
      performance = none,
      score = Swiss.Score(0),
      absent = false,
      byes = Set.empty,
      disqualified = false
    ).recomputeScore

  case class WithRank(player: SwissPlayer, rank: Int) {
    def is(other: WithRank)       = player is other.player
    def withUser(user: LightUser) = WithUserAndRank(player, user, rank)
    override def toString         = s"$rank. ${player.userId}[${player.actualRating}]"
  }

  case class WithUser(player: SwissPlayer, user: LightUser)

  case class WithUserAndRank(player: SwissPlayer, user: LightUser, rank: Int)

  sealed private[swiss] trait Viewish {
    val player: SwissPlayer
    val rank: Int
    val user: lila.common.LightUser
    val sheet: SwissSheet
  }

  private[swiss] case class View(
      player: SwissPlayer,
      rank: Int,
      user: lila.common.LightUser,
      pairings: Map[SwissRound.Number, SwissPairing],
      sheet: SwissSheet
  ) extends Viewish

  private[swiss] case class ViewExt(
      player: SwissPlayer,
      rank: Int,
      user: lila.common.LightUser,
      pairings: Map[SwissRound.Number, SwissPairing.View],
      sheet: SwissSheet
  ) extends Viewish

  type PlayerMap = Map[User.ID, SwissPlayer]

  def toMap(players: List[SwissPlayer]): PlayerMap =
    players.view.map(p => p.userId -> p).toMap

  object Fields {
    val id           = "_id"
    val swissId      = "s"
    val userId       = "u"
    val rating       = "r"
    val inputRating  = "ir"
    val provisional  = "pr"
    val points       = "p"
    val sbTieBreak   = "t"
    val bhTieBreak   = "tb"
    val performance  = "e"
    val score        = "c"
    val absent       = "a"
    val byes         = "b"
    val disqualified = "dq"
  }
  def fields[A](f: Fields.type => A): A = f(Fields)
}
