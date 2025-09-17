package lila.irc

import lila.common.LightUser
import lila.user.User

final class DiscordApi(
    client: DiscordClient,
    baseUrl: String,
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
          s":loudspeaker: Starting Now - [${name}](<$baseUrl/tournament/${id}>)",
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

  //TODO Dameo Add Discord channel
  def gameGroupLink(gameFamilyKey: String, variant: String): String = (gameFamilyKey, variant) match {
    case ("chess", "Chess")        => "<@&1344675363279867904>"
    case ("chess", _)              => "<@&1344695574112239708>"
    case ("loa", _)                => "<@&1344678278547640382>"
    case ("draughts", _)           => "<@&1344677542250025011>"
    case ("shogi", _)              => "<@&1344678410055843860>"
    case ("xiangqi", _)            => "<@&1344678872322543697>"
    case ("flipello", _)           => "<@&1344678917486678066>"
    case ("amazons", _)            => "<@&1344678966488731750>"
    case ("togyzkumalak", _)       => "<@&1344679095526625291>"
    case ("oware", _)              => "<@&1344679056683040838>"
    case ("breakthroughtroyka", _) => "<@&1344679010734440448>"
    case ("go", _)                 => "<@&1344679175453409323>"
    case ("backgammon", _)         => "<@&1344679208735215616>"
    case ("abalone", _)            => "<@&1344679243082108988>"
    case _                         => "<@&1344676517237755925>"
  }

  private def link(url: String, name: String) = s"[$name](<$url>)"
  private val userRegex                       = lila.common.String.atUsernameRegex.pattern
  private val userReplace                     = link(baseUrl + "/@/$1", "$1")

  private def linkifyUsers(msg: String) =
    userRegex matcher msg replaceAll userReplace
}
