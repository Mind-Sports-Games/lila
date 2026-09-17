package views.html.study

import play.api.libs.json.{ JsObject, Json }

import lila.api.Context
import lila.app.templating.Environment.*
import lila.app.ui.ScalatagsTemplate.*
import lila.common.String.html.safeJsonValue

object show {

  def apply(
      s: lila.study.Study,
      data: lila.study.JsonView.JsData,
      chatOption: Option[lila.chat.UserChat.Mine],
      socketVersion: lila.socket.Socket.SocketVersion,
      streamers: List[lila.user.User.ID]
  )(implicit ctx: Context) =
    views.html.base.layout(
      title = s.name.value,
      moreCss = cssTag("analyse.study"),
      moreJs = frag(
        analyseTag,
        analyseNvuiTag,
        embedJsUnsafe(s"""playstrategy.study=${safeJsonValue(
            Json.obj(
              "study"    -> data.study.add("admin" -> isGranted(_.StudyAdmin)),
              "data"     -> data.analysis,
              "i18n"     -> jsI18n(),
              "tagTypes" -> lila.study.PgnTags.typesToString,
              "userId"   -> ctx.userId,
              "chat"     -> chatOption.map { c =>
                views.html.chat.json(
                  c.chat,
                  name = trans.chatRoom.txt(),
                  timeout = c.timeout,
                  writeable = ctx.userId exists s.canChat,
                  public = false,
                  resourceId = lila.chat.Chat.ResourceId(s"study/${c.chat.id}"),
                  palantir = ctx.userId exists s.isMember,
                  localMod = ctx.userId exists s.canContribute
                )
              },
              "explorer" -> Json.obj(
                "endpoint"          -> explorerEndpoint,
                "tablebaseEndpoint" -> tablebaseEndpoint
              ),
              "socketUrl"     -> socketUrl(s.id.value),
              "socketVersion" -> socketVersion.value
            )
          )}""")
      ),
      robots = s.isPublic,
      chessground = false,
      zoomable = true,
      csp = defaultCsp.withWebAssembly.withPeer.some,
      openGraph = lila.app.ui
        .OpenGraph(
          title = s.name.value,
          url = s"$netBaseUrl${routes.Study.show(s.id.value).url}",
          description = description(s, data)
        )
        .some
    )(
      frag(
        main(cls := "analyse"),
        bits.streamers(streamers)
      )
    )

  def socketUrl(id: String) = s"/study/$id/socket/v$apiVersion"

  // chapters are client-rendered, so the description is the only place their names reach crawlers
  private def description(s: lila.study.Study, data: lila.study.JsonView.JsData) = {
    val chapters = (data.study \ "chapters")
      .asOpt[List[JsObject]]
      .so(_.flatMap(c => (c \ "name").asOpt[String]))
    val intro = s.description
      .map(_.replaceAll("\\s+", " ").trim)
      .filter(_.nonEmpty)
      .getOrElse(s"A study by ${usernameOrId(s.ownerId)} on PlayStrategy")
    val withChapters =
      if (chapters.sizeIs > 1) s"$intro. ${chapters.size} chapters: ${chapters.take(6).mkString(", ")}"
      else intro
    lila.common.String.shorten(withChapters, 250, "…")
  }
}
