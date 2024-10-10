package lila.study

import strategygames.format.pgn.{ Glyph, Glyphs, Tag, Tags }
import strategygames.format.{ FEN, Uci, UciCharPair }
import strategygames.variant.Variant
import strategygames.chess.variant.{ Variant => ChessVariant }
import strategygames.{
  Centis,
  Player => PlayerIndex,
  GameFamily,
  GameLogic,
  Pocket,
  PocketData,
  Pockets,
  Pos,
  PromotableRole,
  Role
}
import strategygames.chess.{ Pos => ChessPos }
import org.joda.time.DateTime
import reactivemongo.api.bson._
import scala.util.Success
import Uci.WithSan

import lila.common.Iso
import lila.db.BSON
import lila.db.BSON.{ Reader, Writer }
import lila.db.dsl._
import lila.tree.Eval
import lila.tree.Eval.Score
import lila.tree.Node.{ Comment, Comments, Gamebook, Shape, Shapes }

object BSONHandlers {
  import Chapter._

  implicit val StudyIdBSONHandler     = stringIsoHandler(Study.idIso)
  implicit val StudyNameBSONHandler   = stringIsoHandler(Study.nameIso)
  implicit val ChapterIdBSONHandler   = stringIsoHandler(Chapter.idIso)
  implicit val ChapterNameBSONHandler = stringIsoHandler(Chapter.nameIso)
  implicit val CentisBSONHandler      = intIsoHandler(Iso.centisIso)
  implicit val StudyTopicBSONHandler  = stringIsoHandler(StudyTopic.topicIso)
  implicit val StudyTopicsBSONHandler =
    implicitly[BSONHandler[List[StudyTopic]]].as[StudyTopics](StudyTopics.apply, _.value)

  private case class DbMember(role: StudyMember.Role) extends AnyVal

  implicit val GamebookBSONHandler = Macros.handler[Gamebook]

  implicit val GlyphsBSONHandler = {
    val intReader = collectionReader[List, Int]
    tryHandler[Glyphs](
      { case arr: Barr =>
        intReader.readTry(arr) map { ints =>
          Glyphs.fromList(ints flatMap Glyph.find)
        }
      },
      x => BSONArray(x.toList.map(_.id).map(BSONInteger.apply))
    )
  }

  import Study.IdName
  implicit val StudyIdNameBSONHandler = Macros.handler[IdName]

  implicit private val CommentIdBSONHandler = stringAnyValHandler[Comment.Id](_.value, Comment.Id.apply)
  implicit private val CommentTextBSONHandler =
    stringAnyValHandler[Comment.Text](_.value, Comment.Text.apply)
  implicit val CommentAuthorBSONHandler = quickHandler[Comment.Author](
    {
      case BSONString(lila.user.User.playstrategyId | "l") => Comment.Author.PlayStrategy
      case BSONString(name)                                => Comment.Author.External(name)
      case doc: Bdoc =>
        {
          for {
            id   <- doc.getAsOpt[String]("id")
            name <- doc.getAsOpt[String]("name")
          } yield Comment.Author.User(id, name)
        } err s"Invalid comment author $doc"
      case _ => Comment.Author.Unknown
    },
    {
      case Comment.Author.User(id, name) => $doc("id" -> id, "name" -> name)
      case Comment.Author.External(name) => BSONString(s"${name.trim}")
      case Comment.Author.PlayStrategy   => BSONString("l")
      case Comment.Author.Unknown        => BSONString("")
    }
  )
  implicit private val CommentBSONHandler = Macros.handler[Comment]

  implicit val CommentsBSONHandler: BSONHandler[Comments] =
    isoHandler[Comments, List[Comment]]((s: Comments) => s.value, Comments(_))

  implicit val EvalScoreBSONHandler = {
    val mateFactor = 1000000
    BSONIntegerHandler.as[Score](
      v =>
        Score {
          if (v >= mateFactor || v <= -mateFactor) Right(Eval.Mate(v / mateFactor))
          else Left(Eval.Cp(v))
        },
      _.value.fold(
        cp => cp.value atLeast (-mateFactor + 1) atMost (mateFactor - 1),
        mate => mate.value * mateFactor
      )
    )
  }

