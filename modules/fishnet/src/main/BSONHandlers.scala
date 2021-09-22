package lila.fishnet

import lila.db.dsl._
import lila.db.BSON
import lila.db.BSON.{ Reader, Writer }
import reactivemongo.api.bson._

import strategygames.GameLogic
import strategygames.variant.Variant

private object BSONHandlers {

  implicit val ClientKeyBSONHandler     = stringAnyValHandler[Client.Key](_.value, Client.Key.apply)
  implicit val ClientVersionBSONHandler = stringAnyValHandler[Client.Version](_.value, Client.Version.apply)
  implicit val ClientPythonBSONHandler  = stringAnyValHandler[Client.Python](_.value, Client.Python.apply)
  implicit val ClientUserIdBSONHandler  = stringAnyValHandler[Client.UserId](_.value, Client.UserId.apply)

  implicit val ClientSkillBSONHandler = tryHandler[Client.Skill](
    { case BSONString(v) => Client.Skill byKey v toTry s"Invalid client skill $v" },
    x => BSONString(x.key)
  )

  import Client.Instance
  implicit val InstanceBSONHandler = Macros.handler[Instance]

  implicit val ClientBSONHandler = Macros.handler[Client]

  implicit val VariantBSONHandler = new BSON[Variant] {
    def reads(r: Reader) = Variant(GameLogic(r.intD("gl")), r.int("v")) match {
      case Some(v) => v
      case None => sys.error(s"No such variant: ${r.intD("v")} for gamelogic: ${r.intD("gl")}")
    }
    def writes(w: Writer, v: Variant) = $doc("gl" -> v.gameLogic.id, "v" -> v.id)
  }

  implicit val WorkIdBSONHandler = stringAnyValHandler[Work.Id](_.value, Work.Id.apply)
  import Work.Acquired
  implicit val MoveAcquiredHandler = Macros.handler[Acquired]
  import Work.Clock
  implicit val ClockHandler = Macros.handler[Clock]
  import Work.Game
  implicit val GameHandler = Macros.handler[Game]
  import Work.Sender
  implicit val SenderHandler = Macros.handler[Sender]
  import Work.Analysis
  implicit val AnalysisHandler = Macros.handler[Analysis]
}
