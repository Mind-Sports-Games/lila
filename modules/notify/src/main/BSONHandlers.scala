package lila.notify

import strategygames.{ Player => PlayerIndex }
import lila.db.BSON.{ Reader, Writer }
import lila.db.dsl._
import lila.db.{ dsl, BSON }
import lila.notify.InvitedToStudy.{ InvitedBy, StudyId, StudyName }
import lila.notify.MentionedInThread._
import lila.notify.Notification._
import reactivemongo.api.bson._

private object BSONHandlers {

  implicit val NotifiesHandler: BSONHandler[Notifies] = stringAnyValHandler[Notifies](_.value, Notifies.apply)

  implicit val MentionByHandler: BSONHandler[MentionedBy] =
    stringAnyValHandler[MentionedBy](_.value, MentionedBy.apply)
  implicit val TopicHandler: BSONHandler[Topic]       = stringAnyValHandler[Topic](_.value, Topic.apply)
  implicit val TopicIdHandler: BSONHandler[TopicId]   = stringAnyValHandler[TopicId](_.value, TopicId.apply)
  implicit val CategoryHandler: BSONHandler[Category] = stringAnyValHandler[Category](_.value, Category.apply)
  implicit val PostIdHandler: BSONHandler[PostId]     = stringAnyValHandler[PostId](_.value, PostId.apply)

  implicit val InvitedToStudyByHandler: BSONHandler[InvitedBy] =
    stringAnyValHandler[InvitedBy](_.value, InvitedBy.apply)
  implicit val StudyNameHandler: BSONHandler[StudyName] =
    stringAnyValHandler[StudyName](_.value, StudyName.apply)
  implicit val StudyIdHandler: BSONHandler[StudyId] = stringAnyValHandler[StudyId](_.value, StudyId.apply)
  implicit val ReadHandler: BSONHandler[NotificationRead] =
    booleanAnyValHandler[NotificationRead](_.value, NotificationRead.apply)

  import PrivateMessage._
  implicit val PMSenderIdHandler: BSONHandler[Sender]                     = stringAnyValHandler[Sender](_.value, Sender.apply)
  implicit val PMTextHandler: BSONHandler[Text]                           = stringAnyValHandler[Text](_.value, Text.apply)
  implicit val PrivateMessageHandler: BSONDocumentHandler[PrivateMessage] = Macros.handler[PrivateMessage]

  implicit val TeamIdHandler: BSONHandler[TeamJoined.Id] =
    stringAnyValHandler[TeamJoined.Id](_.value, TeamJoined.Id.apply)
  implicit val TeamNameHandler: BSONHandler[TeamJoined.Name] =
    stringAnyValHandler[TeamJoined.Name](_.value, TeamJoined.Name.apply)
  implicit val TeamJoinedHandler: BSONDocumentHandler[TeamJoined] = Macros.handler[TeamJoined]

  implicit val GameEndGameIdHandler: BSONHandler[GameEnd.GameId] =
    stringAnyValHandler[GameEnd.GameId](_.value, GameEnd.GameId.apply)
  implicit val GameEndOpponentHandler: BSONHandler[GameEnd.OpponentId] =
    stringAnyValHandler[GameEnd.OpponentId](_.value, GameEnd.OpponentId.apply)
  implicit val GameEndWinHandler: BSONHandler[GameEnd.Win] =
    booleanAnyValHandler[GameEnd.Win](_.value, GameEnd.Win.apply)
  implicit val GameEndHandler: BSONDocumentHandler[GameEnd] = Macros.handler[GameEnd]

  implicit val TitledTournamentInvitationHandler: BSONDocumentHandler[TitledTournamentInvitation] =
    Macros.handler[TitledTournamentInvitation]

  implicit val PlanStartHandler: BSONDocumentHandler[PlanStart]   = Macros.handler[PlanStart]
  implicit val PlanExpireHandler: BSONDocumentHandler[PlanExpire] = Macros.handler[PlanExpire]

