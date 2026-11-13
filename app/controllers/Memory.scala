package controllers

import lila.app.*

final class Memory(env: Env) extends LilaController(env) {

  def home =
    Open { implicit ctx =>
      NoBot {
        tickProgress(lila.progress.Step.MemoryGame)
        Ok(views.html.memory.home).fuccess
      }
    }
}
