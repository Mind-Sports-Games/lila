package lila.team

import org.apache.pekko.stream.scaladsl._
import reactivemongo.pekkostream.cursorProducer
import reactivemongo.api.ReadPreference

import lila.common.config.MaxPerSecond
import lila.db.dsl._
import lila.user.{ User, UserRepo }

final class TeamMemberStream(
    memberRepo: MemberRepo,
    userRepo: UserRepo
)(implicit
    ec: scala.concurrent.ExecutionContext,
    mat: org.apache.pekko.stream.Materializer
):

  def apply(team: Team, perSecond: MaxPerSecond): Source[User, ?] =
    idsBatches(team, perSecond)
      .mapAsync(1)(userRepo.usersFromSecondary)
      .mapConcat(identity)

  def subscribedIds(team: Team, perSecond: MaxPerSecond): Source[User.ID, ?] =
    idsBatches(team, perSecond, $doc("unsub" `$ne` true))
      .mapConcat(identity)

  private def idsBatches(
      team: Team,
      perSecond: MaxPerSecond,
      selector: Bdoc = $empty
  ): Source[Seq[User.ID], ?] =
    memberRepo.coll
      .find($doc("team" -> team.id) ++ selector, $doc("user" -> true).some)
      .sort($sort `desc` "date")
      .batchSize(perSecond.value)
      .cursor[Bdoc](ReadPreference.secondaryPreferred)
      .documentSource()
      .grouped(perSecond.value)
      .map(_.flatMap(_.getAsOpt[User.ID]("user")))
      .throttle(1, 1 second)
