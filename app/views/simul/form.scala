package views.html.simul

import play.api.data.Form
import strategygames.variant.Variant

import lila.api.Context
import lila.app.templating.Environment.*
import lila.app.ui.ScalatagsTemplate.*
import lila.hub.LeaderTeam
import lila.i18n.VariantKeys
import lila.simul.Simul
import lila.simul.SimulForm

object form {

  def create(form: Form[SimulForm.Setup], teams: List[LeaderTeam])(implicit
      ctx: Context
  ) =
    views.html.base.layout(
      title = trans.hostANewSimul.txt(),
      moreCss = cssTag("simul.form"),
      // moreJs = jsModule("flatpickr")
      moreJs = jsModule("simulForm")
    ) {
      main(cls := "box box-pad page-small simul-form")(
        h1(trans.hostANewSimul()),
        postForm(cls := "form3", action := routes.Simul.create)(
          br,
          p(trans.whenCreateSimul()),
          br,
          br,
          formContent(form, teams, none),
          form3.actions(
            a(href := routes.Simul.home)(trans.cancel()),
            form3.submit(trans.hostANewSimul(), icon = "g".some)
          )
        )
      )
    }

  def edit(form: Form[SimulForm.Setup], teams: List[LeaderTeam], simul: Simul)(implicit
      ctx: Context
  ) =
    views.html.base.layout(
      title = s"Edit ${simul.fullName}",
      moreCss = cssTag("simul.form"),
      // moreJs = jsModule("flatpickr")
      moreJs = jsModule("simulForm")
    ) {
      main(cls := "box box-pad page-small simul-form")(
        h1(s"Edit ${simul.fullName}"),
        postForm(cls := "form3", action := routes.Simul.update(simul.id))(
          formContent(form, teams, simul.some),
          form3.actions(
            a(href := routes.Simul.show(simul.id))(trans.cancel()),
            form3.submit(trans.save(), icon = "g".some)
          )
        ),
        postForm(cls := "terminate", action := routes.Simul.abort(simul.id))(
          submitButton(dataIcon := "j", cls := "text button button-red confirm")(
            "Cancel the simul"
          )
        )
      )
    }

