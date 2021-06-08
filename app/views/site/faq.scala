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
        h2("Playstrategy"),
        question(
          "name",
          whyIsPlaystrategyCalledPlaystrategy.txt(),
          p(
            playstrategyCombinationLiveLightLibrePronounced(em(leechess())),
            " ",
            a(href := "https://www.youtube.com/watch?v=KRpPqcrdE-o")(hearItPronouncedBySpecialist())
          ),
          p(
            whyLiveLightLibre()
          ),
          p(
            whyIsLilaCalledLila(
              a(href := "https://github.com/ornicar/lila")("lila"),
              a(href := "https://www.scala-lang.org/")("Scala")
            )
          )
        ),
        question(
          "contributing",
          howCanIContributeToPlaystrategy.txt(),
          p(playstrategyPoweredByDonationsAndVolunteers()),
          p(
            findMoreAndSeeHowHelp(
              a(href := routes.Plan.index)(beingAPatron()),
              a(href := routes.Main.costs)(breakdownOfOurCosts()),
              a(href := routes.Page.help)(otherWaysToHelp())
            )
          )
        ),
        question(
          "sites_based_on_Playstrategy",
          areThereWebsitesBasedOnPlaystrategy.txt(),
          p(
            yesPlaystrategyInspiredOtherOpenSourceWebsites(
              a(href := "/source")(trans.sourceCode()),
              a(href := "/api")("API"),
              a(href := "https://database.playstrategy.org")(trans.database())
            )
          ),
          ul(
            li(a(href := "https://blitztactics.com/about")("Blitz Tactics")),
            li(a(href := "https://tailuge.github.io/chess-o-tron/html/blunder-bomb.html")("Blunder Bomb")),
            li(a(href := "https://lidraughts.org")("lidraughts.org"))
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
        question(
          "correspondence",
          isCorrespondenceDifferent.txt(),
          p(
            youCanUseOpeningBookNoEngine()
          )
        ),
        h2(gameplay()),
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
          "variants",
          whatVariantsCanIplay.txt(),
          p(
            playstrategySupportChessAnd(
              a(href := routes.Page.variantHome)(eightVariants())
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
        question(
          "timeout",
          insufficientMaterial.txt(),
          p(
            playstrategyFollowFIDErules(a(href := fideHandbookUrl)(fideHandbookX("§6.9")))
          )
        ),
        question(
          "en-passant",
          discoveringEnPassant.txt(),
          p(
            explainingEnPassant(
              a(href := "https://en.wikipedia.org/wiki/En_passant")(goodIntroduction()),
              a(href := fideHandbookUrl)(fideHandbookX("§3.7")),
              a(href := s"${routes.Learn.index}#/15")(playstrategyTraining())
            )
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
        h2(accounts()),
        question(
          "titles",
          titlesAvailableOnPlaystrategy.txt(),
          p(
            playstrategyRecognizeAllOTBtitles(
              a(href := "https://github.com/ornicar/lila/wiki/Handling-title-verification-requests")(
                asWellAsManyNMtitles()
              )
            )
          ),
          ul(
            li("Grandmaster (GM)"),
            li("International Master (IM)"),
            li("FIDE Master (FM)"),
            li("Candidate Master (CM)"),
            li("Woman Grandmaster (WGM)"),
            li("Woman International Master (WIM)"),
            li("Woman FIDE Master (WFM)"),
            li("Woman Candidate Master (WCM)")
          ),
          p(
            showYourTitle(
              a(href := routes.Main.verifyTitle)(verificationForm()),
              a(href := "#lm")("Playstrategy master (LM)")
            )
          )
        ),
        question(
          "lm",
          canIbecomeLM.txt(),
          p(strong(noUpperCaseDot())),
          p(lMtitleComesToYouDoNotRequestIt())
        ),
        question(
          "usernames",
          whatUsernameCanIchoose.txt(),
          p(
            usernamesNotOffensive(
              a(href := "https://github.com/ornicar/lila/wiki/Username-policy")(guidelines())
            )
          )
        ),
        question(
          "change-username",
          canIChangeMyUsername.txt(),
          p(usernamesCannotBeChanged.txt())
        ),
        question(
          "trophies",
          uniqueTrophies.txt(),
          h4("The way of Berserk"),
          p(
            ownerUniqueTrophies(
              a(href := "https://playstrategy.org/@/hiimgosu")("hiimgosu")
            )
          ),
          p(
            wayOfBerserkExplanation(
              a(href := "https://playstrategy.org/tournament/cDyjj1nL")(aHourlyBulletTournament())
            )
          ),
          h4("The Golden Zee"),
          p(
            ownerUniqueTrophies(
              a(href := "https://playstrategy.org/@/ZugAddict")("ZugAddict")
            )
          ),
          p(
            goldenZeeExplanation()
          )
        ),
        h2(playstrategyRatings()),
        question(
          "ratings",
          whichRatingSystemUsedByPlaystrategy.txt(),
          p(
            ratingSystemUsedByPlaystrategy()
          ),
          p(
            a(href := routes.Page.loneBookmark("rating-systems"))("More about rating systems")
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
          "leaderboards",
          howDoLeaderoardsWork.txt(),
          p(
            inOrderToAppearsYouMust(
              a(href := routes.User.list)(ratingLeaderboards())
            )
          ),
          ol(
            li(havePlayedMoreThanThirtyGamesInThatRating()),
            li(havePlayedARatedGameAtLeastOneWeekAgo()),
            li(
              ratingDeviationLowerThanXinChessYinVariants(
                lila.rating.Glicko.standardRankableDeviation,
                lila.rating.Glicko.variantRankableDeviation
              )
            ),
            li(beInTopTen())
          ),
          p(
            secondRequirementToStopOldPlayersTrustingLeaderboards()
          )
        ),
        question(
          "high-ratings",
          whyAreRatingHigher.txt(),
          p(
            whyAreRatingHigherExplanation()
          ),
          p(
            a(href := routes.Page.loneBookmark("rating-systems"))("More about rating systems")
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
          p(img(src := assetUrl("images/connection-info.png"), alt := viewSiteInformationPopUp.txt())),
          p(
            playstrategyCanOptionnalySendPopUps()
          )
        )
      )
    }
}
