package playstrategy

case class LightUser(id: String, name: String, title: Option[String] = None)

case class Users(p1: LightUser, p2: LightUser) {
  def apply(player: strategygames.Player) = player.fold(p1, p2)
}
