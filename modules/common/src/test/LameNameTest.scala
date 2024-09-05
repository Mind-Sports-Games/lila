package lila.common

import org.specs2.mutable.Specification

class LameNameTest extends Specification {

  def test = LameName.username _

  "disallow" should {
    "separated titles" in {
      test("fm-foo") must beTrue
      test("fm_foo") must beTrue
      test("wgm-foo") must beTrue
      test("wcm_foo") must beTrue
      test("gmFoobar") must beTrue
      test("gm007") must beTrue
      test("GmFoo") must beTrue
      test("nm_brianmatthews") must beTrue
      test("the_nm_brianmatthews") must beTrue
    }
    "uppercase titles" in {
      test("GMfoo") must beTrue
      test("IMfoo") must beTrue
      test("WFMfoo") must beTrue
      test("WIMfoo") must beTrue
      test("1Mfoo") must beTrue
      test("BriaNMatthews") must beTrue
      test("NMBrianMatthews") must beTrue
      test("BrianMatthews_NM") must beTrue
      test("BrianMatthewsNM") must beTrue
      test("TheGMBrianMatthews") must beTrue
    }
    "impersonating us" in {
      test("PlayStrategy") must beTrue
      test("P1ayStrategy") must beTrue
      test("Play-Strategy") must beTrue
      test("P_L_A_Y_S_T_R_A_T_E_G_Y") must beTrue
      test("P1ay-Strategy") must beTrue
    }
    "gross" in {
      test("Shit") must beTrue
      test("Sh1t") must beTrue
      test("douchebag") must beTrue
      test("d0uchebag") must beTrue
      test("urcunt-blah") must beTrue
      test("urcuntblah") must beTrue
      test("fuckster") must beTrue
      test("fuuckster") must beTrue
    }
  }
  "allow" should {
    "good stuff" in {
      test("joey") must beFalse
      test("gmfoo") must beFalse
      test("g-foo") must beFalse
      test("g_foo") must beFalse
      test("g-foo") must beFalse
      test("agm-foo") must beFalse
      test("atf90") must beFalse
      test("a_b") must beFalse
      test("BRIANMATTHEWS") must beFalse
      test("BrianMatthews") must beFalse
      test("BrianMatthewsnm") must beFalse
      test("TheGMBRianMatthews") must beFalse
    }
  }

  def teamTest = LameName.team _
  "disallow" should {
    "impersonating us" in {
      teamTest("PlayStrategy") must beTrue
      teamTest("playstrategy oware") must beTrue
      teamTest("plouf playstrategy oware") must beTrue
      teamTest("plouf_playstrategy_oware") must beTrue
      teamTest("test_mindsportsolympiad_chess") must beTrue
      teamTest("hipbackgammonhubhup") must beTrue
      teamTest("miniadminia warum nicht") must beTrue

      // these below are FALSE
      // teamTest("P1ayStrategy") must beTrue // with a 1 instead of a l
      // teamTest("Play-Strategy") must beTrue
      // teamTest("P_L_A_Y_S_T_R_A_T_E_G_Y") must beTrue
      // teamTest("P1ay-Strategy") must beTrue
    }
    "gross" in {
      teamTest("Play fuck") must beTrue
      teamTest("abifuuuuuckster") must beTrue
    }
  }
  "allow" should {
    "good stuff" in {
      teamTest("champion") must beFalse
      teamTest("oware") must beFalse
      teamTest("chess") must beFalse
      teamTest("paystrategy") must beFalse
    }
  }
}