  implicit val VariantBSONHandler = new BSON[Variant] {
    def reads(r: Reader) = Variant(GameLogic(r.intD("gl")), r.int("v")) match {
      case Some(v) => v
      case None    => sys.error(s"No such variant: ${r.intD("v")} for gamelogic: ${r.intD("gl")}")
    }
    def writes(w: Writer, v: Variant) = $doc("gl" -> v.gameLogic.id, "v" -> v.id)
  }

  implicit val PathBSONHandler = BSONStringHandler.as[Path](Path.apply, _.toString)

  implicit val PgnTagBSONHandler = tryHandler[Tag](
    { case BSONString(v) =>
      v.split(":", 2) match {
        case Array(name, value) => Success(Tag(name, value))
        case _                  => handlerBadValue(s"Invalid pgn tag $v")
      }
    },
    t => BSONString(s"${t.name}:${t.value}")
  )
  implicit val tagsHandler                     = implicitly[BSONHandler[List[Tag]]].as[Tags](Tags.apply, _.value)
  implicit private val ChapterSetupBSONHandler = Macros.handler[Chapter.Setup]
  implicit val ChapterRelayBSONHandler         = Macros.handler[Chapter.Relay]
  implicit val ChapterServerEvalBSONHandler    = Macros.handler[Chapter.ServerEval]
  import Chapter.Ply
  implicit val PlyBSONHandler     = intAnyValHandler[Ply](_.value, Ply.apply)
  implicit val ChapterBSONHandler = Macros.handler[Chapter]

  implicit val PositionRefBSONHandler = tryHandler[Position.Ref](
    { case BSONString(v) => Position.Ref.decode(v) toTry s"Invalid position $v" },
    x => BSONString(x.encode)
  )
  implicit val StudyMemberRoleBSONHandler = tryHandler[StudyMember.Role](
    { case BSONString(v) => StudyMember.Role.byId get v toTry s"Invalid role $v" },
    x => BSONString(x.id)
  )

  implicit private val DbMemberBSONHandler = Macros.handler[DbMember]
  implicit private[study] val StudyMemberBSONWriter = new BSONWriter[StudyMember] {
    def writeTry(x: StudyMember) = DbMemberBSONHandler writeTry DbMember(x.role)
  }
  implicit private[study] val MembersBSONHandler: BSONHandler[StudyMembers] =
    implicitly[BSONHandler[Map[String, DbMember]]].as[StudyMembers](
      members =>
        StudyMembers(members map { case (id, dbMember) =>
          id -> StudyMember(id, dbMember.role)
        }),
      _.members.view.mapValues(m => DbMember(m.role)).toMap
    )
  import Study.Visibility
  implicit private[study] val VisibilityHandler = tryHandler[Visibility](
    { case BSONString(v) => Visibility.byKey get v toTry s"Invalid visibility $v" },
    v => BSONString(v.key)
  )
  import Study.From
  implicit private[study] val FromHandler = tryHandler[From](
    { case BSONString(v) =>
      v.split(' ') match {
        case Array("scratch")   => Success(From.Scratch)
        case Array("game", id)  => Success(From.Game(id))
        case Array("study", id) => Success(From.Study(Study.Id(id)))
        case Array("relay")     => Success(From.Relay(none))
        case Array("relay", id) => Success(From.Relay(Study.Id(id).some))
        case _                  => handlerBadValue(s"Invalid from $v")
      }
    },
    x =>
      BSONString(x match {
        case From.Scratch   => "scratch"
        case From.Game(id)  => s"game $id"
        case From.Study(id) => s"study $id"
        case From.Relay(id) => s"relay${id.fold("")(" " + _)}"
      })
  )
  import Settings.UserSelection
  implicit private[study] val UserSelectionHandler = tryHandler[UserSelection](
    { case BSONString(v) => UserSelection.byKey get v toTry s"Invalid user selection $v" },
    x => BSONString(x.key)
  )
  implicit val SettingsBSONHandler = new BSON[Settings] {
    def reads(r: Reader) =
      Settings(
        computer = r.get[UserSelection]("computer"),
        explorer = r.get[UserSelection]("explorer"),
        cloneable = r.getO[UserSelection]("cloneable") | Settings.init.cloneable,
        chat = r.getO[UserSelection]("chat") | Settings.init.chat,
        sticky = r.getO[Boolean]("sticky") | Settings.init.sticky,
        description = r.getO[Boolean]("description") | Settings.init.description
      )
    private val writer                 = Macros.writer[Settings]
    def writes(w: Writer, s: Settings) = writer.writeTry(s).get
  }

