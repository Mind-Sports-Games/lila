package lila.common

import play.api.libs.json._

case class LightUser(
    id: String,
    name: String,
    country: Option[String],
    title: Option[String],
    isPatron: Boolean
) {

  def titleName = title.fold(name)(_ + " " + name)

  def isBot = title has "BOT"
}

object LightUser {

  private type UserID = String

  val ghost = LightUser("ghost", "ghost", none, none, false)

  implicit val lightUserWrites = OWrites[LightUser] { u =>
    writeNoId(u) + ("id" -> JsString(u.id))
  }

  def writeNoId(u: LightUser): JsObject =
    Json
      .obj("name" -> u.name)
      .add("country" -> u.country)
      .add("title" -> u.title)
      .add("patron" -> u.isPatron)

  def fallback(name: String) =
    LightUser(
      id = name.toLowerCase,
      name = name,
      country = None,
      title = None,
      isPatron = false
    )

  final class Getter(f: UserID => Fu[Option[LightUser]]) extends (UserID => Fu[Option[LightUser]]) {
    def apply(u: UserID) = f(u)
  }

  final class GetterSync(f: UserID => Option[LightUser]) extends (UserID => Option[LightUser]) {
    def apply(u: UserID) = f(u)
  }

  final class IsBotSync(f: UserID => Boolean) extends (UserID => Boolean) {
    def apply(userId: UserID) = f(userId)
  }

  //If adding a second bot to the list will need to consider the pairing algorithm in
  //modules/tournament/src/main/arena/PairingSystem.scala
  //as this will only use the first bot in this list that is in a tournament
  //but the auto subscribeBotsToShields code will make all bots that are listed here
  //join any shield tournaments
  val tourBots: List[LightUser] = List(
    //LightUser("pst-rando", "PST-Rando", "_playstrategy".some, "BOT".some, false)
    LightUser("pst-greedy-tom", "PST-Greedy-Tom", "_playstrategy".some, "BOT".some, false)
  )

  val tourBotsIDs: List[UserID] = tourBots.map(_.id)

}
