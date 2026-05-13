package lila.blog

import org.joda.time.DateTime

case class MiniPost(
    id: String,
    slug: String,
    title: String,
    shortlede: String,
    date: DateTime,
    image: String
)

object MiniPost {

  def urlencode(str: String): String =
    java.net.URLEncoder.encode(str, "US-ASCII")

  def fromDocument(coll: String, imgSize: String = "icon")(doc: io.prismic.Document): Option[MiniPost] = {
    for {
      title <- doc.getText(s"$coll.title")
      shortlede = ~doc.getText(s"$coll.shortlede")
      date <- doc
        .getDate(s"$coll.date")
        .map(d =>
          new org.joda.time.DateTime(d.value.atStartOfDay(java.time.ZoneOffset.UTC).toInstant.toEpochMilli)
        )
      image <- doc.getImage(s"$coll.image", imgSize).map(_.url)
    } yield MiniPost(
      doc.id,
      urlencode(doc.getText("blog.title").getOrElse("-").toLowerCase().replace(" ", "-")),
      title,
      shortlede,
      date,
      image
    )
  }
}
