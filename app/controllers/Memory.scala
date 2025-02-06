package controllers

import lila.app._

final class Memory(env: Env) extends LilaController(env) {

  def home =
    Open { implicit ctx =>
      NoBot {
        Ok(views.html.memory.home).fuccess
      }
    }
}