  private def formContent(form: Form[SimulForm.Setup], teams: List[LeaderTeam], simul: Option[Simul])(implicit
      ctx: Context
  ) = {
    import lila.simul.SimulForm.*
    frag(
      globalError(form),
      form3.group(form("name"), trans.name()) { f =>
        div(
          form3.input(f),
          " Simul",
          br,
          small(cls := "form-help")(trans.inappropriateNameWarning())
        )
      },
      form3.group(form("variant"), trans.simulVariantsHint()) { f =>
        frag(
          {
            val encode  = (v: Variant) => s"${v.gameFamily.id}_${v.id}"
            val options = translatedAllVariantChoicesWithVariants(encode)
            val checks  = form.value
              .map(_.variants.map(_.toString))
              .getOrElse(simul.filterNot(s => SimulForm.isAnyVariant(s.variants)).so(_.variants.map(encode)))
              .toSet
            val variantOf = Variant.all.map(v => encode(v) -> v).toMap
            // a chip that toggles the game: the index stays the option's own, so the form is posted
            // as it always was
            def chip(option: ((String, String, Option[String]), Int)) = {
              val ((value, text, hint), index) = option
              label(
                cls      := "text variants__chip",
                dataIcon := variantOf.get(value).fold("")(_.perfIcon.toString),
                title    := hint
              )(
                input(
                  tpe      := "checkbox",
                  name     := s"${form("variants").name}[$index]",
                  st.value := value,
                  checks(value).option(checked)
                ),
                raw(text)
              )
            }
            // family by family, as on the tournament pages: a family chip shows its games, a family of
            // one game is its own chip. Shown first: the first family with a pick, else none.
            val indexed = options.zipWithIndex
            val byGroup = displayedGameGroups.map { g =>
              g -> indexed.filter { case ((value, _, _), _) => variantOf.get(value).exists(g.variants.contains) }
            }
            val placed = byGroup.flatMap(_._2).map(_._2).toSet
            val open = byGroup.collectFirst {
              case (g, items) if items.size > 1 && items.exists(i => checks(i._1._1)) => g
            }
            div(cls := "variants")(
              byGroup.map[Frag] {
                case (g, items) if items.size > 1 =>
                  val inputId = s"variantGroup-${g.key}"
                  // the games and their count come after the radio: the count is a CSS counter over them
                  div(cls := "variants__family")(
                    input(
                      tpe      := "radio",
                      id       := inputId,
                      name     := "variantGroup",
                      st.value := g.key,
                      open.contains(g).option(checked)
                    ),
                    div(cls := "variants__group")(items.map(chip)),
                    label(
                      cls      := "text variants__family-name",
                      `for`    := inputId,
                      dataIcon := g.variants.headOption.fold("")(_.perfIcon.toString)
                    )(VariantKeys.gameGroupName(g), span(cls := "variants__count"))
                  )
                case _ => emptyFrag
              },
              // the games of a family of one game are toggled where they stand, so on a line of their own under the
              // families
              div(cls := "variants__singles")(
                byGroup.collect { case (_, items) if items.size == 1 => items.map(chip) },
                indexed.filterNot(i => placed(i._2)).map(chip)
              )
            )
          },
          errMsg(f)
        )
      },
      form3.split(
        form3.group(
          form("clock.limit"),
          trans.clockIncrement(),
          help = frag(
            trans.simulClockHint().some,
            a(href := s"${routes.Page.lonePage("clocks")}", target := "_blank")("Clock details here")
          ).some,
          half = true
        )(form3.select(_, clockTimeChoices)),
        form3.group(form("clock.increment"), trans.clockIncrement(), klass = "clockIncrement", half = true)(
          form3.select(_, clockIncrementChoices)
        ),
        form3.group(form("clock.delay"), trans.clockDelay(), klass = "clockDelay", half = true)(
          form3.select(_, clockDelayChoices)
        )
      ),
      form3.split(
        form3.checkbox(form("clock.useByoyomi"), trans.useByoyomi()),
        form3.checkbox(form("clock.useBronsteinDelay"), trans.useBronsteinDelay()),
        form3.checkbox(form("clock.useSimpleDelay"), trans.useSimpleDelay())
      ),
      form3.split(
        form3.group(form("clock.byoyomi"), trans.clockByoyomi(), klass = "byoyomiClock", half = true)(
          form3.select(_, SimulForm.clockByoyomiChoices)
        ),
        form3.group(form("clock.periods"), trans.numberOfPeriods(), klass = "byoyomiPeriods", half = true)(
          form3.select(_, SimulForm.periodsChoices)
        )
      ),
      form3.split(
        form3.group(
          form("clockExtra"),
          trans.simulHostExtraTime(),
          help = trans.simulAddExtraTime().some,
          half = true
        )(
          form3.select(_, clockExtraChoices)
        ),
        form3.group(form("playerIndex"), raw("Host player number for each game (who starts)"), half = true)(
          form3.select(_, playerIndexChoices)
        )
      ),
      form3.split(
        teams.nonEmpty.option(
          form3.group(form("team"), raw("Only members of team"), half = true)(
            form3.select(_, List(("", "No Restriction")) ::: teams.map(_.pair))
          )
        ),
        form3.group(
          form("position"),
          trans.startPosition(),
          klass = "position",
          half = true,
          help = views.html.tournament.form.positionInputHelp.some
        )(form3.input(_))
      ),
      form3.group(
        form("estimatedStartAt"),
        frag("Estimated start time"),
        half = true
      )(form3.flatpickr(_)),
      form3.group(
        form("text"),
        raw("Simul description"),
        help = frag("Anything you want to tell the participants?").some
      )(form3.textarea(_)(rows := 10)),
      ctx.me
        .exists(_.isSimulFeatured)
        .option(
          form3.checkbox(
            form("featured"),
            frag("Feature on playstrategy.org/simul"),
            help =
              frag("Show your simul to everyone on playstrategy.org/simul. Disable for private simuls.").some
          )
        )
    )
  }
}
