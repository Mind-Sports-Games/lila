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

  implicit val lightUserWrites: OWrites[LightUser] = OWrites[LightUser] { u =>
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

  val poolBots: List[LightUser] = List(
    LightUser("ps-greedy-one-move", "PS-Greedy-One-Move", "_playstrategy".some, "BOT".some, false),
    LightUser("ps-greedy-two-move", "PS-Greedy-Two-Move", "_playstrategy".some, "BOT".some, false),
    LightUser("ps-greedy-four-move", "PS-Greedy-Four-Move", "_playstrategy".some, "BOT".some, false)
    //LightUser("bot1", "bot1", "_playstrategy".some, "BOT".some, false)
  )

  val stockfishBots: List[LightUser] = List(
    LightUser("stockfish-level1", "Stockfish-Level1", "_playstrategy".some, "BOT".some, false),
    LightUser("stockfish-level2", "Stockfish-Level2", "_playstrategy".some, "BOT".some, false),
    LightUser("stockfish-level3", "Stockfish-Level3", "_playstrategy".some, "BOT".some, false),
    LightUser("stockfish-level4", "Stockfish-Level4", "_playstrategy".some, "BOT".some, false),
    LightUser("stockfish-level5", "Stockfish-Level5", "_playstrategy".some, "BOT".some, false),
    LightUser("stockfish-level6", "Stockfish-Level6", "_playstrategy".some, "BOT".some, false),
    LightUser("stockfish-level7", "Stockfish-Level7", "_playstrategy".some, "BOT".some, false),
    LightUser("stockfish-level8", "Stockfish-Level8", "_playstrategy".some, "BOT".some, false)
  )

  val randomBot = LightUser(
    id = "ps-random-mover",
    name = "PS-Random-Mover",
    country = "_playstrategy".some,
    title = "BOT".some,
    isPatron = false
  )

  val tourBotsIDs: List[UserID] = tourBots.map(_.id)

  val poolBotsIDs: List[UserID] = poolBots.map(_.id)

  val stockfishBotsIDs: List[UserID] = stockfishBots.map(_.id)

  val lobbyBotsIDs: List[UserID] = List(randomBot.id) ++ poolBotsIDs

  val easiestPoolBotId: UserID = "ps-greedy-one-move"

  val psBotsIDs = tourBotsIDs ++ poolBotsIDs ++ stockfishBotsIDs ++ List(randomBot.id)

}
