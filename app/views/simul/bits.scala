package views.html.simul

import play.api.i18n.Lang

import lila.api.Context
import lila.app.templating.Environment.*
import lila.app.ui.ScalatagsTemplate.*
import lila.i18n.VariantKeys

object bits {

  def link(simulId: lila.simul.Simul.ID): Frag =
    a(href := routes.Simul.show(simulId))("Simultaneous exhibition")

  def jsI18n()(implicit lang: Lang) = i18nJsObject(baseTranslations)

  def notFound()(implicit ctx: Context) =
    views.html.base.layout(
      title = trans.noSimulFound.txt()
    ) {
      main(cls := "page-small box box-pad")(
        h1(trans.noSimulFound()),
        p(trans.noSimulExplanation()),
        p(a(href := routes.Simul.home)(trans.returnToSimulHomepage()))
      )
    }

  def homepageSpotlight(s: lila.simul.Simul)(implicit ctx: Context) =
    a(href := routes.Simul.show(s.id), cls := "tour-spotlight little id_@s.id")(
      img(cls := "img icon", src := staticAssetUrl("images/fire-silhouette.svg")),
      span(cls := "content")(
        span(cls := "name")(s.name, " simul"),
        span(cls := "more")(
          trans.nbPlayers.plural(s.applicants.size, s.applicants.size.localize),
          " • ",
          s.estimatedStartAt.map(momentFromNow(_)),
          s.estimatedStartAt.fold("")(_ => " • "),
          trans.join()
        )
      )
    )

  def allCreated(simuls: Seq[lila.simul.Simul])(implicit lang: play.api.i18n.Lang) =
    table(cls := "slist")(
      simuls map { simul =>
        tr(
          td(cls := "name")(a(href := routes.Simul.show(simul.id))(simul.fullName)),
          td(userIdLink(simul.hostId.some)),
          td(simul.estimatedStartAt.map(momentFromNow(_))),
          td(cls := "text", dataIcon := "p")(simul.clock.config.show),
          td(cls := "text", dataIcon := "r")(simul.applicants.size)
        )
      }
    )

  private[simul] def gameChips(sim: lila.simul.Simul)(implicit ctx: Context) = {
    val favs = favVariants
    span(cls := "games")(
      if (lila.simul.SimulForm.isAnyVariant(sim.variants)) span(cls := "game")(trans.historyAllGames())
      else
        frag(sim.variants.map { v =>
          span(cls := List("game text" -> true, "fav" -> favs(v.key)), dataIcon := v.perfIcon.toString)(
            VariantKeys.variantName(v)
          )
        }*)
    )
  }

  private[simul] def games(sim: lila.simul.Simul)(implicit ctx: Context): Frag = {
    val favs = favVariants
    frag(sim.variants.zipWithIndex.map { case (v, i) =>
      val name = VariantKeys.variantName(v)
      frag(if (i > 0) ", " else "", if (favs(v.key)) span(cls := "fav")(name) else frag(name))
    }*)
  }

  private val baseTranslations = Vector(
    trans.finished,
    trans.withdraw,
    trans.join,
    trans.cancel,
    trans.joinTheGame,
    trans.nbPlaying,
    trans.nbWins,
    trans.nbDraws,
    trans.nbLosses,
    trans.by,
    trans.signIn,
    trans.mustBeInTeam,
    trans.p1,
    trans.p2,
    trans.randomColor,
    trans.absentSimulApplicants
  ).map(_.key)
}
