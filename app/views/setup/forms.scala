package views.html.setup

import controllers.routes
import play.api.data.Form
import play.api.mvc.Call

import lila.api.Context
import lila.app.templating.Environment._
import lila.app.ui.ScalatagsTemplate._
import lila.user.User

import strategygames.GameFamily

object forms {

  import bits._

  def hook(form: Form[_])(implicit ctx: Context) =
    layout(
      "hook",
      trans.createAGame(),
      routes.Setup.hook("sri-placeholder")
    ) {
      frag(
        renderVariant(form, translatedVariantChoicesWithVariants),
        renderTimeMode(form, allowAnon = false),
        ctx.isAuth option frag(
          div(cls := "mode_choice buttons")(
            renderRadios(form("mode"), translatedModeChoices)
          ),
          ctx.noBlind option div(cls := "optional_config")(
            div(cls := "rating-range-config")(
              trans.ratingRange(),
              div(cls := "rating-range") {
                val field = form("ratingRange")
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
          )
        )
      )
    }

  def ai(form: Form[_], ratings: Map[Int, Int], validFen: Option[lila.setup.ValidFen])(implicit
      ctx: Context
  ) =
    layout("ai", trans.playWithTheMachine(), routes.Setup.ai) {
      frag(
        div(cls := "botInfo")(
          a(href := routes.PlayApi.botOnline)(trans.seeOurListOfBots()),
          br,
          span(s"- ${trans.orUpper.txt()} -"),
          br,
          span(trans.playAgainstX("Stockfish"))
        ),
        renderVariant(form, translatedAiVariantChoices),
        fenInput(form("fen"), strict = true, validFen),
        renderTimeMode(form, allowAnon = true),
        if (ctx.blind)
          frag(
            renderLabel(form("level"), trans.strength()),
            renderSelect(form("level"), lila.setup.AiConfig.levelChoices),
            blindSideChoice(form)
          )
        else
          frag(
            br,
            trans.strength(),
            div(cls := "level buttons")(
              div(id := "config_level")(
                renderRadios(form("level"), lila.setup.AiConfig.levelChoices)
              ),
              div(cls := "ai_info")(
                ratings.toList.map { case (level, _) =>
                  div(cls := s"${prefix}level_$level")(trans.aiNameLevelAiLevel("Stockfish 13", level))
                }
              )
            )
          )
      )
    }

  def friend(
      form: Form[_],
      user: Option[User],
      error: Option[String],
      validFen: Option[lila.setup.ValidFen]
  )(implicit ctx: Context) =
    layout(
      "friend",
      (if (user.isDefined) trans.challenge.challengeToPlay else trans.playWithAFriend)(),
      routes.Setup.friend(user map (_.id)),
      error.map(e => raw(e.replace("{{user}}", userIdLink(user.map(_.id)).toString)))
    )(
      frag(
        user.map { u =>
          userLink(u, cssClass = "target".some)
        },
        renderVariant(form, translatedVariantChoicesWithVariantsAndFen),
        fenInput(form("fen"), strict = false, validFen),
        renderGoOptions(form),
        renderTimeMode(form, allowAnon = true),
        renderMultiMatch(form),
        ctx.isAuth option div(cls := "mode_choice buttons")(
          renderRadios(form("mode"), translatedModeChoices)
        ),
        blindSideChoice(form)
      )
    )

  private def blindSideChoice(form: Form[_])(implicit ctx: Context) =
    ctx.blind option frag(
      renderLabel(form("playerIndex"), trans.side()),
      renderSelect(form("playerIndex").copy(value = "random".some), translatedSideChoices)
    )

  private def layout(
      typ: String,
      titleF: Frag,
      route: Call,
      error: Option[Frag] = None
  )(fields: Frag)(implicit ctx: Context) =
    div(cls := error.isDefined option "error")(
      h2(titleF),
      error
        .map { e =>
          frag(
            p(cls := "error")(e),
            br,
            a(href := routes.Lobby.home, cls := "button text", dataIcon := "L")(trans.cancel.txt())
          )
        }
        .getOrElse {
          postForm(
            action := route,
            novalidate,
            dataRandomPlayerIndexVariants,
            dataType := typ,
            dataAnon := ctx.isAnon.option("1")
          )(
            fields,
            if (ctx.blind) submitButton("Create the game")
            else
              div(cls := "playerIndex-submits")(
                translatedSideChoices.map { case (key, name, _) =>
                  submitButton(
                    (typ == "hook") option disabled,
                    title := name,
                    cls := s"playerIndex-submits__button button button-metal $key",
                    st.name := "playerIndex",
                    value := key
                  )(i)
                }
              )
          )
        },
      ctx.me.ifFalse(ctx.blind).map { me =>
        div(cls := "ratings")(
          form3.hidden("rating", "?"),
          lila.rating.PerfType.nonPuzzle.map { perfType =>
            div(cls := perfType.key)(
              trans.perfRatingX(
                raw(s"""<strong data-icon="${perfType.iconChar}">${me
                  .perfs(perfType.key)
                  .map(_.intRating)
                  .getOrElse("?")}</strong> ${perfType.trans}""")
              )
            )
          }
        )
      }
    )
}
