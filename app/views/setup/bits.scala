package views.html.setup

import controllers.routes
import play.api.data.{ Field, Form }

import lila.api.Context
import lila.app.templating.Environment._
import lila.app.ui.ScalatagsTemplate._
import lila.user.User

private object bits {

  val prefix = "sf_"

  def fenInput(field: Field, strict: Boolean, validFen: Option[lila.setup.ValidFen])(implicit
      ctx: Context
  ) = {
    val url = field.value.fold(routes.Editor.index.url) { fen =>
      routes.Editor.load(fen, None).url
    }
    div(cls := "fen_position optional_config")(
      frag(
        div(
          cls := "fen_form",
          dataValidateUrl := s"""${routes.Setup.validateFen}${strict.??("?strict=1")}"""
        )(
          form3.input(field)(st.placeholder := trans.pasteTheFenStringHere.txt()),
          a(cls := "button button-empty", dataIcon := "m", title := trans.boardEditor.txt(), href := url)
        ),
        a(cls := "board_editor", href := url)(
          span(cls := "preview")(
            validFen.map { vf =>
              views.html.board.bits.mini(vf.fen, vf.playerIndex, vf.situation.board.variant.key)(div)
            }
          )
        )
      )
    )
  }

  def renderGameGroupOptions(form: Form[_], libs: List[SelectChoice])(implicit ctx: Context) =
    div(cls := "gameGroup_choice buttons collapsible optional_config")(
      div(cls := "section_title")("Game Group"),
      div(id := "gameGroup_icons")(
        renderIconRadios(form("gameGroup"), libs),
        renderSelectedChoice(form("gameGroup"), libs)
      )
    )

  def renderVariantOptions(form: Form[_], libs: List[SelectChoice])(implicit ctx: Context) =
    div(cls := "variant_choice buttons collapsible")(
      div(cls := "section_title")(
        a(
          cls := "remove_color",
          title := "More info",
          href := s"${routes.Page.variantHome}",
          target := "_blank"
        )(trans.variant())
      ),
      div(id := "variant_icons")(
        renderIconRadios(form("variant"), libs),
        renderSelectedChoice(form("variant"), libs)
      ),
      renderRatings()
    )

  def renderRatings()(implicit ctx: Context) =
    ctx.me.ifFalse(ctx.blind).map { me =>
      div(cls := "ratings")(
        form3.hidden("rating", "?"),
        lila.rating.PerfType.nonPuzzle.map { perfType =>
          div(cls := perfType.key)(
            trans.perfRatingX(
              raw(s"""<strong>${me
                .perfs(perfType.key)
                .map(_.intRating)
                .getOrElse("?")}</strong>""")
            )
          )
        }
      )
    }

  def renderVariant(
      form: Form[_],
      variants: List[(SelectChoice, List[SelectChoice])]
  )(implicit ctx: Context) =
    div(cls := "variant label_select")(
      renderLabel(
        form("Variant"),
        a(
          cls := "remove_color",
          title := "More info",
          href := s"${routes.Page.variantHome}",
          target := "_blank"
        )(
          trans.variant()
        )
      ),
      renderSelectWithOptGroups(
        form("variant"),
        variants.map { case (gf, v) =>
          (
            gf,
            v.filter { case (id, _, _) =>
              ctx.noBlind || lila.game.Game.blindModeVariants.exists(_.id.toString == id)
            }
          )
        }
      )
    )

  def renderSelect(
      field: Field,
      options: Seq[SelectChoice],
      compare: (String, String) => Boolean = (a, b) => a == b
  ) =
    select(id := s"$prefix${field.id}", name := field.name)(
      renderOptions(field, options, compare)
    )

  def renderSelectWithOptGroups(
      field: Field,
      options: List[(SelectChoice, Seq[SelectChoice])],
      compare: (String, String) => Boolean = (a, b) => a == b
  ) =
    select(id := s"$prefix${field.id}", name := field.name)(
      options.map { case ((ogValue, ogName, _), opts) =>
        optgroup(name := ogName)(
          renderOptions(field, opts, compare, ogValue + '_')
        )
      }
    )

  private def renderOptions(
      field: Field,
      options: Seq[SelectChoice],
      compare: (String, String) => Boolean = (a, b) => a == b,
      optValuePrefix: String = ""
  ) =
    options.map { case (value, name, _) =>
      option(
        st.value := optValuePrefix + value,
        field.value.exists(v => compare(v, value)) option selected
      )(name)
    }

  def renderSelectedChoice(field: Field, options: Seq[SelectChoice], userPart: Option[Tag] = None) =
    options.map { case (key, icon, hint) =>
      div(cls := s"${field.name}_$key choice", dataIcon := icon)(
        userPart == None option hint,
        (key == "bot" || key == "friend") option userPart
      )
    }

