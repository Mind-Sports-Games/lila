package views.html.setup

import controllers.routes
import play.api.data.Form
import play.api.mvc.Call

import lila.api.Context
import lila.app.templating.Environment._
import lila.app.ui.ScalatagsTemplate._
import lila.user.User
import lila.common.LightUser

import strategygames.GameFamily

object forms {

  import bits._

  // def hook(form: Form[_])(implicit ctx: Context) =
  //   layout(
  //     "hook",
  //     trans.createAGame(),
  //     routes.Setup.hook("sri-placeholder")
  //   ) {
  //     frag(
  //       renderVariant(form, translatedVariantChoicesWithVariants),
  //       renderTimeMode(form, allowAnon = false, allowCorrespondence = true),
  //       ctx.isAuth option frag(
  //         div(cls := "mode_choice buttons")(
  //           renderRadios(form("mode"), translatedModeChoices)
  //         ),
  //         ctx.noBlind option div(cls := "optional_config")(
  //           div(cls := "rating-range-config")(
  //             trans.ratingRange(),
  //             div(cls := "rating-range") {
  //               val field = form("ratingRange")
  //               frag(
  //                 renderInput(field),
  //                 input(
  //                   name := s"${field.name}_range_min",
  //                   tpe := "range",
  //                   cls := "range rating-range__min"
  //                 ),
  //                 span(cls := "rating-min"),
  //                 "/",
  //                 span(cls := "rating-max"),
  //                 input(
  //                   name := s"${field.name}_range_max",
  //                   tpe := "range",
  //                   cls := "range rating-range__max"
  //                 )
  //               )
  //             }
  //           )
  //         )
  //       )
  //     )
  //   }

  // def friend(
  //     form: Form[_],
  //     user: Option[User],
  //     error: Option[String],
  //     validFen: Option[lila.setup.ValidFen]
  // )(implicit ctx: Context) =
  //   layout(
  //     "friend",
  //     (if (user.isDefined) trans.challenge.challengeToPlay else trans.playWithAFriend)(),
  //     routes.Setup.friend(user map (_.id)),
  //     error.map(e => raw(e.replace("{{user}}", userIdLink(user.map(_.id)).toString)))
  //   )(
  //     frag(
  //       user.map { u =>
  //         userLink(u, cssClass = "target".some)
  //       },
  //       renderVariant(form, translatedVariantChoicesForUser(user)),
  //       fenInput(form("fen"), strict = false, validFen),
  //       renderGoOptions(form),
  //       renderBackgammonOptions(form),
  //       renderTimeMode(form, allowAnon = true, allowCorrespondence = user.fold(true)(u => !u.isBot)),
  //       renderMultiMatch(form),
  //       ctx.isAuth option div(cls := "mode_choice buttons")(
  //         renderRadios(form("mode"), translatedModeChoices)
  //       ),
  //       blindSideChoice(form)
  //     )
  //   )

  def game(
      form: Form[_],
      user: Option[User],
      error: Option[String],
      validFen: Option[lila.setup.ValidFen]
  )(implicit ctx: Context) =
    layout(
      "game",
      (if (user.isDefined) trans.challenge.challengeToPlay else trans.createAGame)(),
      routes.Setup.game("sri-placeholder", user map (_.id)),
      error.map(e => raw(e.replace("{{user}}", userIdLink(user.map(_.id)).toString)))
    ) {
      frag(
        renderGameFamilyOptions(form, translatedGameFamilyIconChoices),
        renderVariantOptions(form, translatedVariantIconChoices),
        fenInput(form("fen"), strict = false, validFen),
        renderGoOptions(form),
        renderBackgammonOptions(form),
        renderTimeModeOptions(form),
        renderTimeMode(form, allowAnon = false, allowCorrespondence = true),
        renderMultiMatch(form),
        renderPlayerIndexOptions(form("playerIndex")),
        ctx.isAuth option frag(
          div(cls := "mode_choice buttons collapsible optional_config")(
            div(cls := "section_title")("Mode"),
            renderIconRadios(form("mode"), translatedModeIconChoices),
            renderSelectedChoice(form("mode"), translatedModeIconChoices)
          )
          // ctx.noBlind option div(cls := "optional_config")(
          //   div(cls := "rating-range-config")(
          //     trans.ratingRange(),
          //     div(cls := "rating-range") {
          //       val field = form("ratingRange")
          //       frag(
          //         renderInput(field),
          //         input(
          //           name := s"${field.name}_range_min",
          //           tpe := "range",
          //           cls := "range rating-range__min"
          //         ),
          //         span(cls := "rating-min"),
          //         "/",
          //         span(cls := "rating-max"),
          //         input(
          //           name := s"${field.name}_range_max",
          //           tpe := "range",
          //           cls := "range rating-range__max"
          //         )
          //       )
          //     }
          //   )
          // )
        ),
        renderOpponentOptions(
          form,
          user.map { u =>
            userLink(u, cssClass = "target".some)
          }
        ),
        blindSideChoice(form)
      )
    }

  private def translatedVariantChoicesForUser(user: Option[User])(implicit cts: Context) = {
    user match {
      case Some(u) if LightUser.stockfishBotsIDs.contains(u.id) => translatedAiVariantChoices
      case Some(u) if u.id == "ps-greedy-four-move"             => translatedGreedyFourMoveChoices
      case _                                                    => translatedVariantChoicesWithVariantsAndFen
    }
  }

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
            submitButton("Create the game")
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
