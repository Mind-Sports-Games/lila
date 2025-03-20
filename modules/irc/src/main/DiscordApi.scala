package lila.irc

import lila.common.LightUser
import lila.user.User

final class DiscordApi(
    client: DiscordClient,
    implicit val lightUser: LightUser.Getter
)(implicit ec: scala.concurrent.ExecutionContext) {

  def matchmakingAnnouncement(text: String, gameFamilyKey: String, variant: String, isHook: Boolean): Funit =
    client(
      DiscordMessage(
        text = linkifyUsers(text) + (if (isHook) s" ${gameGroupLink(gameFamilyKey, variant)}" else ""),
        channel = MatchMaking
      )
    )

  def tournamentAnnouncement(
      freq: String,
      name: String,
      variant: String,
      gameFamilyKey: String,
      duration: String,
      id: String,
      isMedley: Boolean
  ): Funit =
    client(
      DiscordMessage(
        text = List(
          s"${gameGroupLink(gameFamilyKey, variant)}",
          s":loudspeaker: Starting Now - [${name}](https://playstrategy.org/tournament/${id})",
          s"${freqIcon(freq)} ${variantLine(variant, isMedley)}",
          s":alarm_clock: $duration"
        ).mkString("\n"),
        channel = Tournaments
      )
    )

  def variantLine(variant: String, isMedley: Boolean) = {
    if (isMedley) s"Medley beginning with $variant"
    else variant
  }

  def freqIcon(freq: String): String = freq match {
    case "weekly"       => ":trophy:"
    case "shield"       => ":shield:"
    case "medleyshield" => ":shield:"
    case "yearly"       => ":cyclone:"
    case _              => ":trophy:"
  }

  def gameGroupLink(gameFamilyKey: String, variant: String): String = (gameFamilyKey, variant) match {
    case ("chess", "Chess")        => "@chess"
    case ("chess", _)              => "@chess-variants"
    case ("loa", _)                => "@lines-of-action"
    case ("flipello", _)           => "@othello"
    case ("togyzkumalak", _)       => "@togyzqumalaq"
    case ("breakthroughtroyka", _) => "@breakthrough"
    case _                         => s"@${gameFamilyKey}"
  }

  private def link(url: String, name: String) = s"[$name]($url)"
  private val userRegex                       = lila.common.String.atUsernameRegex.pattern
  private val userReplace                     = link("https://playstrategy.org/@/$1", "$1")

  private def linkifyUsers(msg: String) =
    userRegex matcher msg replaceAll userReplace
}