  import Study.Likes
  implicit val LikesBSONHandler = intAnyValHandler[Likes](_.value, Likes.apply)
  import Study.Rank
  implicit private[study] val RankBSONHandler =
    dateIsoHandler[Rank](Iso[DateTime, Rank](Rank.apply, _.value))

  // implicit val StudyBSONHandler = BSON.LoggingHandler(logger)(Macros.handler[Study])
  implicit val StudyBSONHandler = Macros.handler[Study]

  implicit val lightStudyBSONReader = new BSONDocumentReader[Study.LightStudy] {
    def readDocument(doc: BSONDocument) =
      Success(
        Study.LightStudy(
          isPublic = doc.string("visibility") has "public",
          contributors = doc.getAsOpt[StudyMembers]("members").??(_.contributorIds)
        )
      )
  }

  implicit val ChapterMetadataBSONReader = new BSONDocumentReader[Chapter.Metadata] {
    def readDocument(doc: Bdoc) = for {
      id    <- doc.getAsTry[Chapter.Id]("_id")
      name  <- doc.getAsTry[Chapter.Name]("name")
      setup <- doc.getAsTry[Chapter.Setup]("setup")
      resultPlayerIndex = doc
        .getAsOpt[List[String]]("tags")
        .map {
          _.headOption
            .map(_ drop 7)
            .filter("*" !=) map PlayerIndex.fromResult
        }
      hasRelayPath = doc.getAsOpt[Bdoc]("relay").flatMap(_ string "path").exists(_.nonEmpty)
    } yield Chapter.Metadata(id, name, setup, resultPlayerIndex, hasRelayPath)
  }

