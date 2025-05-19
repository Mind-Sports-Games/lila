package views.html.plan

import controllers.routes

import lila.api.Context
import lila.app.templating.Environment._
import lila.app.ui.ScalatagsTemplate._

object indexPayPal {

  import trans.patron._

  private val dataForm = attr("data-form")

  def apply(
      me: lila.user.User,
      patron: lila.plan.Patron,
      subscription: lila.plan.PayPalSubscription
  )(implicit ctx: Context) =
    views.html.base.layout(
      title = thankYou.txt(),
      moreCss = cssTag("plan"),
      moreJs = frag(jsModule("plan"), embedJsUnsafeLoadThen("""payPalStart()"""))
    ) {
      main(cls := "box box-pad plan")(
        h1(
          userLink(me),
          " • ",
          if (patron.isLifetime) strong(lifetimePatron())
          else patronForMonths(me.plan.months)
        ),
        table(cls := "all")(
          tbody(
            tr(
              th(currentStatus()),
              td(
                youSupportWith(strong(subscription.capturedMoney.display)),
                span(cls := "thanks")(tyvm())
              )
            ),
            tr(
              th(nextPayment()),
              td(
                youWillBeChargedXOnY(
                  strong(subscription.capturedMoney.display),
                  showDate(subscription.nextChargeAt)
                ),
                br,
                a(href := s"${routes.Plan.list}?freq=onetime")(makeAdditionalDonation())
              )
            ),
            tr(
              th(update()),
              td(cls := "change") {
                val cancelButton = a(dataForm := "cancel")(cancelSupport())
                frag(
                  cancelButton,
                  postForm(cls := "cancel", action := routes.Plan.cancel)(
                    p(stopPayments()),
                    submitButton(cls := "button button-red")(noLongerSupport()),
                    a(dataForm := "cancel")(trans.cancel())
                  )
                )
              }
            ),
            tr(
              th,
              td(a(href := routes.Plan.list)(viewOthers()))
            )
          )
        )
      )
    }
}
