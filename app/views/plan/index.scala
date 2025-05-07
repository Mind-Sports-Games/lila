package views.html.plan

import play.api.i18n.Lang

import lila.api.Context
import lila.app.templating.Environment._
import lila.app.ui.ScalatagsTemplate._

import controllers.routes

object index {

  import trans.patron._

  private[plan] val stripeScript = script(src := "https://js.stripe.com/v3/")

  private val namespaceAttr = attr("data-namespace")

  def apply(
      email: Option[lila.common.EmailAddress],
      stripePublicKey: String,
      payPalPublicKey: String,
      patron: Option[lila.plan.Patron],
      recentIds: List[String],
      bestIds: List[String]
  )(implicit ctx: Context) = {

    views.html.base.layout(
      title = becomePatron.txt(),
      moreCss = cssTag("plan"),
      moreJs = ctx.isAuth option
        frag(
          stripeScript,
          frag(
            script(
              src := s"https://www.paypal.com/sdk/js?client-id=${payPalPublicKey}&currency=USD&locale=${ctx.lang.locale}",
              namespaceAttr := "paypalOrder"
            ),
            script(
              src := s"https://www.paypal.com/sdk/js?client-id=${payPalPublicKey}&vault=true&intent=subscription&currency=USD&locale=${ctx.lang.locale}",
              namespaceAttr := "paypalSubscription"
            )
          ),
          jsModule("checkout"),
          embedJsUnsafeLoadThen(s"""CheckoutStart("$stripePublicKey")""")
        ),
      openGraph = lila.app.ui
        .OpenGraph(
          title = becomePatron.txt(),
          url = s"$netBaseUrl${routes.Plan.index.url}",
          description = freeChess.txt()
        )
        .some,
      csp = defaultCsp.withStripe.withPayPal.some
    ) {
      main(cls := "page-menu plan")(
        st.aside(cls := "page-menu__menu recent-patrons")(
          h2(newPatrons()),
          div(cls := "list")(
            recentIds.map { userId =>
              div(userIdLink(userId.some))
            }
          )
        ),
        div(cls := "page-menu__content box")(
          patron.ifTrue(ctx.me.??(_.isPatron)).map { p =>
            div(cls := "banner one_time_active")(
              iconTag(patronIconChar),
              div(
                h1(thankYou()),
                if (p.isLifetime) youHaveLifetime()
                else
                  p.expiresAt.map { expires =>
                    frag(
                      patronUntil(showDate(expires)),
                      br,
                      ifNotRenewed()
                    )
                  }
              ),
              iconTag(patronIconChar)
            )
          } getOrElse div(cls := "banner moto")(
            iconTag(patronIconChar),
            div(
              h1(freeGames()),
              p(noAdsNoSubs())
            ),
            iconTag(patronIconChar)
          ),
          div(cls := "box__pad")(
            div(cls := "wrapper")(
              div(cls := "text")(
                p(weAreFree()),
                p(weRelyOnSupport())
              ),
              div(cls := "content")(
                div(
                  cls := "plan_checkout",
                  attr("data-email") := email.??(_.value),
                  attr("data-lifetime-usd") := lila.plan.Cents.lifetime.usd.toString,
                  attr("data-lifetime-cents") := lila.plan.Cents.lifetime.value
                )(
//                   raw(s"""
// <form class="paypal_checkout onetime none" action="https://www.sandbox.paypal.com/cgi-bin/webscr" method="post" target="_top">
// ${payPalFormSingle(pricing, "playstrategy.dev one-time")}
// </form>
// <form class="paypal_checkout monthly none" action="https://www.sandbox.paypal.com/cgi-bin/webscr" method="post" target="_top">
// ${payPalFormRecurring(pricing, "playstrategy.dev monthly")}
// </form>
// <form class="paypal_checkout lifetime none" action="https://www.sandbox.paypal.com/cgi-bin/webscr" method="post" target="_top">
// ${payPalFormSingle(pricing, "playstrategy.dev lifetime")}
// </form>"""),
                  ctx.me map { me =>
                    p(style := "text-align:center;margin-bottom:1em")(
                      if (patron.exists(_.isLifetime))
                        makeExtraDonation()
                      else
                        frag(
                          "Donating ",
                          strong("publicly"),
                          " as ",
                          userSpan(me)
                        )
                    )
                  },
                  st.group(cls := "radio buttons freq")(
                    div(
                      st.title := payLifetimeOnce.txt(lila.plan.Cents.lifetime.usd),
                      cls := List("lifetime-check" -> patron.exists(_.isLifetime)),
                      input(
                        tpe := "radio",
                        name := "freq",
                        id := "freq_lifetime",
                        patron.exists(_.isLifetime) option disabled,
                        value := "lifetime"
                      ),
                      label(`for` := "freq_lifetime")(lifetime())
                    ),
                    div(
                      st.title := recurringBilling.txt(),
                      input(
                        tpe := "radio",
                        name := "freq",
                        id := "freq_monthly",
                        checked,
                        value := "monthly"
                      ),
                      label(`for` := "freq_monthly")(monthly())
                    ),
                    div(
                      st.title := singleDonation.txt(),
                      input(
                        tpe := "radio",
                        name := "freq",
                        id := "freq_onetime",
                        checked,
                        value := "onetime"
                      ),
                      label(`for` := "freq_onetime")(onetime())
                    )
                  ),
                  div(cls := "amount_choice")(
                    st.group(cls := "radio buttons amount")(
                      lila.plan.StripePrice.defaultAmounts.map { cents =>
                        val id = s"plan_${cents.value}"
                        div(
                          input(
                            tpe := "radio",
                            name := "plan",
                            st.id := id,
                            cents.usd.value == 10 option checked,
                            value := cents.value,
                            attr("data-usd") := cents.usd.toString,
                            attr("data-amount") := cents.value
                          ),
                          label(`for` := id)(cents.usd.toString)
                        )
                      },
                      div(cls := "other")(
                        input(tpe := "radio", name := "plan", id := "plan_other", value := "other"),
                        label(
                          `for` := "plan_other",
                          title := pleaseEnterAmount.txt(),
                          attr("data-trans-other") := otherAmount.txt()
                        )(otherAmount())
                      )
                    )
                  ),
                  div(cls := "amount_fixed none")(
                    st.group(cls := "radio buttons amount")(
                      div {
                        val cents = lila.plan.Cents.lifetime
                        label(`for` := s"plan_${cents.value}")(cents.usd.toString)
                      }
                    )
                  ),
                  div(cls := "service")(
                    if (ctx.isAuth)
                      button(cls := "stripe button")(withCreditCard())
                    else
                      a(
                        cls := "stripe button",
                        href := s"${routes.Auth.login}?referrer=${routes.Plan.index}"
                      )(withCreditCard()),
                    // button(cls := "paypal button")(withPaypal())
                    (payPalPublicKey != "") option frag(
                      div(cls := "paypal paypal--order"),
                      div(cls := "paypal paypal--subscription"),
                      button(cls := "paypal button disabled paypal--disabled")("PAYPAL")
                    )
                  )
                )
              )
            ),
            p(id := "error")(),
            p(cls := "small_team")(weAreSmallTeam()),
            faq,
            p(cls := "watkins_address")(watkinsAddress()),
            div(cls := "best_patrons")(
              h2(celebratedPatrons()),
              div(cls := "list")(
                bestIds.map { userId =>
                  div(userIdLink(userId.some))
                }
              )
            )
          )
        )
      )
    }
  }

//   private def payPalFormSingle(pricing: lila.plan.PlanPricing, itemName: String)(implicit ctx: Context) = s"""
//   ${payPalForm(pricing, itemName)}
//   <input type="hidden" name="cmd" value="_xclick">
//   <input type="hidden" name="amount" class="amount" value="">
//   <input type="hidden" name="button_subtype" value="services">
// """

//   private def payPalFormRecurring(pricing: lila.plan.PlanPricing, itemName: String)(implicit ctx: Context) =
//     s"""
//   ${payPalForm(pricing, itemName)}
//   <input type="hidden" name="cmd" value="_xclick-subscriptions">
//   <input type="hidden" name="a3" class="amount" value="">
//   <input type="hidden" name="p3" value="1">
//   <input type="hidden" name="t3" value="M">
//   <input type="hidden" name="src" value="1">
// """

//   private def payPalForm(pricing: lila.plan.PlanPricing, itemName: String)(implicit ctx: Context) = s"""
//   <input type="hidden" name="item_name" value="$itemName">
//   <input type="hidden" name="custom" value="${~ctx.userId}">
//   <input type="hidden" name="business" value="UEWHVQ2F7SQNC">
//   <input type="hidden" name="no_note" value="1">
//   <input type="hidden" name="no_shipping" value="1">
//   <input type="hidden" name="rm" value="1">
//   <input type="hidden" name="return" value="https://playstrategy.dev/patron/thanks">
//   <input type="hidden" name="cancel_return" value="https://playstrategy.dev/patron">
//   <input type="hidden" name="lc" value="US">
//   <input type="hidden" name="currency_code" value="USD">
// """

  private def faq(implicit lang: Lang) =
    div(cls := "faq")(
      dl(
        dt(whereMoneyGoes()),
        dd(serversAndDeveloper())
      ),
      dl(
        dt(changeMonthlySupport()),
        dd(
          changeOrContact(a(href := routes.Main.contact, targetBlank)(contactSupport()))
        )
      ),
      dl(
        dt(patronFeatures()),
        dd(
          patronPerks(),
          br,
          a(href := routes.Plan.features, targetBlank)(featuresComparison()),
          "."
        )
      )
    )
}
