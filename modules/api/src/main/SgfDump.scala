package lila.api

import strategygames.format.FEN
import lila.analyse.{ Analysis }
import lila.game.Game
import lila.game.PgnDump.WithFlags
import lila.team.GameTeams
import play.api.i18n.Lang

final class SgfDump(
    val dumper: lila.game.SgfDump,
    @annotation.nowarn("msg=unused") _simulApi: lila.simul.SimulApi,
    @annotation.nowarn("msg=unused") _getTournamentName: lila.tournament.GetTourName,
    @annotation.nowarn("msg=unused") _getSwissName: lila.swiss.GetSwissName
)(implicit @annotation.nowarn("msg=unused") _ec: scala.concurrent.ExecutionContext) {

  @annotation.nowarn("msg=unused") implicit private val lang: Lang = lila.i18n.defaultLang

  def apply(
      game: Game,
      initialFen: Option[FEN],
      flags: WithFlags,
      teams: Option[GameTeams] = None,
      @annotation.nowarn("msg=unused") realPlayers: Option[RealPlayers] = None
  ): Fu[String] = dumper(game, initialFen, flags.tags, teams)

  def formatter(flags: WithFlags) = (
      game: Game,
      initialFen: Option[FEN],
      _: Option[Analysis],
      teams: Option[GameTeams],
      realPlayers: Option[RealPlayers]
  ) => apply(game, initialFen, flags, teams, realPlayers)
}

