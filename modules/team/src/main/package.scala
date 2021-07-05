package lila

package object team extends PackageObject {

  private[team] def logger = lila.log("team")

  type GameTeams = strategygames.chess.Color.Map[Team.ID]
}
