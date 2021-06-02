package lila.api

import org.specs2.mutable.Specification

import lila.common.config.BaseUrl

class ReferrerRedirectTest extends Specification {

  def r = new ReferrerRedirect(BaseUrl("https://playstrategy.org"))

  "referrer" should {
    "be valid" in {
      r.valid("/tournament") must beTrue
      r.valid("/@/neio") must beTrue
      r.valid("/@/Neio") must beTrue
      r.valid("//playstrategy.org") must beTrue
      r.valid("//foo.playstrategy.org") must beTrue
      r.valid("https://playstrategy.org/tournament") must beTrue
      r.valid("https://playstrategy.org/?a_a=b-b&C[]=#hash") must beTrue
      r.valid(
        "https://oauth.playstrategy.org/oauth/authorize?response_type=code&client_id=NotReal1&redirect_uri=http%3A%2F%2Fexample.playstrategy.ovh%3A9371%2Foauth-callback&scope=challenge:read+challenge:write+board:play&state=123abc"
      ) must beTrue
    }
    "be invalid" in {
      r.valid("") must beFalse
      r.valid("ftp://playstrategy.org/tournament") must beFalse
      r.valid("https://evil.com") must beFalse
      r.valid("https://evil.com/foo") must beFalse
      r.valid("//evil.com") must beFalse
      r.valid("//playstrategy.org.evil.com") must beFalse
      r.valid("/\t/evil.com") must beFalse
      r.valid("/ /evil.com") must beFalse
    }
  }
}