  case class VariantHandlers()(implicit variant: Variant) {

    implicit val PosBSONHandler = tryHandler[Pos](
      { case BSONString(v) => Pos.fromKey(variant.gameLogic, v) toTry s"No such pos: $v" },
      x => BSONString(x.key)
    )

    implicit val ChessPosBSONHandler = tryHandler[ChessPos](
      { case BSONString(v) => ChessPos.fromKey(v) toTry s"No such pos: $v" },
      x => BSONString(x.key)
    )

    implicit val ShapeBSONHandler = new BSON[Shape] {
      def reads(r: Reader) = {
        val brush = r str "b"
        r.getO[Pos]("p") map { pos =>
          Shape.Circle(brush, pos)
        } getOrElse Shape.Arrow(brush, r.get[Pos]("o"), r.get[Pos]("d"))
      }
      def writes(w: Writer, t: Shape) =
        t match {
          case Shape.Circle(brush, pos)       => $doc("b" -> brush, "p" -> pos.key)
          case Shape.Arrow(brush, orig, dest) => $doc("b" -> brush, "o" -> orig.key, "d" -> dest.key)
        }
    }

    implicit val ShapesBSONHandler: BSONHandler[Shapes] =
      isoHandler[Shapes, List[Shape]]((s: Shapes) => s.value, Shapes(_))

    implicit val PromotableRoleHandler = tryHandler[PromotableRole](
      { case BSONString(v) =>
        v.headOption flatMap Role
          .allPromotableByForsyth(variant.gameLogic, variant.gameFamily)
          .get toTry s"No such role: $v"
      },
      x => BSONString(x.forsyth.toString)
    )

    implicit val RoleHandler = tryHandler[Role](
      { case BSONString(v) =>
        v.headOption flatMap Role
          .allByForsyth(variant.gameLogic, variant.gameFamily)
          .get toTry s"No such role: $v"
      },
      x => BSONString(x.forsyth.toString)
    )

    implicit val UciHandler = tryHandler[Uci](
      { case BSONString(v) => Uci(variant.gameLogic, variant.gameFamily, v) toTry s"Bad UCI: $v" },
      x => BSONString(x.uci)
    )

    implicit val UciCharPairHandler = tryHandler[UciCharPair](
      { case BSONString(v) =>
        v.toArray match {
          case Array(a, b) => Success(UciCharPair(a, b))
          case _           => handlerBadValue(s"Invalid UciCharPair $v")
        }
      },
      x => BSONString(x.toString)
    )

    implicit def CrazyDataBSONHandler: BSON[PocketData] =
      new BSON[PocketData] {
        private def writePocket(p: Pocket) = p.roles.map(_.forsyth).mkString
        //will need to be updated for different GameFamilys
        private def readPocket(p: String) = Pocket(
          p.view.flatMap(r => Role.forsyth(variant.gameLogic, r)).toList
        )
        def reads(r: Reader) =
          PocketData.Chess(
            strategygames.chess.PocketData(
              promoted = r.getsD[strategygames.chess.Pos]("o").toSet,
              pockets = Pockets(
                p1 = readPocket(r.strD("w")),
                p2 = readPocket(r.strD("b"))
              )
            )
          )
        def writes(w: Writer, s: PocketData) =
          $doc(
            "o" -> w.listO(s.promoted.toList),
            "w" -> w.strO(writePocket(s.pockets.p1)),
            "b" -> w.strO(writePocket(s.pockets.p2))
          )
      }

    def readNode(doc: Bdoc, id: UciCharPair): Option[Node] = {
      import Node.{ BsonFields => F }
      for {
        ply       <- doc.getAsOpt[Int](F.ply)
        turnCount <- doc.getAsOpt[Int](F.turnCount).orElse(ply.some)
        playedPlayerIndex <- doc
          .getAsOpt[PlayerIndex](F.playedPlayerIndex)
          .orElse(
            PlayerIndex.apply(if (ply % 2 == 1) "p1" else "p2")
          ) //assume existing studies were single action
        uci <- doc.getAsOpt[Uci](F.uci)
        san <- doc.getAsOpt[String](F.san)
        fen <- doc.getAsOpt[FEN](F.fen)
        check          = ~doc.getAsOpt[Boolean](F.check)
        shapes         = doc.getAsOpt[Shapes](F.shapes).getOrElse(Shapes.empty)
        comments       = doc.getAsOpt[Comments](F.comments).getOrElse(Comments.empty)
        gamebook       = doc.getAsOpt[Gamebook](F.gamebook)
        glyphs         = doc.getAsOpt[Glyphs](F.glyphs).getOrElse(Glyphs.empty)
        score          = doc.getAsOpt[Score](F.score)
        clock          = doc.getAsOpt[Centis](F.clock)
        crazy          = doc.getAsOpt[PocketData](F.crazy)
        forceVariation = ~doc.getAsOpt[Boolean](F.forceVariation)
      } yield Node(
        id,
        ply,
        turnCount,
        playedPlayerIndex,
        variant,
        WithSan(variant.gameLogic, uci, san),
        fen,
        check,
        shapes,
        comments,
        gamebook,
        glyphs,
        score,
        clock,
        crazy,
        Node.emptyChildren,
        forceVariation
      )
    }

    def writeNode(n: Node) = {
      import Node.BsonFields._
      val w = new Writer
      $doc(
        ply               -> n.ply,
        playedPlayerIndex -> n.playedPlayerIndex,
        uci               -> n.move.uci,
        san               -> n.move.san,
        fen               -> n.fen,
        check             -> w.boolO(n.check),
        shapes            -> n.shapes.value.nonEmpty.option(n.shapes),
        comments          -> n.comments.value.nonEmpty.option(n.comments),
        gamebook          -> n.gamebook,
        glyphs            -> n.glyphs.nonEmpty,
        score             -> n.score,
        clock             -> n.clock,
        crazy             -> n.pocketData,
        forceVariation    -> w.boolO(n.forceVariation),
        order -> {
          (n.children.nodes.sizeIs > 1) option n.children.nodes.map(_.id)
        }
      )
    }
  }

