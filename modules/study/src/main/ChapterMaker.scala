package lila.study

import strategygames.format.pgn.Tags
import strategygames.format.{ FEN, Forsyth }
import strategygames.variant.Variant
import strategygames.{ GameLogic, Player => PlayerIndex, PocketData }
import strategygames.chess.variant.{ Variant => ChessVariant }
import lila.chat.{ Chat, ChatApi }
import lila.game.{ Game, Namer }
import lila.user.User

final private class ChapterMaker(
    net: lila.common.config.NetConfig,
    lightUser: lila.user.LightUserApi,
    chatApi: ChatApi,
    gameRepo: lila.game.GameRepo,
    pgnFetch: PgnFetch,
    pgnDump: lila.game.PgnDump
)(implicit ec: scala.concurrent.ExecutionContext) {

  import ChapterMaker._

  def apply(study: Study, data: Data, order: Int, userId: User.ID): Fu[Chapter] =
    data.game.??(parseGame) flatMap {
      case None =>
        data.game ?? pgnFetch.fromUrl flatMap {
          case Some(pgn) => fromFenOrPgnOrBlank(study, data.copy(pgn = pgn.some), order, userId)
          case _         => fromFenOrPgnOrBlank(study, data, order, userId)
        }
      case Some(game) => fromGame(study, game, data, order, userId)
    } map { (c: Chapter) =>
      if (c.name.value.isEmpty) c.copy(name = Chapter defaultName order) else c
    }

  def fromFenOrPgnOrBlank(study: Study, data: Data, order: Int, userId: User.ID): Fu[Chapter] =
    data.pgn.filter(_.trim.nonEmpty) match {
      case Some(pgn) => fromPgn(study, pgn, data, order, userId)
      case None      => fuccess(fromFenOrBlank(study, data, order, userId))
    }

  private def fromPgn(study: Study, pgn: String, data: Data, order: Int, userId: User.ID): Fu[Chapter] = {
    implicit val variant = Variant.Chess(ChessVariant.default)
    // TODO: support PGN parsing for other variants later.
    for {
      contributors <- lightUser.asyncMany(study.members.contributorIds.toList)
      parsed <- PgnImport(pgn, contributors.flatten).toFuture recoverWith { case e: Exception =>
        fufail(ValidationException(e.getMessage))
      }
    } yield Chapter.make(
      studyId = study.id,
      name = parsed.tags(_.P1).flatMap { p1 =>
        parsed
          .tags(_.P2)
          .ifTrue {
            data.name.value.isEmpty || data.isDefaultName
          }
          .map { p2 =>
            Chapter.Name(s"$p1 - $p2")
          }
      } | data.name,
      setup = Chapter.Setup(
        none,
        parsed.variant,
        resolveOrientation(data.realOrientation, parsed.root, parsed.tags)
      ),
      root = parsed.root,
      tags = parsed.tags,
      order = order,
      ownerId = userId,
      practice = data.isPractice,
      gamebook = data.isGamebook,
      conceal = data.isConceal option Chapter.Ply(parsed.root.ply)
    )
  }

  private def resolveOrientation(
      orientation: Orientation,
      root: Node.Root,
      tags: Tags = Tags.empty
  ): PlayerIndex =
    orientation match {
      case Orientation.Fixed(playerIndex)   => playerIndex
      case _ if tags.resultPlayer.isDefined => PlayerIndex.p1
      case _                                => root.lastMainlineNode.playerIndex
    }

  private def fromFenOrBlank(study: Study, data: Data, order: Int, userId: User.ID): Chapter = {
    val variant =
      data.variant.flatMap(v => Variant.apply(v)) | Variant.default(GameLogic.Chess())
    (data.fen.map(FEN(variant, _)).flatMap { Forsyth.<<<@(variant.gameLogic, variant, _) } match {
      case Some(sit) =>
        Node.Root(
          ply = sit.plies,
          turnCount = sit.turnCount,
          playedPlayerIndex =
            if (sit.situation.board.history.currentTurn.nonEmpty) sit.situation.player
            else !sit.situation.player,
          variant = sit.situation.board.variant,
          fen = Forsyth.>>(sit.situation.board.variant.gameLogic, sit),
          check = sit.situation.check,
          clock = none,
          pocketData = sit.situation.board.pocketData,
          children = Node.emptyChildren
        ) -> true
      case None =>
        Node.Root(
          ply = 0,
          turnCount = 0,
          playedPlayerIndex = PlayerIndex.P2,
          variant = variant,
          fen = variant.initialFen,
          check = false,
          clock = none,
          pocketData = variant.dropsVariant option PocketData.init(variant.gameLogic),
          children = Node.emptyChildren
        ) -> false
    }) match {
      case (root, isFromFen) =>
        Chapter.make(
          studyId = study.id,
          name = data.name,
          setup = Chapter.Setup(
            none,
            variant,
            resolveOrientation(data.realOrientation, root),
            fromFen = isFromFen option true
          ),
          root = root,
          tags = Tags.empty,
          order = order,
          ownerId = userId,
          practice = data.isPractice,
          gamebook = data.isGamebook,
          conceal = None
        )
    }
  }

  private[study] def fromGame(
      study: Study,
      game: Game,
      data: Data,
      order: Int,
      userId: User.ID,
      initialFen: Option[FEN] = None
  ): Fu[Chapter] =
    for {
      root <- game2root(game, initialFen)
      tags <- pgnDump.tags(game, initialFen, none, withOpening = true)
      name <- {
        if (data.isDefaultName)
          Namer.gameVsText(game, withRatings = false)(lightUser.async) dmap Chapter.Name.apply
        else fuccess(data.name)
      }
      _ = notifyChat(study, game, userId)
    } yield Chapter.make(
      studyId = study.id,
      name = name,
      setup = Chapter.Setup(
        !game.synthetic option game.id,
        game.variant,
        data.realOrientation match {
          case Orientation.Auto               => PlayerIndex.p1
          case Orientation.Fixed(playerIndex) => playerIndex
        }
      ),
      root = root,
      tags = PgnTags(tags),
      order = order,
      ownerId = userId,
      practice = data.isPractice,
      gamebook = data.isGamebook,
      conceal = data.isConceal option Chapter.Ply(root.ply)
    )

  def notifyChat(study: Study, game: Game, userId: User.ID) =
    if (study.isPublic) List(game.id, s"${game.id}/w") foreach { chatId =>
      chatApi.userChat.write(
        chatId = Chat.Id(chatId),
        userId = userId,
        text = s"I'm studying this game on ${net.domain}/study/${study.id}",
        publicSource = none,
        _.Round,
        persist = false
      )
    }

  private[study] def game2root(game: Game, initialFen: Option[FEN]): Fu[Node.Root] =
    initialFen.fold(gameRepo initialFen game) { fen =>
      fuccess(fen.some)
    } map { GameToRoot(game, _, withClocks = true) }

  private val UrlRegex = {
    val escapedDomain = net.domain.value.replace(".", "\\.")
    s"""$escapedDomain/(\\w{8,12})"""
  }.r.unanchored

  @scala.annotation.tailrec
  private def parseGame(str: String): Fu[Option[Game]] =
    str match {
      case s if s.lengthIs == Game.gameIdSize => gameRepo.game(s)
      case s if s.lengthIs == Game.fullIdSize => gameRepo.game(Game.takeGameId(s))
      case UrlRegex(id)                       => parseGame(id)
      case _                                  => fuccess(none)
    }
}

