package controllers

import play.api.mvc.Result
import views.*

import lila.api.Context
import lila.app.*
import lila.report.Suspect
import play.api.data.Form

final class Appeal(env: Env, reportC: => Report, prismicC: => Prismic, userC: => User)
    extends LilaController(env) {

  private def form(implicit ctx: Context) =
    if isGranted(_.Appeals) then lila.appeal.Appeal.modForm
    else lila.appeal.Appeal.form

  def home =
    Auth { implicit ctx => me =>
      renderAppealOrTree(me) map { content => Ok(content) }
    }

  def landing =
    Auth { implicit ctx => _ =>
      if ctx.isAppealUser || isGranted(_.Appeals) then {
        pageHit
        OptionOk(prismicC.getPage("appeal-landing")) { case (doc, resolver) =>
          views.html.site.page.lone(doc, resolver): scalatags.Text.Frag
        }
      } else notFound
    }

  private def renderAppealOrTree(
      me: lila.user.User,
      err: Option[Form[String]] = None
  )(implicit ctx: Context) = env.appeal.api.mine(me) flatMap {
    case None =>
      env.playban.api.currentBan(me.id).dmap(_.isDefined) map {
        html.appeal.tree(me, _)
      }
    case Some(a) => fuccess(html.appeal.discussion(a, err | form))
  }

  def post =
    AuthBody { implicit ctx => me =>
      implicit val req = ctx.body
      form
        .bindFromRequest()
        .fold(
          err => renderAppealOrTree(me, err.some) map { content => BadRequest(content) },
          text => env.appeal.api.post(text, me) inject Redirect(routes.Appeal.home).flashSuccess
        )
    }

  def queue =
    Secure(_.Appeals) { implicit ctx => me =>
      env.appeal.api.queueOf(
        me.user
      ) zip env.report.api.inquiries.allBySuspect zip reportC.getScores flatMap {
        case ((appeals, inquiries), ((scores, streamers), nbAppeals)) =>
          (env.user.lightUserApi preloadMany appeals.map(_.id)) inject
            Ok(html.appeal.queue(appeals, inquiries, scores, streamers, nbAppeals))
      }
    }

  def show(username: String) =
    Secure(_.Appeals) { implicit ctx => me =>
      asMod(username) { (appeal, suspect) =>
        getModData(me, appeal, suspect) map { modData =>
          Ok(html.appeal.discussion.show(appeal, form, modData))
        }
      }
    }

  def reply(username: String) =
    SecureBody(_.Appeals) { implicit ctx => me =>
      asMod(username) { (appeal, suspect) =>
        implicit val req = ctx.body
        form
          .bindFromRequest()
          .fold(
            err =>
              getModData(me, appeal, suspect) map { modData =>
                BadRequest(html.appeal.discussion.show(appeal, err, modData))
              },
            text =>
              for {
                _ <- env.security.automaticEmail.onAppealReply(suspect.user)
                preset = getPresets.findLike(text)
                _ <- env.appeal.api.reply(text, appeal, me, preset.map(_.name))
                _ <- env.mod.logApi.appealPost(me.id, suspect.user.id)
              } yield Redirect(s"${routes.Appeal.show(username)}#appeal-actions")
          )
      }
    }

  private def getModData(
      me: lila.user.Holder,
      @annotation.nowarn("msg=unused") appeal: lila.appeal.Appeal,
      suspect: Suspect
  )(implicit
      ctx: Context
  ) =
    for {
      users   <- env.security.userLogins(suspect.user, 100)
      logins  <- userC.loginsTableData(suspect.user, users, 100)
      appeals <- env.appeal.api.byUserIds(suspect.user.id :: logins.userLogins.otherUserIds)
      inquiry <- env.report.api.inquiries.ofSuspectId(suspect.user.id)
    } yield html.appeal.discussion.ModData(
      mod = me,
      suspect = suspect,
      presets = getPresets,
      logins = logins,
      appeals = appeals,
      renderIp = env.mod.ipRender(me),
      inquiry = inquiry.filter(_.mod == me.user.id)
    )

  def mute(username: String) =
    Secure(_.Appeals) { implicit ctx => me =>
      asMod(username) { (appeal, _) =>
        env.appeal.api.toggleMute(appeal) >>
          env.report.api.inquiries.toggle(lila.report.Mod(me.user), appeal.id) map { _ =>
            Redirect(routes.Appeal.queue)
          }
      }
    }

  def notifySlack(username: String) =
    Secure(_.NotifySlack) { implicit ctx => me =>
      asMod(username) { (_, suspect) =>
        env.irc.slack.userAppeal(user = suspect.user, mod = me) inject NoContent
      }
    }

  def snooze(username: String, dur: String) =
    Secure(_.Appeals) { implicit ctx => me =>
      asMod(username) { (appeal, _) =>
        env.appeal.api.snooze(me.user, appeal.id, dur)
        env.report.api.inquiries.toggle(lila.report.Mod(me.user), appeal.id) inject
          Redirect(routes.Appeal.queue)
      }
    }

  private def getPresets = env.mod.presets.appealPresets.get()

  private def asMod(
      username: String
  )(f: (lila.appeal.Appeal, Suspect) => Fu[Result])(implicit ctx: Context): Fu[Result] =
    env.user.repo.named(username) flatMap {
      case Some(user) =>
        env.appeal.api.get(user) flatMap {
          case Some(appeal) => f(appeal, Suspect(user)).dmap(some)
          case None         => fuccess(none)
        }
      case None => fuccess(none)
    } flatMap {
      _.fold(notFound)(fuccess)
    }
}
