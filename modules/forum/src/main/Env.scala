package lila.forum

import com.softwaremill.macwire._
import lila.common.autoconfig.{ AutoConfig, ConfigName }
import play.api.Configuration

import lila.common.config._
import lila.hub.actorApi.team.CreateTeam
import lila.mod.ModlogApi
import lila.notify.NotifyApi
import lila.relation.RelationApi
import play.api.libs.ws.StandaloneWSClient

@Module
final private class ForumConfig(
    @ConfigName("topic.max_per_page") val topicMaxPerPage: MaxPerPage,
    @ConfigName("post.max_per_page") val postMaxPerPage: MaxPerPage
)

@Module
final class Env(
    appConfig: Configuration,
    db: lila.db.Db,
    modLog: ModlogApi,
    spam: lila.security.Spam,
    promotion: lila.security.PromotionApi,
    captcher: lila.hub.actors.Captcher,
    timeline: lila.hub.actors.Timeline,
    shutup: lila.hub.actors.Shutup,
    forumSearch: lila.hub.actors.ForumSearch,
    notifyApi: NotifyApi,
    relationApi: RelationApi,
    userRepo: lila.user.UserRepo,
    cacheApi: lila.memo.CacheApi,
    ws: StandaloneWSClient
)(implicit ec: scala.concurrent.ExecutionContext) {

  private val config = appConfig.get[ForumConfig]("forum")(using AutoConfig.loader)

  lazy val categRepo = new CategRepo(db(CollName("f_categ")))
  lazy val topicRepo = new TopicRepo(db(CollName("f_topic")))
  lazy val postRepo  = new PostRepo(db(CollName("f_post")))

  private lazy val detectLanguage =
    new DetectLanguage(ws, appConfig.get[DetectLanguage.Config]("detectlanguage.api"))

  private val env: Env = this

  lazy val categApi: CategApi = wire[CategApi]

  lazy val topicApi: TopicApi =
    new TopicApi(env, forumSearch, config.topicMaxPerPage, modLog, spam, promotion, timeline, shutup, detectLanguage)

  lazy val postApi: PostApi =
    new PostApi(env, forumSearch, config.postMaxPerPage, modLog, spam, promotion, timeline, shutup, detectLanguage)

  lazy val mentionNotifier: MentionNotifier = wire[MentionNotifier]
  lazy val forms                            = wire[ForumForm]
  lazy val recent                           = wire[ForumRecent]

  lila.common.Bus.subscribeFun("team", "gdprErase") {
    case CreateTeam(id, name, _)        => categApi.makeTeam(id, name).discard
    case lila.user.User.GDPRErase(user) => postApi.eraseFromSearchIndex(user).discard
  }
}
