package controllers

import lila.app.{ *, given }

import play.api.data.*
import play.api.data.Forms.*
import play.api.libs.json.*
import views.html

final class Learn(env: Env) extends LilaController(env) {

  import lila.learn.JSONHandlers.*
  import lila.core.lilaism.Lilaism.unapply

  def index =
    Open { implicit ctx =>
      pageHit
      ctx.me
        .so { me =>
          env.learn.api.get(me) map { Json.toJson(_) } map some
        }
        .map { progress =>
          Ok(html.learn.index(progress))
        }
    }

  private val scoreForm = Form(
    mapping(
      "stage" -> nonEmptyText,
      "level" -> number,
      "score" -> number
    )(Tuple3.apply)(unapply)
  )

  def score =
    AuthBody { implicit ctx => me =>
      implicit val body = ctx.body
      scoreForm
        .bindFromRequest()
        .fold(
          _ => BadRequest.fuccess,
          { case (stage, level, s) =>
            val score = lila.learn.StageProgress.Score(s)
            env.learn.api.setScore(me, stage, level, score) >>
              env.activity.write.learn(me.id, stage) inject Ok(Json.obj("ok" -> true))
          }
        )
    }

  def reset =
    AuthBody { _ => me =>
      env.learn.api.reset(me) inject Ok(Json.obj("ok" -> true))
    }
}
