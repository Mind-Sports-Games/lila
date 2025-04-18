package lila.study

import strategygames.format.pgn.{ Glyph, Tags }
import strategygames.format.FEN
import strategygames.variant.Variant
import strategygames.{ Centis, Player => PlayerIndex }
import org.joda.time.DateTime

import strategygames.opening.{ FullOpening, FullOpeningDB }
import lila.tree.Node.{ Comment, Gamebook, Shapes }
import lila.user.User
import lila.common.Form
import lila.common.Iso

case class Chapter(
    _id: Chapter.Id,
    studyId: Study.Id,
    name: Chapter.Name,
    setup: Chapter.Setup,
    root: Node.Root,
    tags: Tags,
    order: Int,
    ownerId: User.ID,
    conceal: Option[Chapter.Ply] = None,
    practice: Option[Boolean] = None,
    gamebook: Option[Boolean] = None,
    description: Option[String] = None,
    relay: Option[Chapter.Relay] = None,
    serverEval: Option[Chapter.ServerEval] = None,
    createdAt: DateTime
) extends Chapter.Like {

  def updateRoot(f: Node.Root => Option[Node.Root]) =
    f(root) map { newRoot =>
      copy(root = newRoot)
    }

  def addNode(node: Node, path: Path, newRelay: Option[Chapter.Relay] = None): Option[Chapter] =
    updateRoot {
      _.withChildren(_.addNodeAt(node, path))
    } map {
      _.copy(relay = newRelay orElse relay)
    }

  def setShapes(shapes: Shapes, path: Path): Option[Chapter] =
    updateRoot(_.setShapesAt(shapes, path))

  def setComment(comment: Comment, path: Path): Option[Chapter] =
    updateRoot(_.setCommentAt(comment, path))

  def setGamebook(gamebook: Gamebook, path: Path): Option[Chapter] =
    updateRoot(_.setGamebookAt(gamebook, path))

  def deleteComment(commentId: Comment.Id, path: Path): Option[Chapter] =
    updateRoot(_.deleteCommentAt(commentId, path))

  def toggleGlyph(glyph: Glyph, path: Path): Option[Chapter] =
    updateRoot(_.toggleGlyphAt(glyph, path))

  def setClock(clock: Option[Centis], path: Path): Option[Chapter] =
    updateRoot(_.setClockAt(clock, path))

  def forceVariation(force: Boolean, path: Path): Option[Chapter] =
    updateRoot(_.forceVariationAt(force, path))

  def opening: Option[FullOpening] =
    if (!Variant.openingSensibleVariants(setup.variant.gameLogic)(setup.variant)) none
    else FullOpeningDB.searchInFens(setup.variant.gameLogic, root.mainline.map(_.fen))

  def isEmptyInitial = order == 1 && root.children.nodes.isEmpty

  def cloneFor(study: Study) =
    copy(
      _id = Chapter.makeId,
      studyId = study.id,
      ownerId = study.ownerId,
      createdAt = DateTime.now
    )

  private def tagsResultPlayerIndex = tags.resultPlayer match {
    case Some(Some(playerIndex)) => Some(Some(playerIndex))
    case Some(None)              => Some(None)
    case None                    => None
    case _                       => sys.error("Not implemented for draughts yet")
  }

  def metadata = Chapter.Metadata(
    _id = _id,
    name = name,
    setup = setup,
    resultPlayerIndex = tagsResultPlayerIndex.isDefined option tagsResultPlayerIndex,
    hasRelayPath = relay.exists(!_.path.isEmpty)
  )

  def isPractice = ~practice
  def isGamebook = ~gamebook
  def isConceal  = conceal.isDefined

  def withoutChildren = copy(root = root.withoutChildren)

  def withoutChildrenIfPractice = if (isPractice) copy(root = root.withoutChildren) else this

  def relayAndTags = relay map { Chapter.RelayAndTags(id, _, tags) }

  def isOverweight = root.children.countRecursive >= Chapter.maxNodes
}

object Chapter {

  // I've seen chapters with 35,000 nodes on prod.
  // It works but could be used for DoS.
  val maxNodes = 3000

  case class Id(value: String) extends AnyVal with StringValue
  implicit val idIso: Iso.StringIso[Id] = lila.common.Iso.string[Id](Id.apply, _.value)

  case class Name(value: String) extends AnyVal with StringValue
  implicit val nameIso: Iso.StringIso[Name] = lila.common.Iso.string[Name](Name.apply, _.value)

  sealed trait Like {
    val _id: Chapter.Id
    val name: Chapter.Name
    val setup: Chapter.Setup
    def id = _id

    def initialPosition = Position.Ref(id, Path.root)
  }

  case class Setup(
      gameId: Option[lila.game.Game.ID],
      variant: Variant,
      orientation: PlayerIndex,
      fromFen: Option[Boolean] = None
  ) {
    def isFromFen = ~fromFen
  }

  case class Relay(
      index: Int, // game index in the source URL
      path: Path,
      lastMoveAt: DateTime
  ) {
    def secondsSinceLastMove: Int = (nowSeconds - lastMoveAt.getSeconds).toInt
  }

  case class ServerEval(path: Path, done: Boolean)

  case class RelayAndTags(id: Id, relay: Relay, tags: Tags) {

    def looksAlive =
      tags.resultPlayer.isEmpty &&
        relay.lastMoveAt.isAfter {
          DateTime.now.minusMinutes {
            tags.clockConfig.fold(40)(_.limitInMinutes.toInt / 2 atLeast 15 atMost 60)
          }
        }

    def looksOver = !looksAlive
  }

  case class Metadata(
      _id: Id,
      name: Name,
      setup: Setup,
      resultPlayerIndex: Option[Option[Option[PlayerIndex]]],
      hasRelayPath: Boolean
  ) extends Like {

    def looksOngoing = resultPlayerIndex.exists(_.isEmpty) && hasRelayPath

    def resultStr: Option[String] =
      resultPlayerIndex.map(_.fold("*")(c => PlayerIndex.showResult(c)).replace("1/2", "½"))
  }

  case class IdName(id: Id, name: Name)

  case class Ply(value: Int) extends AnyVal with Ordered[Ply] {
    def compare(that: Ply) = Integer.compare(value, that.value)
  }

  def defaultName(order: Int) = Name(s"Chapter $order")

  private val defaultNameRegex = """Chapter \d+""".r
  def isDefaultName(n: Name)   = n.value.isEmpty || defaultNameRegex.matches(n.value)

  def fixName(n: Name) = Name(n.value.trim take 80)

  val idSize = 8

  def makeId = Id(lila.common.ThreadLocalRandom nextString idSize)

  def make(
      studyId: Study.Id,
      name: Name,
      setup: Setup,
      root: Node.Root,
      tags: Tags,
      order: Int,
      ownerId: User.ID,
      practice: Boolean,
      gamebook: Boolean,
      conceal: Option[Ply],
      relay: Option[Relay] = None
  ) =
    Chapter(
      _id = makeId,
      studyId = studyId,
      name = fixName(name),
      setup = setup,
      root = root,
      tags = tags,
      order = order,
      ownerId = ownerId,
      practice = practice option true,
      gamebook = gamebook option true,
      conceal = conceal,
      relay = relay,
      createdAt = DateTime.now
    )
}
