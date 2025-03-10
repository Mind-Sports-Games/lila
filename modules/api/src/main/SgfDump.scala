package lila.api

import strategygames.format.FEN
import lila.analyse.{ Analysis }
import lila.game.Game
import lila.game.PgnDump.WithFlags
import lila.team.GameTeams
import play.api.i18n.Lang

final class SgfDump(
    val dumper: lila.game.SgfDump,
    simulApi: lila.simul.SimulApi,
    getTournamentName: lila.tournament.GetTourName,
    getSwissName: lila.swiss.GetSwissName
)(implicit ec: scala.concurrent.ExecutionContext) {

  implicit private val lang: Lang = lila.i18n.defaultLang

  def apply(
      game: Game,
      initialFen: Option[FEN],
      flags: WithFlags,
      teams: Option[GameTeams] = None,
      realPlayers: Option[RealPlayers] = None
  ): Fu[String] = dumper(game, initialFen, flags.tags, teams)

  def formatter(flags: WithFlags) = (
      game: Game,
      initialFen: Option[FEN],
      _: Option[Analysis],
      teams: Option[GameTeams],
      realPlayers: Option[RealPlayers]
  ) => apply(game, initialFen, flags, teams, realPlayers)

}
