package lila.push

import org.apache.pekko.actor._
import play.api.libs.json._
import scala.concurrent.duration._
import strategygames.Pos

import lila.challenge.Challenge
import lila.common.{ Future, LightUser }
import lila.game.{ Game, Namer, Pov }
import lila.hub.actorApi.map.Tell
import lila.hub.actorApi.round.{ IsOnGame, MoveEvent }
import lila.user.User
import lila.i18n.VariantKeys

final private class PushApi(
    firebasePush: FirebasePush,
    webPush: WebPush,
    userRepo: lila.user.UserRepo,
    implicit val lightUser: LightUser.Getter,
    proxyRepo: lila.round.GameProxyRepo,
    gameRepo: lila.game.GameRepo
)(implicit
    ec: scala.concurrent.ExecutionContext,
    scheduler: org.apache.pekko.actor.Scheduler
) {

  def finish(game: Game): Funit =
    if (!game.isCorrespondence || game.hasAi) funit
    else
      Future.sequence(game.userIds
        .map { userId =>
          Pov.ofUserId(game, userId) so { pov =>
            IfAway(pov) {
              gameRepo.countWhereUserTurn(userId) flatMap { nbMyTurn =>
                asyncOpponentName(pov) flatMap { opponent =>
                  pushToAll(
                    userId,
                    _.finish,
                    PushApi.Data(
                      title = pov.win match {
                        case Some(true)  => "You won!"
                        case Some(false) => "You lost."
                        case _           => "It's a draw."
                      },
                      body = s"Your game with $opponent is over.",
                      stacking = Stacking.GameFinish,
                      payload = Json.obj(
                        "userId" -> userId,
                        "userData" -> Json.obj(
                          "type"   -> "gameFinish",
                          "gameId" -> game.id,
                          "fullId" -> pov.fullId
                        )
                      ),
                      iosBadge = nbMyTurn.some.filter(0 <)
                    )
                  )
                }
              }
            }
          }
        })
        .void

  def move(move: MoveEvent): Funit =
    Future.delay(2 seconds) {
      proxyRepo.game(move.gameId) flatMap {
        _.filter(_.playable) so { game =>
          val pov = Pov(game, game.player.playerIndex)
          game.player.userId so { userId =>
            IfAway(pov) {
              gameRepo.countWhereUserTurn(userId) flatMap { nbMyTurn =>
                asyncOpponentName(pov) flatMap { opponent =>
                  game.actionStrs.filter(_.size > 0).map(_.mkString(",")).lastOption so { turn =>
                    pushToAll(
                      userId,
                      _.move,
                      PushApi.Data(
                        title = "It's your turn!",
                        body = s"$opponent played $turn",
                        stacking = Stacking.GameMove,
                        payload = Json.obj(
                          "userId"   -> userId,
                          "userData" -> corresGameJson(pov, "gameMove")
                        ),
                        iosBadge = nbMyTurn.some.filter(0 <)
                      )
                    )
                  }
                }
              }
            }
          }
        }
      }
    }

  def takebackOffer(gameId: Game.ID): Funit =
    Future.delay(1 seconds) {
      proxyRepo.game(gameId) flatMap {
        _.filter(_.playable).so { game =>
          game.players.collectFirst {
            case p if p.isProposingTakeback => Pov(game, game opponent p)
          } so { pov => // the pov of the receiver
            pov.player.userId so { userId =>
              IfAway(pov) {
                asyncOpponentName(pov) flatMap { opponent =>
                  pushToAll(
                    userId,
                    _.takeback,
                    PushApi.Data(
                      title = "Takeback offer",
                      body = s"$opponent proposes a takeback",
                      stacking = Stacking.GameTakebackOffer,
                      payload = Json.obj(
                        "userId"   -> userId,
                        "userData" -> corresGameJson(pov, "gameTakebackOffer")
                      )
                    )
                  )
                }
              }
            }
          }
        }
      }
    }

  def drawOffer(gameId: Game.ID): Funit =
    Future.delay(1 seconds) {
      proxyRepo.game(gameId) flatMap {
        _.filter(_.playable).so { game =>
          game.players.collectFirst {
            case p if p.isOfferingDraw => Pov(game, game opponent p)
          } so { pov => // the pov of the receiver
            pov.player.userId so { userId =>
              IfAway(pov) {
                asyncOpponentName(pov) flatMap { opponent =>
                  pushToAll(
                    userId,
                    _.takeback,
                    PushApi.Data(
                      title = "Draw offer",
                      body = s"$opponent offers a draw",
                      stacking = Stacking.GameDrawOffer,
                      payload = Json.obj(
                        "userId"   -> userId,
                        "userData" -> corresGameJson(pov, "gameDrawOffer")
                      )
                    )
                  )
                }
              }
            }
          }
        }
      }
    }

  def selectSquaresOffer(gameId: Game.ID): Funit =
    Future.delay(1 seconds) {
      proxyRepo.game(gameId) flatMap {
        _.filter(_.playable).so { game =>
          game.players.collectFirst {
            case p if p.isOfferingSelectSquares => Pov(game, game opponent p)
          } so { pov => // the pov of the receiver
            pov.player.userId so { userId =>
              IfAway(pov) {
                asyncOpponentName(pov) flatMap { opponent =>
                  pushToAll(
                    userId,
                    _.takeback,
                    PushApi.Data(
                      title = "Select square offer",
                      body = s"$opponent offers a selections of squares",
                      stacking = Stacking.GameSelectSquareOffer,
                      payload = Json.obj(
                        "userId"   -> userId,
                        "userData" -> corresGameJson(pov, "gameSelectSquareOffer")
                      )
                    )
                  )
                }
              }
            }
          }
        }
      }
    }

  def acceptSquaresOffer(gameId: Game.ID): Funit =
    Future.delay(1 seconds) {
      proxyRepo.game(gameId) flatMap {
        _.filter(_.playable).so { game =>
          game.players.collectFirst {
            case p if p.isOfferingSelectSquares => Pov(game, game opponent p)
          } so { pov => // the pov of the receiver
            pov.player.userId so { userId =>
              IfAway(pov) {
                asyncOpponentName(pov) flatMap { opponent =>
                  pushToAll(
                    userId,
                    _.takeback,
                    PushApi.Data(
                      title = "Square offer accepted",
                      body = s"$opponent accepted your offer of squares",
                      stacking = Stacking.GameSelectSquareOffer,
                      payload = Json.obj(
                        "userId"   -> userId,
                        "userData" -> corresGameJson(pov, "gameAcceptSquareOffer")
                      )
                    )
                  )
                }
              }
            }
          }
        }
      }
    }

  def declineSquaresOffer(gameId: Game.ID): Funit =
    Future.delay(1 seconds) {
      proxyRepo.game(gameId) flatMap {
        _.filter(_.playable).so { game =>
          game.players.collectFirst {
            case p if p.isOfferingSelectSquares => Pov(game, game opponent p)
          } so { pov => // the pov of the receiver
            pov.player.userId so { userId =>
              IfAway(pov) {
                asyncOpponentName(pov) flatMap { opponent =>
                  pushToAll(
                    userId,
                    _.takeback,
                    PushApi.Data(
                      title = "Square offer declined",
                      body = s"$opponent declined your offer of squares",
                      stacking = Stacking.GameSelectSquareOffer,
                      payload = Json.obj(
                        "userId"   -> userId,
                        "userData" -> corresGameJson(pov, "gameAcceptSquareOffer")
                      )
                    )
                  )
                }
              }
            }
          }
        }
      }
    }

  def corresAlarm(pov: Pov): Funit =
    pov.player.userId so { userId =>
      asyncOpponentName(pov) flatMap { opponent =>
        pushToAll(
          userId,
          _.corresAlarm,
          PushApi.Data(
            title = "Time is almost up!",
            body = s"You are about to lose on time against $opponent",
            stacking = Stacking.GameMove,
            payload = Json.obj(
              "userId"   -> userId,
              "userData" -> corresGameJson(pov, "corresAlarm")
            )
          )
        )
      }
    }

  private def corresGameJson(pov: Pov, typ: String) =
    Json.obj(
      "type"   -> typ,
      "gameId" -> pov.gameId,
      "fullId" -> pov.fullId
    )

  def newMsg(t: lila.msg.MsgThread): Funit =
    lightUser(t.lastMsg.user) flatMap {
      _ so { sender =>
        userRepo.isKid(t other sender) flatMap {
          !_ so {
            pushToAll(
              t other sender,
              _.message,
              PushApi.Data(
                title = sender.titleName,
                body = t.lastMsg.text take 140,
                stacking = Stacking.NewMessage,
                payload = Json.obj(
                  "userId" -> t.other(sender),
                  "userData" -> Json.obj(
                    "type"     -> "newMessage",
                    "threadId" -> sender.id
                  )
                )
              )
            )
          }
        }
      }
    }

  def challengeCreate(c: Challenge): Funit =
    c.destUser so { dest =>
      c.challengerUser.ifFalse(c.hasClock) so { challenger =>
        lightUser(challenger.id) flatMap {
          _ so { lightChallenger =>
            pushToAll(
              dest.id,
              _.challenge.create,
              PushApi.Data(
                title = s"${lightChallenger.titleName} (${challenger.rating.show}) challenges you!",
                body = describeChallenge(c),
                stacking = Stacking.ChallengeCreate,
                payload = Json.obj(
                  "userId" -> dest.id,
                  "userData" -> Json.obj(
                    "type"        -> "challengeCreate",
                    "challengeId" -> c.id
                  )
                )
              )
            )
          }
        }
      }
    }

  def challengeAccept(c: Challenge, joinerId: Option[String]): Funit =
    c.challengerUser.ifTrue(c.finalPlayerIndex.p1 && !c.hasClock) so { challenger =>
      joinerId so lightUser flatMap { lightJoiner =>
        pushToAll(
          challenger.id,
          _.challenge.accept,
          PushApi.Data(
            title = s"${lightJoiner.fold("Anonymous")(_.titleName)} accepts your challenge!",
            body = describeChallenge(c),
            stacking = Stacking.ChallengeAccept,
            payload = Json.obj(
              "userId" -> challenger.id,
              "userData" -> Json.obj(
                "type"        -> "challengeAccept",
                "challengeId" -> c.id
              )
            )
          )
        )
      }
    }

  private type MonitorType = lila.mon.push.send.type => ((String, Boolean) => Unit)

  private def pushToAll(userId: User.ID, monitor: MonitorType, data: PushApi.Data): Funit =
    webPush(userId, data).addEffects { res =>
      monitor(lila.mon.push.send)("web", res.isSuccess)
    } zip
      firebasePush(userId, data).addEffects { res =>
        monitor(lila.mon.push.send)("firebase", res.isSuccess)
      } void

  private def describeChallenge(c: Challenge) = {
    import lila.challenge.Challenge.TimeControl._
    List(
      c.mode.fold("Casual", "Rated"),
      c.timeControl match {
        case Unlimited         => "Unlimited"
        case Correspondence(d) => s"$d days"
        case c: Clock          => c.show
      },
      VariantKeys.variantName(c.variant)
    ) mkString " • "
  }

  private def IfAway(pov: Pov)(f: => Funit): Funit =
    lila.common.Bus.ask[Boolean]("roundSocket") { p =>
      Tell(pov.gameId, IsOnGame(pov.playerIndex, p))
    } flatMap {
      case true  => funit
      case false => f
    }

  private def asyncOpponentName(pov: Pov): Fu[String] = Namer playerText pov.opponent
}

private object PushApi {

  case class Data(
      title: String,
      body: String,
      stacking: Stacking,
      payload: JsObject,
      iosBadge: Option[Int] = None
  )
}