  import Node.Root
  implicit private[study] lazy val NodeRootBSONHandler: BSON[Root] = new BSON[Root] {
    import Node.{ BsonFields => F }
    def reads(fullReader: Reader) = {
      val rootNode         = fullReader.doc.getAsOpt[Bdoc](Path.rootDbKey) err "Missing root"
      val r                = new Reader(rootNode)
      implicit val variant = (r getO [Variant] F.variant) | Variant.Chess(ChessVariant.default)
      val variantHandlers  = VariantHandlers()
      import variantHandlers._

      Root(
        ply = r.int(F.ply),
        turnCount = r.intO(F.turnCount).getOrElse(r.int(F.ply)),
        playedPlayerIndex =
          r.getO[PlayerIndex](F.playedPlayerIndex).getOrElse(PlayerIndex.apply(r.int(F.ply) % 2 == 1)),
        variant = variant,
        fen = r.get[FEN](F.fen),
        check = r boolD F.check,
        shapes = r.getO[Shapes](F.shapes) | Shapes.empty,
        comments = r.getO[Comments](F.comments) | Comments.empty,
        gamebook = r.getO[Gamebook](F.gamebook),
        glyphs = r.getO[Glyphs](F.glyphs) | Glyphs.empty,
        score = r.getO[Score](F.score),
        clock = r.getO[Centis](F.clock),
        pocketData = r.getO[PocketData](F.crazy),
        children = StudyFlatTree.reader.rootChildren(fullReader.doc)
      )
    }
    def writes(w: Writer, r: Root) = $doc(
      StudyFlatTree.writer.rootChildren(r) appended {
        val variantHandlers = VariantHandlers()(r.variant)
        import variantHandlers._
        Path.rootDbKey -> $doc(
          F.ply               -> r.ply,
          F.playedPlayerIndex -> r.playedPlayerIndex,
          F.variant           -> r.variant,
          F.fen               -> r.fen,
          F.check             -> r.check.some.filter(identity),
          F.shapes            -> r.shapes.value.nonEmpty.option(r.shapes),
          F.comments          -> r.comments.value.nonEmpty.option(r.comments),
          F.gamebook          -> r.gamebook,
          F.glyphs            -> r.glyphs.nonEmpty,
          F.score             -> r.score,
          F.clock             -> r.clock,
          F.crazy             -> r.pocketData
        )
      }
    )
  }

  def variantChapterPreview(
      doc: Bdoc,
      id: Chapter.Id,
      variant: Variant
  ): Option[StudyMultiBoard.ChapterPreview] = {
    val variantHandlers = VariantHandlers()(variant)
    import variantHandlers._
    for {
      name <- doc.getAsOpt[Chapter.Name]("name")
      comp <- doc.getAsOpt[Bdoc]("comp")
      node <- comp.getAsOpt[Bdoc]("node")
      fen  <- node.getAsOpt[FEN]("fen")
      lastMove = node.getAsOpt[Uci]("uci")
      tags     = comp.getAsOpt[Tags]("tags")
    } yield StudyMultiBoard.ChapterPreview(
      id = id,
      name = name,
      players = tags flatMap StudyMultiBoard.ChapterPreview.players,
      orientation = doc.getAsOpt[PlayerIndex]("orientation") | PlayerIndex.P1,
      fen = fen,
      lastMove = lastMove,
      playing = lastMove.isDefined && tags.flatMap(_(_.Result)).has("*")
    )
  }

  def chapterPreview(doc: Bdoc): Option[StudyMultiBoard.ChapterPreview] = {
    for {
      id      <- doc.getAsOpt[Chapter.Id]("_id")
      variant <- doc.getAsOpt[Variant]("variant")
      preview <- variantChapterPreview(doc, id, variant)
    } yield preview
  }
}
