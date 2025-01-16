package views.html.game

import lila.api.Context
import lila.app.templating.Environment._
import lila.app.ui.ScalatagsTemplate._
import lila.game.{ Game, Pov }
import lila.i18n.VariantKeys

import strategygames.format.FEN
import strategygames.variant.Variant

import controllers.routes

object bits {

  def gameIcon(game: Game): Char =
    game.perfType match {
      case _ if game.fromPosition         => '*'
      case _ if game.imported             => '/'
      case Some(p) if game.variant.exotic => p.iconChar
      case _ if game.hasAi                => 'n'
      case Some(p)                        => p.iconChar
      case _                              => '8'
    }

  def sides(
      pov: Pov,
      initialFen: Option[FEN],
      tour: Option[lila.tournament.TourAndTeamVs],
      cross: Option[lila.game.Crosstable.WithMatchup],
      simul: Option[lila.simul.Simul],
      userTv: Option[lila.user.User] = None,
      bookmarked: Boolean,
      swissPairingGames: Option[lila.swiss.SwissPairingGames]
  )(implicit ctx: Context) =
    div(
      side.meta(pov, initialFen, tour, simul, userTv, bookmarked = bookmarked, swissPairingGames),
      cross.map { c =>
        div(cls := "crosstable")(crosstable(ctx.userId.fold(c)(c.fromPov), pov.gameId.some))
      }
    )

  def variantLink(
      variant: Variant,
      name: String,
      initialFen: Option[FEN] = None,
      matchPoints: Option[Int] = None
  ) =
    a(
      cls := "variant-link",
      href := (variant match {
        case Variant.Chess(strategygames.chess.variant.Standard) => "https://en.wikipedia.org/wiki/Chess"
        case Variant.Chess(strategygames.chess.variant.FromPosition) =>
          s"""${routes.Editor.index}?fen=${initialFen.??(_.value.replace(' ', '_'))}"""
        case v => routes.Page.variant(v.key).url
      }),
      targetBlank,
      title := VariantKeys.variantTitle(variant)
    )(matchPoints.fold("")(p => s"${p}pt ") + name)

  def medleyLink =
    a(
      cls := "variant-link",
      href := routes.Page.loneBookmark("medley"),
      targetBlank,
      title := "Medley"
    )("MEDLEY")

}
