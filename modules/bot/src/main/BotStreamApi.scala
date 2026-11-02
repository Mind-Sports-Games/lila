package lila.bot

import play.api.data.Form

import lila.user.User

final class BotStreamApi(
    repo: BotStreamRepo,
    coordinator: BotVsBotCoordinator
)(implicit ec: scala.concurrent.ExecutionContext) {

  def list: Fu[List[BotStream]] = repo.all

  def one(id: BotStream.ID): Fu[Option[BotStream]] = repo.byId(id)

  def createForm = BotStreamForm.form

  def editForm(stream: BotStream) = BotStreamForm.form.fill(BotStreamForm.Data.make(stream))

  def withUniqueName(
      form: Form[BotStreamForm.Data],
      except: Option[BotStream.ID]
  ): Fu[Form[BotStreamForm.Data]] =
    form.value.fold(fuccess(form)) { data =>
      repo.nameTaken(data.name.trim, except) map {
        if (_) form.withError("name", "Another bot stream already has this name") else form
      }
    }

  def create(data: BotStreamForm.Data, by: User.ID): Fu[BotStream] = {
    val stream = data.make(by)
    repo.insert(stream) inject stream
  }

  def update(old: BotStream, data: BotStreamForm.Data): Funit =
    repo.update(data.update(old)) >> coordinator.refresh

  def delete(stream: BotStream): Funit =
    repo.delete(stream.id) >> coordinator.refresh

  def clone(old: BotStream) =
    old.copy(name = s"${old.name} (clone)")
}
