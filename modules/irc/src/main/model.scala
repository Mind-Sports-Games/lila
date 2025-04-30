package lila.irc

private case class SlackMessage(
    username: String,
    text: String,
    icon: String,
    channel: String
) {

  override def toString = s"[$channel] :$icon: @$username: $text"
}

class DiscordChannel
case object Tournaments extends DiscordChannel
case object MatchMaking extends DiscordChannel

private case class DiscordMessage(
    text: String,
    channel: DiscordChannel
) {

  override def toString = s"[$channel] $text"
}
