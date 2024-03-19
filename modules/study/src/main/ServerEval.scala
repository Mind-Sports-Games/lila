package lila.study

import strategygames.format.pgn.Glyphs
import strategygames.format.{ Forsyth, Uci, UciCharPair, UciDump }
import strategygames.variant.Variant
import strategygames.{ Division, Game, GameLogic, Player => PlayerIndex, Replay }
import play.api.libs.json._
import scala.concurrent.duration._

import lila.analyse.{ Analysis, Info }
import lila.hub.actorApi.fishnet.StudyChapterRequest
import lila.security.Granter
import lila.tree.Node.Comment
import lila.user.{ User, UserRepo }
import lila.{ tree => T }

object ServerEval {

  final class Requester(
      fishnet: lila.hub.actors.Fishnet,
      chapterRepo: ChapterRepo,
      userRepo: UserRepo
  )(implicit ec: scala.concurrent.ExecutionContext) {

    private val onceEvery = lila.memo.OnceEvery(5 minutes)

    def apply(study: Study, chapter: Chapter, userId: User.ID): Funit =
      chapter.serverEval.fold(true) { eval =>
        !eval.done && onceEvery(chapter.id.value)
      } ?? {
        val unlimitedFu =
          fuccess(userId == User.playstrategyId) >>| userRepo
            .byId(userId)
            .map(_.exists(Granter(_.Relay)))
        unlimitedFu flatMap { unlimited =>
          chapterRepo.startServerEval(chapter) >>- {
            fishnet ! StudyChapterRequest(
              studyId = study.id.value,
              chapterId = chapter.id.value,
              initialFen = chapter.root.fen.some,
              variant = chapter.setup.variant,
              moves = UciDump(
                lib = chapter.setup.variant.gameLogic,
                //TODO upgrade for multiaction but fine for now as ServerEval only handles single action games
                actionStrs = chapter.root.mainline.map(_.move.san).map(Vector(_)),
                initialFen = chapter.root.fen.some,
                variant = chapter.setup.variant,
                finalSquare = chapter.setup.variant.gameLogic match {
                  case GameLogic.Draughts() => true
                  case _                    => false
                }
              ).toOption
                .map(
                  //TODO upgrade for multiaction but fine for now as ServerEval only handles single action games
                  _.flatten.toList.flatMap(m =>
                    Uci.apply(
                      chapter.setup.variant.gameLogic,
                      chapter.setup.variant.gameFamily,
                      m
                    )
                  )
                ) | List.empty,
              userId = userId,
              unlimited = unlimited
            )
          }
        }
      }
  }

  final class Merger(
      sequencer: StudySequencer,
      socket: StudySocket,
      chapterRepo: ChapterRepo,
      divider: lila.game.Divider
  )(implicit ec: scala.concurrent.ExecutionContext) {

    def apply(analysis: Analysis, complete: Boolean): Funit =
      analysis.studyId.map(Study.Id.apply) ?? { studyId =>
        sequencer.sequenceStudyWithChapter(studyId, Chapter.Id(analysis.id)) {
          case Study.WithChapter(_, chapter) => {
            implicit val variant = chapter.root.variant
            (complete ?? chapterRepo.completeServerEval(chapter)) >> {
              lila.common.Future
                .fold(chapter.root.mainline.zip(analysis.infoAdvices).toList)(Path.root) {
                  case (path, (node, (info, advOpt))) =>
                    chapter.root.nodeAt(path).flatMap { parent =>
                      analysisLine(parent, chapter.setup.variant, info) map { subTree =>
                        parent.addChild(subTree) -> subTree
                      }
                    } ?? { case (newParent, subTree) =>
                      chapterRepo.addSubTree(subTree, newParent, path)(chapter)
                    } >> {
                      import BSONHandlers._
                      import Node.{ BsonFields => F }
                      ((info.eval.score.isDefined && node.score.isEmpty) || (advOpt.isDefined && !node.comments.hasPlayStrategyComment)) ??
                        chapterRepo
                          .setNodeValues(
                            chapter,
                            path + node,
                            List(
                              F.score -> info.eval.score
                                .ifTrue {
                                  node.score.isEmpty ||
                                  advOpt.isDefined && node.comments
                                    .findBy(Comment.Author.PlayStrategy)
                                    .isEmpty
                                }
                                .flatMap(EvalScoreBSONHandler.writeOpt),
                              F.comments -> advOpt
                                .map { adv =>
                                  node.comments + Comment(
                                    Comment.Id.make,
                                    Comment.Text(adv.makeComment(withEval = false, withBestMove = true)),
                                    Comment.Author.PlayStrategy
                                  )
                                }
                                .flatMap(CommentsBSONHandler.writeOpt),
                              F.glyphs -> advOpt
                                .map { adv =>
                                  node.glyphs merge Glyphs.fromList(List(adv.judgment.glyph))
                                }
                                .flatMap(GlyphsBSONHandler.writeOpt)
                            )
                          )
                    } inject path + node
                } void
            } >>- {
              chapterRepo.byId(Chapter.Id(analysis.id)).foreach {
                _ ?? { chapter =>
                  socket.onServerEval(
                    studyId,
                    ServerEval.Progress(
                      chapterId = chapter.id,
                      tree = lila.study.TreeBuilder(chapter.root, chapter.setup.variant),
                      analysis = toJson(chapter, analysis),
                      division = divisionOf(chapter)
                    )
                  )
                }
              }
            } logFailure logger
          }
        }
      }

    def divisionOf(chapter: Chapter) =
      divider(
        id = chapter.id.value,
        //TODO upgrade for multiaction
        actionStrs = chapter.root.mainline.map(_.move.san).toVector.map(Vector(_)),
        variant = chapter.setup.variant,
        initialFen = chapter.root.fen.some
      )

    private def analysisLine(root: RootOrNode, variant: Variant, info: Info): Option[Node] =
      Replay.gameWithUciWhileValid(
        variant.gameLogic,
        info.variation.take(20),
        //TODO: multiaction doublecheck: Think this is ok to handle like this
        PlayerIndex.P1,
        PlayerIndex.fromTurnCount(info.variation.take(20).size),
        root.fen,
        variant
      ) match {
        case (_, games, error) =>
          error foreach { logger.info(_) }
          games.reverse match {
            case Nil => none
            case (g, m) :: rest =>
              rest
                .foldLeft(makeBranch(g, m)) { case (node, (g, m)) =>
                  makeBranch(g, m) addChild node
                } some
          }
      }

    private def makeBranch(g: Game, m: Uci.WithSan) =
      Node(
        id = UciCharPair(g.situation.board.variant.gameLogic, m.uci),
        ply = g.plies,
        variant = g.situation.board.variant,
        move = m,
        fen = Forsyth.>>(g.situation.board.variant.gameLogic, g),
        check = g.situation.check,
        pocketData = g.situation.board.pocketData,
        clock = none,
        children = Node.emptyChildren,
        forceVariation = false
      )
  }

  case class Progress(chapterId: Chapter.Id, tree: T.Root, analysis: JsObject, division: Division)

  def toJson(chapter: Chapter, analysis: Analysis) =
    lila.analyse.JsonView.bothPlayers(
      lila.analyse.Accuracy.PovLike(PlayerIndex.P1, chapter.root.playerIndex, chapter.root.ply),
      analysis
    )
}
