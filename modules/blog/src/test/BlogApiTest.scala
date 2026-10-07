package lila.blog

class BlogApiTest extends munit.FunSuite {

  test("validDocumentId - real prismic id") {
    assert(BlogApi.validDocumentId("aoa4AhEAACsAikMz"))
    assert(BlogApi.validDocumentId("Ab_c-9"))
  }

  test("validDocumentId - rejects prismic predicate injection") {
    assert(!BlogApi.validDocumentId("""aoa4AhEAACsAikMz")][:d = at(document.id, "aoa4AhEAACsAikMz"""))
    assert(
      !BlogApi.validDocumentId(
        """aoa4AhEAACsAikMz%")) OR EXTRACTVALUE(6042,CONCAT(0x7e,((SELECT (ELT(6042=6042,1)))),0x7e))-- -"""
      )
    )
  }

  test("validDocumentId - rejects empty and overlong ids") {
    assert(!BlogApi.validDocumentId(""))
    assert(!BlogApi.validDocumentId("a" * 33))
  }
}
