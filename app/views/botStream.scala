package views.html

import play.api.data.Form

import lila.api.Context
import lila.app.templating.Environment.*
import lila.app.ui.ScalatagsTemplate.*
import lila.bot.{ BotStream, BotStreamForm, BotStreamStatus, BotVsBotConfig, BotVsBotCoordinator }
import lila.game.{ Game, Pov }

object botStream {

  private val dataVariants = attr("data-variants")
  private val dataLabel    = attr("data-label")

  private val priorityHelp = "Ranked above other bot games on TV. Use it to show off a newly released game."

  private val priorityIcon =
    span(cls := "bot-streams__priority", dataIcon := "S", title := s"Priority stream. $priorityHelp")

  def create(form: Form[?])(implicit ctx: Context) =
    layout(title = "New bot stream", css = "mod.form", moreJs = formJs) {
      div(cls := "crud page-menu__content box box-pad")(
        h1("New bot stream"),
        postForm(cls := "content_box_content form3", action := routes.BotStream.create)(inForm(form))
      )
    }

  def edit(stream: BotStream, form: Form[?])(implicit ctx: Context) =
    layout(title = stream.name, css = "mod.form", moreJs = formJs) {
      div(cls := "crud edit page-menu__content box box-pad")(
        div(cls := "box__top")(
          h1(
            stream.name,
            span("Created by ", usernameOrId(stream.createdBy), " ", momentFromNow(stream.createdAt)),
            span("Updated ", momentFromNow(stream.updatedAt))
          ),
          st.form(cls := "box__top__actions", action := routes.BotStream.cloneS(stream.id), method := "get")(
            form3.submit("Clone", "".some, klass = "button-green button-empty")
          )
        ),
        standardFlash(),
        postForm(cls := "content_box_content form3", action := routes.BotStream.update(stream.id))(
          inForm(form)
        )
      )
    }

  def manager(
      streams: List[BotStream],
      coordinator: BotVsBotCoordinator,
      status: Map[BotStream.ID, BotStreamStatus],
      games: List[Game]
  )(implicit ctx: Context) = {
    val title = "Bot streams"
    layout(title = title) {
      div(cls := "crud bot-streams page-menu__content box")(
        div(cls := "box__top")(
          h1(title),
          div(cls := "box__top__actions")(
            a(cls := "button button-green", href := routes.BotStream.form, dataIcon := "O")
          )
        ),
        div(cls := "bot-streams__messages")(
          standardFlash(),
          (!coordinator.autoStart).option(
            p(
              strong("Auto-start is off in config (autoBotvsBot.enabled)."),
              " Streams only run once started here, and do not resume after a restart."
            )
          )
        ),
        table(cls := "slist slist-pad")(
          thead(
            tr(
              th("Stream"),
              th("Matchups"),
              th("Clock"),
              th(utcLink, " end"),
              th("State"),
              th
            )
          ),
          tbody(
            streams.map { s =>
              val live = status.get(s.id)
              tr(
                td(dataLabel := "Stream")(
                  a(href := routes.BotStream.edit(s.id))(strong(s.priority.option(priorityIcon), s.name))
                ),
                td(dataLabel := "Matchups")(
                  s.matchups.map { m =>
                    div(
                      userIdLink(m.p1.some, withOnline = false, withTitle = false),
                      " vs ",
                      userIdLink(m.p2.some, withOnline = false, withTitle = false),
                      ": ",
                      variantsSummary(m.variants)
                    )
                  }
                ),
                td(cls := "bot-streams__clock", dataLabel := "Clock")(s.clock.show),
                td(dataLabel := "UTC end")(
                  s.until.fold[Frag]("Until stopped") { until =>
                    frag(showDateTimeUTC(until), br, momentFromNow(until))
                  }
                ),
                td(dataLabel := "State")(
                  strong(coordinator.state(s)),
                  live.map { l =>
                    frag(
                      br,
                      l.currentGame.fold[Frag]("Waiting for a game") { id =>
                        a(href := routes.Round.watcher(id, "p1"))(s"Game $id")
                      },
                      s" (${l.cyclePosition}/${l.cycleLength})",
                      l.missingBots.nonEmpty.option(
                        frag(br, em(s"Offline: ${l.missingBots.mkString(", ")}"))
                      )
                    )
                  }
                ),
                td(cls := "bot-streams__actions")(
                  div(
                    live.isEmpty.option(command(routes.BotStream.start(s.id), "Start")),
                    (live.isDefined || s.active).option(command(routes.BotStream.stop(s.id), "Stop")),
                    live.isDefined.option(command(routes.BotStream.restart(s.id), "Restart")),
                    command(
                      routes.BotStream.delete(s.id),
                      "Delete",
                      klass = "button-red confirm",
                      confirm = s"Delete the bot stream ${s.name}?".some
                    )
                  )
                )
              )
            }
          )
        ),
        games.nonEmpty.option(
          frag(
            h2("Games in progress"),
            div(cls := "bot-streams__games now-playing")(
              streams.flatMap { s =>
                status.get(s.id).flatMap(_.currentGame).flatMap(id => games.find(_.id == id)).map { game =>
                  div(cls := "bot-streams__game")(
                    a(href := routes.BotStream.edit(s.id))(s.name),
                    views.html.game.mini(Pov.naturalOrientation(game))
                  )
                }
              }
            )
          )
        )
      )
    }
  }

