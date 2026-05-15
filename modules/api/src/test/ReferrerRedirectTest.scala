package lila.api

import lila.common.config.BaseUrl

class ReferrerRedirectTest extends munit.FunSuite {

  def r = new ReferrerRedirect(BaseUrl("https://playstrategy.org"))

  test("be valid") {
    assert(r.valid("/tournament"))
    assert(r.valid("/@/neio"))
    assert(r.valid("/@/Neio"))
    assert(r.valid("//playstrategy.org"))
    assert(r.valid("//foo.playstrategy.org"))
    assert(r.valid("https://playstrategy.org/tournament"))
    assert(r.valid("https://playstrategy.org/?a_a=b-b&C[]=#hash"))
    assert(
      r.valid(
        "https://oauth.playstrategy.org/oauth/authorize?response_type=code&client_id=NotReal1&redirect_uri=http%3A%2F%2Fexample.playstrategy.ovh%3A9371%2Foauth-callback&scope=challenge:read+challenge:write+board:play&state=123abc"
      )
    )
  }

  test("be invalid") {
    assert(!r.valid(""))
    assert(!r.valid("ftp://playstrategy.org/tournament"))
    assert(!r.valid("https://evil.com"))
    assert(!r.valid("https://evil.com/foo"))
    assert(!r.valid("//evil.com"))
    assert(!r.valid("//playstrategy.org.evil.com"))
    assert(!r.valid("/\t/evil.com"))
    assert(!r.valid("/ /evil.com"))
  }
}