private[study] object ChapterMaker {

  case class ValidationException(message: String) extends lila.base.LilaException

  sealed trait Mode {
    def key = toString.toLowerCase
  }
  object Mode {
    case object Normal   extends Mode
    case object Practice extends Mode
    case object Gamebook extends Mode
    case object Conceal  extends Mode
    val all                = List(Normal, Practice, Gamebook, Conceal)
    def apply(key: String) = all.find(_.key == key)
  }

  trait ChapterData {
    def orientation: String
    def mode: String
    def realOrientation =
      PlayerIndex.fromName(orientation).fold[Orientation](Orientation.Auto)(Orientation.Fixed)
    def isPractice = mode == Mode.Practice.key
    def isGamebook = mode == Mode.Gamebook.key
    def isConceal  = mode == Mode.Conceal.key
  }

  sealed trait Orientation
  object Orientation {
    case class Fixed(playerIndex: PlayerIndex) extends Orientation
    case object Auto                           extends Orientation
  }

  case class Data(
      name: Chapter.Name,
      game: Option[String] = None,
      variant: Option[String] = None,
      fen: Option[String] = None,
      pgn: Option[String] = None,
      orientation: String = "p1", // can be "auto"
      mode: String = ChapterMaker.Mode.Normal.key,
      initial: Boolean = false,
      isDefaultName: Boolean = true
  ) extends ChapterData {

    def manyGames =
      game
        .??(_.linesIterator.take(Study.maxChapters).toList)
        .map(_.trim)
        .filter(_.nonEmpty)
        .map { g => copy(game = g.some) }
        .some
        .filter(_.sizeIs > 1)
  }

  case class EditData(
      id: Chapter.Id,
      name: Chapter.Name,
      orientation: String,
      mode: String,
      description: String // boolean
  ) extends ChapterData {
    def hasDescription = description.nonEmpty
  }

  case class DescData(id: Chapter.Id, desc: String)
}
