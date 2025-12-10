package lila.irc

import lila.common.LightUser
import lila.user.User

import lila.i18n.VariantKeys

import strategygames.variant.Variant

final class DiscordApi(
    client: DiscordClient,
    baseUrl: String,
    implicit val lightUser: LightUser.Getter
)(implicit ec: scala.concurrent.ExecutionContext) {

  def matchmakingAnnouncement(text: String, variant: Variant, isHook: Boolean): Funit =
    client(
      DiscordMessage(
        text = linkifyUsers(text) + (if (isHook) s" ${gameFamilyRole(variant)}" else ""),
        channel = MatchMaking
      )
    )

  def tournamentAnnouncement(
      freq: String,
      name: String,
      variant: Variant,
      duration: String,
      id: String,
      isMedley: Boolean
  ): Funit =
    client(
      DiscordMessage(
        text = List(
          s"${gameFamilyRole(variant)}",
          s":loudspeaker: Starting Now - [${name}](<$baseUrl/tournament/${id}>)",
          s"${freqIcon(freq)} ${variantLine(VariantKeys.variantName(variant), isMedley)}",
          s":alarm_clock: $duration"
        ).mkString("\n"),
        channel = Tournaments
      )
    )

  private def variantLine(variantName: String, isMedley: Boolean) = {
    if (isMedley) s"Medley beginning with $variantName"
    else variantName
  }

  private def freqIcon(freq: String): String = freq match {
    case "weekly"       => ":trophy:"
    case "shield"       => ":shield:"
    case "medleyshield" => ":shield:"
    case "yearly"       => ":cyclone:"
    case _              => ":trophy:"
  }

  private def gameFamilyRole(variant: Variant): String = (variant.gameFamily.key, variant.key) match {
    case ("chess", "standard")     => DiscordRole.Chess.id
    case ("chess", _)              => DiscordRole.ChessVariants.id
    case ("loa", _)                => DiscordRole.LinesOfAction.id
    case ("draughts", _)           => DiscordRole.Draughts.id
    case ("dameo", _)              => DiscordRole.Draughts.id
    case ("shogi", _)              => DiscordRole.Shogi.id
    case ("xiangqi", _)            => DiscordRole.Xiangqi.id
    case ("flipello", _)           => DiscordRole.Flipello.id
    case ("amazons", _)            => DiscordRole.Amazons.id
    case ("togyzkumalak", _)       => DiscordRole.Togyzkumalak.id
    case ("oware", _)              => DiscordRole.Oware.id
    case ("breakthroughtroyka", _) => DiscordRole.BreakthroughTroyka.id
    case ("go", _)                 => DiscordRole.Go.id
    case ("backgammon", _)         => DiscordRole.Backgammon.id
    case ("abalone", _)            => DiscordRole.Abalone.id
    case _                         => DiscordRole.Default.id
  }

  private def link(url: String, name: String) = s"[$name](<$url>)"
  private val userRegex                       = lila.common.String.atUsernameRegex.pattern
  private val userReplace                     = link(baseUrl + "/@/$1", "$1")

  private def linkifyUsers(msg: String) =
    userRegex matcher msg replaceAll userReplace

  sealed abstract class DiscordRole(val id: String)

  object DiscordRole {

    case object Chess extends DiscordRole("<@&1344675363279867904>")
    case object ChessVariants extends DiscordRole("<@&1344695574112239708>")
    case object LinesOfAction extends DiscordRole("<@&1344678278547640382>")
    case object Draughts extends DiscordRole("<@&1344677542250025011>")
    case object Shogi extends DiscordRole("<@&1344678410055843860>")
    case object Xiangqi extends DiscordRole("<@&1344678872322543697>")
    case object Flipello extends DiscordRole("<@&1344678917486678066>")
    case object Amazons extends DiscordRole("<@&1344678966488731750>")
    case object Togyzkumalak extends DiscordRole("<@&1344679095526625291>")
    case object Oware extends DiscordRole("<@&1344679056683040838>")
    case object BreakthroughTroyka extends DiscordRole("<@&1344679010734440448>")
    case object Go extends DiscordRole("<@&1344679175453409323>")
    case object Backgammon extends DiscordRole("<@&1344679208735215616>")
    case object Abalone extends DiscordRole("<@&1344679243082108988>")

    case object Default extends DiscordRole("<@&1344676517237755925>")

  }

}
