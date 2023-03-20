package lila.lobby

case class WeeklyChallenge(
    currentKey: String,
    currentName: String,
    currentIcon: Option[Char],
    previousKey: String,
    previousName: String,
    previousIcon: Option[Char],
    winner: String
) {

  val currentIconChar  = currentIcon.getOrElse('5')
  val previousIconChar = previousIcon.getOrElse('5')

}
