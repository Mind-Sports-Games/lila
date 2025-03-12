package views
package html.site

import controllers.routes
import lila.api.Context
import lila.app.templating.Environment._
import lila.app.ui.ScalatagsTemplate._

object faq {

  import trans.faq._

  val fideHandbookUrl = "https://handbook.fide.com/chapter/E012018"

  private def question(id: String, title: String, answer: Frag*) =
    div(
      st.id := id,
      cls := "question"
    )(
      h3(a(href := s"#$id")(title)),
      div(cls := "answer")(answer)
    )

  def apply()(implicit ctx: Context) =
    page.layout(
      title = "Frequently Asked Questions",
      active = "faq",
      moreCss = cssTag("faq")
    ) {
      div(cls := "faq small-page box box-pad")(
        h1(cls := "playstrategy_title")(frequentlyAskedQuestions()),
        h2("PlayStrategy"),
        question(
          "what",
          whatIsPlayStrategy.txt(),
          playstrategyAboutSummary(
            a(href := "/about")("PlayStrategy")
          )
        ),
        question(
          "donating",
          canIDonateToPlayStrategy.txt(),
          playstrategyFundedByDonations(
            a(href := "/patron")("patron")
          )
        ),
        question(
          "lichess-difference",
          howAreWeDifferentFromLichess.txt(),
          playstrategySupportsDifferentAbstractGames(
            a(href := "https://lichess.org")("lichess.org"),
            a(href := "https://mindsportsolympiad.com/")("Mind Sports Olympiad")
          )
        ),
        h2(gameplay()),
        question(
          "games",
          whatGamesCanIplay.txt(),
          playstrategyGamesList.txt()
        ),
        question(
          "variants",
          whatVariantsCanIplay.txt(),
          p(
            playstrategySupportChessAnd(
              a(href := routes.Page.variantHome)(variantList())
            )
          )
        ),
        question(
          "time-controls",
          howBulletBlitzEtcDecided.txt(),
          p(
            basedOnGameDuration(strong(durationFormula()))
          ),
          ul(
            li(inferiorThanXsEqualYtimeControl(29, "UltraBullet")),
            li(inferiorThanXsEqualYtimeControl(179, "Bullet")),
            li(inferiorThanXsEqualYtimeControl(479, "Blitz")),
            li(inferiorThanXsEqualYtimeControl(1499, trans.rapid())),
            li(superiorThanXsEqualYtimeControl(1500, trans.classical()))
          )
        ),
        question(
          "correspondence",
          isCorrespondenceDifferent.txt(),
          p(
            youCanUseOpeningBookNoEngine()
          )
        ),
        question(
          "timeout",
          insufficientMaterial.txt(),
          p(
            playstrategyFollowFIDErules(a(href := fideHandbookUrl)(fideHandbookX("§6.9")))
          )
        ),
        question(
          "threefold",
          threefoldRepetition.txt(),
          p(
            threefoldRepetitionExplanation(
              a(href := "https://en.wikipedia.org/wiki/Threefold_repetition")(threefoldRepetitionLowerCase()),
              a(href := fideHandbookUrl)(fideHandbookX("§9.2"))
            )
          ),
          h4(notRepeatedMoves()),
          p(
            repeatedPositionsThatMatters(
              em(positions())
            )
          ),
          h4(weRepeatedthreeTimesPosButNoDraw()),
          p(
            threeFoldHasToBeClaimed(
              a(href := routes.Pref.form("game-behavior"))(configure())
            )
          )
        ),
        question(
          "acpl",
          whatIsACPL.txt(),
          p(
            acplExplanation()
          )
        ),
        h2(fairPlay()),
        question(
          "rating-refund",
          whenAmIEligibleRatinRefund.txt(),
          p(
            ratingRefundExplanation()
          )
        ),
        question(
          "leaving",
          preventLeavingGameWithoutResigning.txt(),
          p(
            leavingGameWithoutResigningExplanation()
          )
        ),
        question(
          "mod-application",
          howCanIBecomeModerator.txt(),
          p(
            youCannotApply()
          )
        ),
        h2(accounts()),
        question(
          "usernames",
          whatUsernameCanIchoose.txt(),
          p(usernamesNotOffensive.txt())
        ),
        question(
          "change-username",
          canIChangeMyUsername.txt(),
          p(usernamesCannotBeChanged.txt())
        ),
        h2(playstrategyRatings()),
        question(
          "ratings",
          whichRatingSystemUsedByPlayStrategy.txt(),
          p(
            ratingSystemUsedByPlayStrategy()
          )
        ),
        question(
          "provisional",
          whatIsProvisionalRating.txt(),
          p(provisionalRatingExplanation()),
          ul(
            li(
              notPlayedEnoughRatedGamesAgainstX(
                em(similarOpponents())
              )
            ),
            li(
              notPlayedRecently()
            )
          ),
          p(
            ratingDeviationMorethanOneHundredTen()
          )
        ),
        question(
          "input-rating",
          whatIsInputRating.txt(),
          p(
            inputRatingExplanation(
              a(href := routes.Page.lonePage("handicaps"))(handicappedTournament())
            )
          )
        ),
        question(
          "leaderboards",
          howDoLeaderoardsWork.txt(),
          p(
            inOrderToAppearsYouMust(
              a(href := routes.User.list)(ratingLeaderboards())
            )
          ),
          ol(
            // //change back when more regular users
            // li(havePlayedMoreThanThirtyGamesInThatRating()),
            // li(havePlayedARatedGameAtLeastOneWeekAgo()),
            // li(
            //   ratingDeviationLowerThanXinChessYinVariants(
            //     lila.rating.Glicko.standardRankableDeviation,
            //     lila.rating.Glicko.variantRankableDeviation
            //   )
            // ),
            // li(beInTopTen())
            li(beInTopTen()),
            li(havePlayedARatedGameAtLeastOneMonthAgo())
          ),
          p(
            secondRequirementToStopOldPlayersTrustingLeaderboards()
          )
        ),
        question(
          "shield-leaderboards",
          howDoesTheSheildLeaderboardWork.txt(),
          p(
            shieldLeaderboardOverview(
              a(href := routes.Tournament.shields)("Shield")
            )
          ),
          ol(
            li(firstPlaceShield()),
            li(secondPlaceShield()),
            li(thirdPlaceShield()),
            li(playedAtLeastOneGameShield())
          ),
          p(
            otherShieldLeaderboardRestrictons()
          )
        ),
        question(
          "high-ratings",
          whyAreRatingHigher.txt(),
          p(
            whyAreRatingHigherExplanation()
          )
        ),
        question(
          "hide-ratings",
          howToHideRatingWhilePlaying.txt(),
          p(
            enableZenMode(
              a(href := routes.Pref.form("game-display"))(displayPreferences()),
              em("z")
            )
          )
        ),
        question(
          "disconnection-loss",
          connexionLostCanIGetMyRatingBack.txt(),
          p(
            weCannotDoThatEvenIfItIsServerSideButThatsRare()
          )
        ),
        h2(howToThreeDots()),
        question(
          "browser-notifications",
          enableDisableNotificationPopUps.txt(),
          p(img(src := staticAssetUrl("images/connection-info.png"), alt := viewSiteInformationPopUp.txt())),
          p(
            playstrategyCanOptionnalySendPopUps()
          )
        )
      )
    }
}