  implicit val RatingRefundHandler: BSONDocumentHandler[RatingRefund] = Macros.handler[RatingRefund]
  implicit val CorresAlarmHandler: BSONDocumentHandler[CorresAlarm]   = Macros.handler[CorresAlarm]
  implicit val IrwinDoneHandler: BSONDocumentHandler[IrwinDone]       = Macros.handler[IrwinDone]
  implicit val GenericLinkHandler: BSONDocumentHandler[GenericLink]   = Macros.handler[GenericLink]

  implicit val PlayerIndexBSONHandler: BSONHandler[PlayerIndex] =
    BSONBooleanHandler.as[PlayerIndex](PlayerIndex.fromP1, _.p1)

  implicit val NotificationContentHandler: BSON[NotificationContent] = new BSON[NotificationContent] {

    private def writeNotificationContent(notificationContent: NotificationContent) = {
      notificationContent match {
        case MentionedInThread(mentionedBy, topic, topicId, category, postId) =>
          $doc(
            "mentionedBy" -> mentionedBy,
            "topic"       -> topic,
            "topicId"     -> topicId,
            "category"    -> category,
            "postId"      -> postId
          )
        case InvitedToStudy(invitedBy, studyName, studyId) =>
          $doc("invitedBy" -> invitedBy, "studyName" -> studyName, "studyId" -> studyId)
        case p: PrivateMessage             => PrivateMessageHandler.writeTry(p).get
        case t: TeamJoined                 => TeamJoinedHandler.writeTry(t).get
        case x: TitledTournamentInvitation => TitledTournamentInvitationHandler.writeTry(x).get
        case x: GameEnd                    => GameEndHandler.writeTry(x).get
        case x: PlanStart                  => PlanStartHandler.writeTry(x).get
        case x: PlanExpire                 => PlanExpireHandler.writeTry(x).get
        case x: RatingRefund               => RatingRefundHandler.writeTry(x).get
        case ReportedBanned                => $empty
        case CoachReview                   => $empty
        case x: CorresAlarm                => CorresAlarmHandler.writeTry(x).get
        case x: IrwinDone                  => IrwinDoneHandler.writeTry(x).get
        case x: GenericLink                => GenericLinkHandler.writeTry(x).get
      }
    } ++ $doc("type" -> notificationContent.key)

    private def readMentionedNotification(reader: Reader): MentionedInThread = {
      val mentionedBy = reader.get[MentionedBy]("mentionedBy")
      val topic       = reader.get[Topic]("topic")
      val topicId     = reader.get[TopicId]("topicId")
      val category    = reader.get[Category]("category")
      val postNumber  = reader.get[PostId]("postId")

      MentionedInThread(mentionedBy, topic, topicId, category, postNumber)
    }

    private def readInvitedStudyNotification(reader: Reader): NotificationContent = {
      val invitedBy = reader.get[InvitedBy]("invitedBy")
      val studyName = reader.get[StudyName]("studyName")
      val studyId   = reader.get[StudyId]("studyId")

      InvitedToStudy(invitedBy, studyName, studyId)
    }

    def reads(reader: Reader): NotificationContent =
      reader.str("type") match {
        case "mention"        => readMentionedNotification(reader)
        case "invitedStudy"   => readInvitedStudyNotification(reader)
        case "privateMessage" => PrivateMessageHandler.readTry(reader.doc).get
        case "teamJoined"     => TeamJoinedHandler.readTry(reader.doc).get
        case "titledTourney"  => TitledTournamentInvitationHandler.readTry(reader.doc).get
        case "gameEnd"        => GameEndHandler.readTry(reader.doc).get
        case "planStart"      => PlanStartHandler.readTry(reader.doc).get
        case "planExpire"     => PlanExpireHandler.readTry(reader.doc).get
        case "ratingRefund"   => RatingRefundHandler.readTry(reader.doc).get
        case "reportedBanned" => ReportedBanned
        case "coachReview"    => CoachReview
        case "corresAlarm"    => CorresAlarmHandler.readTry(reader.doc).get
        case "irwinDone"      => IrwinDoneHandler.readTry(reader.doc).get
        case "genericLink"    => GenericLinkHandler.readTry(reader.doc).get
      }

    def writes(writer: Writer, n: NotificationContent): dsl.Bdoc = writeNotificationContent(n)
  }

  implicit val NotificationBSONHandler: BSONDocumentHandler[Notification] = Macros.handler[Notification]
}