  def renderIconRadios(field: Field, options: Seq[SelectChoice]) =
    st.group(cls := "radio")(
      options.map { case (key, icon, hint) =>
        div(
          input(
            tpe := "radio",
            id := s"$prefix${field.id}_$key",
            st.name := field.name,
            value := key,
            field.value.has(key) option checked
          ),
          label(
            cls := "required",
            dataIcon := icon,
            `for` := s"$prefix${field.id}_$key"
          )(hint)
        )
      }
    )

  def renderRadios(field: Field, options: Seq[SelectChoice]) =
    st.group(cls := "radio")(
      options.map { case (key, name, _) =>
        div(
          input(
            tpe := "radio",
            id := s"$prefix${field.id}_$key",
            st.name := field.name,
            value := key,
            field.value.has(key) option checked
          ),
          label(
            cls := "required",
            `for` := s"$prefix${field.id}_$key"
          )(name)
        )
      }
    )

  def renderInput(field: Field) =
    input(name := field.name, value := field.value, tpe := "hidden")

  def renderDissociatedRange(field: Field) =
    frag(
      renderInput(field)(cls := "range-value"),
      input(name := s"${field.name}_range", tpe := "range")(cls := "range")
    )

  def renderLabel(field: Field, content: Frag) =
    label(`for` := s"$prefix${field.id}")(content)

  def renderCheckbox(field: Field, labelContent: Frag) = div(
    span(cls := "form-check-input")(
      form3.cmnToggle(s"$prefix${field.id}", field.name, field.value.has("true"))
    ),
    renderLabel(field, labelContent)
  )

  def renderMultiMatch(form: Form[_])(implicit ctx: Context) =
    div(cls := "multi_match", title := trans.multiMatchDefinition.txt())(
      renderCheckbox(form("multiMatch"), trans.multiMatch())
    )

  def renderGoOptions(form: Form[_])(implicit ctx: Context) =
    div(cls := "go_config optional_config")(
      div(cls := "go_handicap_choice range")(
        trans.goHandicap(),
        ": ",
        span(form("goHandicap").value),
        renderDissociatedRange(form("goHandicap"))
      ),
      div(cls := "go_komi_choice range")(
        trans.goKomi(),
        ": ",
        span(form("goKomi").value),
        renderDissociatedRange(form("goKomi"))
      )
    )

  def renderBackgammonOptions(form: Form[_])(implicit ctx: Context) =
    div(cls := "backgammon_config optional_config")(
      div(cls := "backgammon_points_choice range")(
        trans.backgammonPoints(),
        ": ",
        span(form("backgammonPoints").value),
        renderDissociatedRange(form("backgammonPoints"))
      )
    )

  def renderOpponentOptions(form: Form[_], userPart: Option[Tag])(implicit ctx: Context) =
    div(cls := "opponent_choices buttons collapsible")(
      div(cls := "section_title")("Opponent"),
      renderIconRadios(form("opponent"), translatedOpponentChoices),
      renderSelectedChoice(form("opponent"), translatedOpponentChoices, userPart),
      renderBotChoice(form),
      renderRatingRange(form("ratingRange"))
    )

  def renderBotChoice(form: Form[_])(implicit ctx: Context) =
    div(cls := "bot_choice buttons")(
      div(cls := "bot_title")("PS Greedy Bot"),
      renderRadios(form("bot"), translatedPSBotChoices),
      div(cls := "bot_title")("Stockfish"),
      renderRadios(form("bot"), translatedStockfishChoices)
    )

  def renderRatingRange(field: Field)(implicit ctx: Context) =
    div(cls := "rating-range-config optional_config")(
      trans.ratingRange(),
      div(cls := "rating-range") {
        frag(
          renderInput(field),
          input(
            name := s"${field.name}_range_min",
            tpe := "range",
            cls := "range rating-range__min"
          ),
          span(cls := "rating-min"),
          "/",
          span(cls := "rating-max"),
          input(
            name := s"${field.name}_range_max",
            tpe := "range",
            cls := "range rating-range__max"
          )
        )
      }
    )

  def renderPlayerIndexOptions(field: Field)(implicit ctx: Context) =
    div(cls := "playerIndex collapsible")(
      div(cls := "section_title")("Side"),
      div(cls := "playerIndex_choices buttons")(
        st.group(cls := "radio")(
          translatedSideChoices.map { case (key, name, _) =>
            div(
              input(
                tpe := "radio",
                id := s"$prefix${field.id}_$key",
                st.name := field.name,
                value := key,
                field.value.has(key) option checked
              ),
              label(
                cls := s"playerIndex__button button button-metal $key",
                `for` := s"$prefix${field.id}_$key"
              )(i, div(name))
            )
          }
        ),
        translatedSideChoices.map { case (key, name, _) =>
          div(
            cls := s"${field.name}_$key choice playerIndex__button $key"
          )(i, div(name))
        }
      )
    )

