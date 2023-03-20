package lila.lobby

case class WeeklyChallenge(
    currentKey: String,
    currentName: String,
    currentIcon: Option[Char],
    previousKey: String,
    previousName: String,
    winner: String
) {

  val iconChar = currentIcon.getOrElse('5')
}
