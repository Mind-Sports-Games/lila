package views.html.site

import lila.api.Context
import lila.app.templating.Environment._
import lila.app.ui.ScalatagsTemplate._

object lag {

  import trans.lag._

  def apply()(implicit ctx: Context) =
    page.layout(
      title = "Is PlayStrategy lagging?",
      active = "lag",
      moreCss = cssTag("lag"),
      moreJs = frag(
        highchartsLatestTag,
        highchartsMoreTag,
        jsTag("lag.js")
      )
    ) {
      div(cls := "box box-pad lag")(
        h1(
          isPlayStrategyLagging(),
          span(cls := "answer short")(
            span(cls := "waiting")(measurementInProgressThreeDot()),
            span(cls := "nope-nope none")(noAndYourNetworkIsGood()),
            span(cls := "nope-yep none")(noAndYourNetworkIsBad()),
            span(cls := "yep none")(yesItWillBeFixedSoon())
          )
        ),
        div(cls := "answer long")(
          andNowTheLongAnswerLagComposedOfTwoValues()
        ),
        div(cls := "sections")(
          st.section(cls := "server")(
            h2(playstrategyServerLatency()),
            div(cls := "meter"),
            p(
              playstrategyServerLatencyExplanation()
            )
          ),
          st.section(cls := "network")(
            h2(networkBetweenPlayStrategyAndYou()),
            div(cls := "meter"),
            p(
              networkBetweenPlayStrategyAndYouExplanation()
            )
          )
        ),
        div(cls := "last-word")(
          p(youCanFindTheseValuesAtAnyTimeByClickingOnYourUsername()),
          h2(lagCompensation()),
          p(
            lagCompensationExplanation()
          )
        )
      )
    }
}