  def renderModeOptions(form: Form[_])(implicit ctx: Context) =
    div(cls := "mode_choice buttons collapsible optional_config")(
      div(cls := "section_title")("Mode"),
      renderIconRadios(form("mode"), translatedModeIconChoices),
      renderSelectedChoice(form("mode"), translatedModeIconChoices)
    )

  def renderTimeModeOptions(form: Form[_])(implicit ctx: Context) =
    div(cls := "time_mode_defaults optional_config collapsible")(
      div(cls := "section_title")(
        a(
          cls := "remove_color",
          title := "More info",
          href := s"${routes.Page.lonePage("clocks")}",
          target := "_blank"
        )(trans.timeControl())
      ),
      renderIconRadios(form("timeModeDefaults"), translatedTimeModeIconChoices),
      renderSelectedChoice(form("timeModeDefaults"), translatedTimeModeIconChoices)
    )

  def renderTimeMode(form: Form[_], allowAnon: Boolean, allowCorrespondence: Boolean)(implicit ctx: Context) =
    div(cls := "time_mode_config optional_config")(
      div(
        cls := List(
          "label_select" -> true,
          "none"         -> (ctx.isAnon && !allowAnon)
        )
      )(
        renderLabel(
          form("timeMode"),
          a(
            cls := "remove_color",
            title := "More info",
            href := s"${routes.Page.lonePage("clocks")}",
            target := "_blank"
          )(trans.timeControl())
        ),
        renderSelect(
          form("timeMode"),
          if (allowCorrespondence) translatedTimeModeChoices else translatedTimeModeChoicesLive
        )
      ),
      if (ctx.blind)
        frag(
          div(cls := "time_choice")(
            renderLabel(form("time"), trans.minutesPerSide()),
            renderSelect(form("time"), clockTimeChoices, (a, b) => a.replace(".0", "") == b)
          ),
          div(cls := "increment_choice")(
            renderLabel(form("increment"), trans.incrementInSeconds()),
            renderSelect(form("increment"), clockIncrementChoices)
          ),
          div(cls := "byoyomi_choice")(
            renderLabel(form("byoyomi"), trans.byoyomiInSeconds()),
            renderSelect(form("byoyomi"), clockByoyomiChoices)
          ),
          renderRadios(form("periods"), periodsChoices)
        )
      else
        frag(
          div(cls := "time_choice range")(
            trans.minutesPerSide(),
            ": ",
            span(
              // NOTE:: I believe that the byoyomi and fischer calculations here will be
              //        the same.
              //strategygames.ByoyomiClock
              //  .Config(~form("time").value.map(x => (x.toDouble * 60).toInt), 0, 0, 1)
              //  .limitString
              strategygames.Clock
                .Config(~form("time").value.map(x => (x.toDouble * 60).toInt), 0)
                .limitString
            ),
            renderDissociatedRange(form("time"))
          ),
          div(cls := "byoyomi_choice range")(
            trans.byoyomiInSeconds(),
            ": ",
            span(form("byoyomi").value),
            renderDissociatedRange(form("byoyomi"))
          ),
          div(cls := "byoyomi_periods buttons")(
            trans.periods(),
            div(id := "config_periods")(
              renderRadios(form("periods"), periodsChoices)
            )
          ),
          div(cls := "increment_choice range")(
            trans.incrementInSeconds(),
            ": ",
            span(form("increment").value),
            renderDissociatedRange(form("increment"))
          )
        ),
      div(cls := "correspondence")(
        if (ctx.blind)
          div(cls := "days_choice")(
            renderLabel(form("days"), trans.daysPerTurn()),
            renderSelect(form("days"), corresDaysChoices)
          )
        else
          div(cls := "days_choice range")(
            trans.daysPerTurn(),
            ": ",
            span(form("days").value),
            renderDissociatedRange(form("days"))
          )
      )
    )

  val dataRandomPlayerIndexVariants =
    attr("data-random-playerindex-variants") := lila.game.Game.variantsWhereP1IsBetter
      .map(v => s"${v.gameFamily.id}_${v.id}")
      .mkString(",")

  val dataAnon        = attr("data-anon")
  val dataMin         = attr("data-min")
  val dataMax         = attr("data-max")
  val dataValidateUrl = attr("data-validate-url")
  val dataResizable   = attr("data-resizable")
  val dataType        = attr("data-type")
}