  private def variantsSummary(variants: List[strategygames.variant.Variant]) =
    if (variants.sizeIs >= 3) s"${variants.size} variants" else variants.map(_.name).mkString(", ")

  private def command(
      call: play.api.mvc.Call,
      label: String,
      klass: String = "",
      confirm: Option[String] = None
  ) =
    postForm(action := call)(
      submitButton(cls := s"button button-thin button-empty $klass", title := confirm)(label)
    )

  private def matchupRow(form: Form[?], index: String, number: String)(implicit ctx: Context) = {
    val botChoices = BotVsBotConfig.psBots.map(u => u.id -> u.name)
    form3.group(form(s"matchups[$index]"), raw(s"Matchup $number"), klass = "bot-stream__matchup") { _ =>
      frag(
        form3.split(
          form3.group(form(s"matchups[$index].p1"), raw("Bot 1"), half = true)(
            form3.select(_, botChoices, default = "".some)
          ),
          form3.group(form(s"matchups[$index].p2"), raw("Bot 2"), half = true)(
            form3.select(_, botChoices, default = "".some)
          )
        ),
        form3.group(form(s"matchups[$index].variants"), raw("Variant keys")) { f =>
          frag(
            form3.textarea(f)(rows := 3),
            div(
              BotVsBotConfig.variantGroups.map { case (name, variants) =>
                button(
                  tpe          := "button",
                  cls          := "button button-thin button-empty bot-stream__fill",
                  dataVariants := variants.map(_.key).mkString(" ")
                )(s"+ $name")
              },
              button(
                tpe          := "button",
                cls          := "button button-thin button-empty bot-stream__fill",
                dataVariants := ""
              )("Clear")
            )
          )
        }
      )
    }
  }

  private def inForm(form: Form[?])(implicit ctx: Context) = {
    val matchupRows = math.max(form("matchups").indexes.maxOption.fold(0)(_ + 1), 1)
    frag(
      form3.globalError(form),
      form3.split(
        form3.group(form("name"), raw("Name"), half = true)(form3.input(_)),
        form3.checkbox(
          form("priority"),
          frag(priorityIcon, " Priority"),
          help = raw(priorityHelp).some,
          half = true
        )
      ),
      form3.split(
        form3.group(form("clock.limit"), trans.clockInitialTime(), half = true)(
          form3.select(_, BotStreamForm.clockTimeChoices)
        ),
        form3.group(form("clock.increment"), trans.clockIncrement(), klass = "clockIncrement", half = true)(
          form3.select(_, BotStreamForm.clockIncrementChoices)
        ),
        form3.group(form("clock.delay"), trans.clockDelay(), klass = "clockDelay", half = true)(
          form3.select(_, BotStreamForm.clockDelayChoices)
        )
      ),
      form3.split(
        form3.checkbox(form("clock.useByoyomi"), trans.useByoyomi()),
        form3.checkbox(form("clock.useBronsteinDelay"), trans.useBronsteinDelay()),
        form3.checkbox(form("clock.useSimpleDelay"), trans.useSimpleDelay())
      ),
      form3.split(
        form3.group(form("clock.byoyomi"), trans.clockByoyomi(), klass = "byoyomiClock", half = true)(
          form3.select(_, BotStreamForm.clockByoyomiChoices)
        ),
        form3.group(form("clock.periods"), trans.numberOfPeriods(), klass = "byoyomiPeriods", half = true)(
          form3.select(_, BotStreamForm.periodsChoices)
        )
      ),
      form3.group(
        form("until"),
        frag("End date ", strong(utcLink)),
        help = raw("Leave empty to run until stopped").some
      )(form3.flatpickr(_, utc = true)),
      form3.group(
        form("matchups"),
        raw("Matchups"),
        klass = "bot-stream__matchups-group",
        help = raw(
          s"Every variant of a matchup is played with both bots taking each side. Up to ${BotStreamForm.maxMatchups} matchups; an empty one is skipped."
        ).some
      ) { _ =>
        frag(
          div(cls := "bot-stream__matchups")(
            (0 until matchupRows).map(i => matchupRow(form, i.toString, (i + 1).toString))
          ),
          tag("template")(cls := "bot-stream__template")(
            matchupRow(form, "__INDEX__", "__NUMBER__")
          ),
          button(
            tpe                  := "button",
            cls                  := "button button-thin bot-stream__add",
            attr("data-max")     := BotStreamForm.maxMatchups,
            (matchupRows >= BotStreamForm.maxMatchups).option(style := "display: none")
          )("Add matchup"),
          div(
            strong("All variant keys"),
            p(BotVsBotConfig.streamableVariants.map(_.key).mkString(", "))
          )
        )
      },
      form3.actions(
        a(href := routes.BotStream.manager)(trans.cancel()),
        form3.submit(trans.apply())
      )
    )
  }

  private def formJs = frag(jsModule("flatpickr"), jsModule("botStreamForm"))

  private def layout(title: String, css: String = "mod.misc", moreJs: Frag = emptyFrag)(body: Frag)(implicit
      ctx: Context
  ) =
    views.html.base.layout(
      title = title,
      moreCss = cssTag(css),
      moreJs = moreJs
    ) {
      main(cls := "page-menu")(
        mod.menu("bot-stream"),
        body
      )
    }
}
