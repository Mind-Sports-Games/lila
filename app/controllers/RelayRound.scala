package controllers

import play.api.data.Form
import play.api.mvc._

import lila.api.Context
import lila.app._

// import lila.common.config.MaxPerSecond
import lila.relay.{ RelayRound => RoundModel, RelayTour => TourModel, RelayRoundForm }
import lila.user.{ User => UserModel }
import views._

final class RelayRound(
    env: Env,
    studyC: => Study
    // apiC: => Api
) extends LilaController(env) {
/*
  import lila.relay.JsonView.roundWrites

  def form(tourId: String) =
    Auth { implicit ctx => me =>
      NoLameOrBot {
        WithTourAndRoundsCanUpdate(tourId) { trs =>
          Ok(html.relay.roundForm.create(env.relay.roundForm.create(trs), trs.tour)).fuccess
        }
      }
    }

  def create(tourId: String) =
    AuthOrScopedBody(_.Study.Write)(
      auth = implicit ctx =>
        me =>
          NoLameOrBot {
            WithTourAndRoundsCanUpdate(tourId) { trs =>
              val tour = trs.tour
              env.relay.roundForm
                .create(trs)
                .bindFromRequest()(ctx.body, formBinding)
                .fold(
                  err => BadRequest(html.relay.roundForm.create(err, tour)).fuccess,
                  setup =>
                    env.relay.api.create(setup, me, tour) map { round =>
                      Redirect(routes.RelayRound.show(tour.slug, round.slug, round.id.value))
                    }
                )
            }
          },
      scoped = req =>
        me =>
          env.relay.api tourById TourModel.Id(tourId) flatMap {
            _ ?? { tour =>
              env.relay.api.withRounds(tour) flatMap { trs =>
                !(me.isBot || me.lame) ??
                  env.relay.roundForm
                    .create(trs)
                    .bindFromRequest()(req, formBinding)
                    .fold(
                      err => BadRequest(apiFormError(err)).fuccess,
                      setup =>
                        env.relay.api
                          .create(setup, me, tour)
                          .map(JsonOk(_))
                    )
              }
            }
          }
    )

  def edit(id: String) =
    Auth { implicit ctx => me =>
      OptionFuResult(env.relay.api.byIdAndContributor(id, me)) { rt =>
        Ok(html.relay.roundForm.edit(rt, env.relay.roundForm.edit(rt.round))).fuccess
      }
    }

  def update(id: String) =
    AuthOrScopedBody(_.Study.Write)(
      auth = implicit ctx =>
        me =>
          doUpdate(id, me)(ctx.body) flatMap {
            case None => notFound
            case Some(res) =>
              res
                .fold(
                  { case (old, err) => BadRequest(html.relay.roundForm.edit(old, err)) },
                  rt => Redirect(rt.path)
                )
                .fuccess
          },
      scoped = req =>
        me =>
          doUpdate(id, me)(req) map {
            case None => NotFound(jsonError("No such broadcast"))
            case Some(res) =>
              res.fold(
                { case (_, err) => BadRequest(apiFormError(err)) },
                rt => JsonOk(rt.round)
              )
          }
    )

  private def doUpdate(id: String, me: UserModel)(implicit
      req: Request[_]
  ): Fu[Option[Either[(RoundModel.WithTour, Form[RelayRoundForm.Data]), RoundModel.WithTour]]] =
    env.relay.api.byIdAndContributor(id, me) flatMap {
      _ ?? { rt =>
        env.relay.roundForm
          .edit(rt.round)
          .bindFromRequest()
          .fold(
            err => fuccess(Left(rt -> err)),
            data =>
              env.relay.api.update(rt.round) { data.update(_, me) }.dmap(_ withTour rt.tour) dmap Right.apply
          ) dmap some
      }
    }

  def reset(id: String) =
    Auth { implicit ctx => me =>
      OptionFuResult(env.relay.api.byIdAndContributor(id, me)) { rt =>
        env.relay.api.reset(rt.round, me) inject Redirect(rt.path)
      }
    }

  def show(ts: String, rs: String, id: String) =
    OpenOrScoped(_.Study.Read)(
      open = implicit ctx => {
        pageHit
        WithRoundAndTour(ts, rs, id) { rt =>
          val sc =
            if (rt.round.sync.ongoing)
              env.study.chapterRepo relaysAndTagsByStudyId rt.round.studyId flatMap { chapters =>
                chapters.find(_.looksAlive) orElse chapters.headOption match {
                  case Some(chapter) => env.study.api.byIdWithChapter(rt.round.studyId, chapter.id)
                  case None          => env.study.api byIdWithChapter rt.round.studyId
                }
              }
            else env.study.api byIdWithChapter rt.round.studyId
          sc flatMap { _ ?? { doShow(rt, _) } }
        }
      },
      scoped = _ =>
        me =>
          env.relay.api.byIdAndContributor(id, me) map {
            case None     => NotFound(jsonError("No such broadcast"))
            case Some(rt) => JsonOk(rt.round)
          }
    )

  def chapter(ts: String, rs: String, id: String, chapterId: String) =
    Open { implicit ctx =>
      WithRoundAndTour(ts, rs, id) { rt =>
        env.study.api.byIdWithChapter(rt.round.studyId, chapterId) flatMap {
          _ ?? { doShow(rt, _) }
        }
      }
    }

  def push(id: String) =
    ScopedBody(parse.tolerantText)(Seq(_.Study.Write)) { req => me =>
      env.relay.api.byIdAndContributor(id, me) flatMap {
        case None     => notFoundJson()
        case Some(rt) => env.relay.push(rt, req.body) inject jsonOkResult
      }
    }

  private def WithRoundAndTour(ts: String, rs: String, id: String)(
      f: RoundModel.WithTour => Fu[Result]
  )(implicit ctx: Context): Fu[Result] =
    OptionFuResult(env.relay.api byIdWithTour id) { rt =>
      if (!ctx.req.path.startsWith(rt.path)) Redirect(rt.path).fuccess
      else f(rt)
    }

  private def WithTour(id: String)(
      f: TourModel => Fu[Result]
  )(implicit ctx: Context): Fu[Result] =
    OptionFuResult(env.relay.api tourById TourModel.Id(id))(f)

  private def WithTourAndRoundsCanUpdate(id: String)(
      f: TourModel.WithRounds => Fu[Result]
  )(implicit ctx: Context): Fu[Result] =
    WithTour(id) { tour =>
      ctx.me.?? { env.relay.api.canUpdate(_, tour) } flatMap {
        _ ?? {
          env.relay.api withRounds tour flatMap f
        }
      }
    }

  private def doShow(rt: RoundModel.WithTour, oldSc: lila.study.Study.WithChapter)(implicit
      ctx: Context
  ): Fu[Result] =
    studyC.CanViewResult(oldSc.study) {
      for {
        (sc, studyData) <- studyC.getJsonData(oldSc)
        rounds          <- env.relay.api.byTourOrdered(rt.tour)
        data = env.relay.jsonView.makeData(
          rt.tour withRounds rounds.map(_.round),
          rt.round.id,
          studyData,
          ctx.userId exists sc.study.canContribute
        )
        chat      <- studyC.chatOf(sc.study)
        sVersion  <- env.study.version(sc.study.id)
        streamers <- studyC.streamersOf(sc.study)
      } yield EnableSharedArrayBuffer(
        Ok(html.relay.show(rt withStudy sc.study, data, chat, sVersion, streamers))
      )
    }

  implicit private def makeRelayId(id: String): RoundModel.Id           = RoundModel.Id(id)
  implicit private def makeChapterId(id: String): lila.study.Chapter.Id = lila.study.Chapter.Id(id)
*/
}
