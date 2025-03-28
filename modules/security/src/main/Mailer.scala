package lila.security

import akka.actor.ActorSystem
import io.methvin.play.autoconfig._
import play.api.i18n.Lang
import play.api.libs.mailer.{ Email, SMTPConfiguration, SMTPMailer }
import scala.concurrent.duration.{ span => _, _ }
import scala.concurrent.{ blocking, Future }
import scalatags.Text.all.{ html => htmlTag, _ }
import scalatags.Text.tags2.{ title => titleTag }

import lila.common.String.html.{ nl2br }
import lila.common.{ Chronometer, EmailAddress, ThreadLocalRandom }
import lila.i18n.I18nKeys.{ emails => trans }
import play.api.ConfigLoader
import akka.dispatch.MessageDispatcher

final class Mailer(
    config: Mailer.Config,
    getSecondaryPermille: () => Int
)(implicit system: ActorSystem) {

  implicit private val blockingExecutionContext: MessageDispatcher =
    system.dispatchers.lookup("blocking-smtp-dispatcher")

  private val primaryClient   = new SMTPMailer(config.primary.toClientConfig)
  private val secondaryClient = new SMTPMailer(config.secondary.toClientConfig)

  private def randomClient(): (SMTPMailer, Mailer.Smtp) =
    if (ThreadLocalRandom.nextInt(1000) < getSecondaryPermille()) (secondaryClient, config.secondary)
    else (primaryClient, config.primary)

  def send(msg: Mailer.Message): Funit =
    if (msg.to.isNoReply) {
      logger.warn(s"Can't send ${msg.subject} to noreply email ${msg.to}")
      funit
    } else
      Future {
        Chronometer.syncMon(_.email.send.time) {
          blocking {
            val (client, config) = randomClient()
            client
              .send(
                Email(
                  subject = msg.subject,
                  from = config.sender,
                  to = Seq(msg.to.value),
                  bodyText = msg.text.some,
                  bodyHtml = msg.htmlBody map { body => Mailer.html.wrap(msg.subject, body).render }
                )
              )
              .unit
          }
        }
      }
}

object Mailer {

  private val timeout = 5 seconds

  case class Smtp(
      mock: Boolean,
      host: String,
      port: Int,
      tls: Boolean,
      user: String,
      sender: String,
      password: String
  ) {
    def toClientConfig = SMTPConfiguration(
      host = host,
      port = port,
      tlsRequired = tls,
      user = user.some,
      password = password.some,
      mock = mock,
      timeout = Mailer.timeout.toMillis.toInt.some
    )
  }
  implicit val smtpLoader: ConfigLoader[Smtp] = AutoConfig.loader[Smtp]

  case class Config(
      primary: Smtp,
      secondary: Smtp
  )
  implicit val configLoader: ConfigLoader[Config] = AutoConfig.loader[Config]

  case class Message(
      to: EmailAddress,
      subject: String,
      text: String,
      htmlBody: Option[Frag] = none
  )

  object txt {

    def serviceNote(implicit lang: Lang): String = s"""
${trans.common_note("https://playstrategy.org").render}

${trans.common_contact("https://playstrategy.org/contact").render}"""

    def addServiceNote(body: String)(implicit lang: Lang) = s"""$body

$serviceNote"""
  }

  object html {

    private val itemscope = attr("itemscope").empty
    private val itemtype  = attr("itemtype")
    private val itemprop  = attr("itemprop")

    val emailMessage = div(itemscope, itemtype := "http://schema.org/EmailMessage")
    val pDesc        = p(itemprop := "description")
    val potentialAction =
      div(itemprop := "potentialAction", itemscope, itemtype := "http://schema.org/ViewAction")
    def metaName(cont: String) = meta(itemprop := "name", content := cont)
    val publisher              = div(itemprop := "publisher", itemscope, itemtype := "http://schema.org/Organization")
    val noteContact = a(itemprop := "url", href := "https://playstrategy.org/contact")(
      span(itemprop := "name")("playstrategy.org/contact")
    )

    def serviceNote(implicit lang: Lang) =
      publisher(
        small(
          trans.common_note(Mailer.html.noteLink),
          " ",
          trans.common_contact(noteContact),
          " ",
          lila.i18n.I18nKeys.readAboutOur(
            a(href := "https://playstrategy.org/privacy")(
              lila.i18n.I18nKeys.privacyPolicy()
            )
          )
        )
      )

    def standardEmail(body: String): Frag =
      emailMessage(
        pDesc(nl2br(body)),
        publisher
      )

    val noteLink = a(
      itemprop := "url",
      href := "https://playstrategy.org/"
    )(span(itemprop := "name")("playstrategy.org"))

    def url(u: String)(implicit lang: Lang) =
      frag(
        meta(itemprop := "url", content := u),
        p(a(itemprop := "target", href := u)(u)),
        p(trans.common_orPaste(lang))
      )

    private[Mailer] def wrap(subject: String, htmlBody: Frag): Frag =
      frag(
        raw("<!doctype html>"),
        htmlTag(
          head(
            meta(httpEquiv := "Content-Type", content := "text/html; charset=utf-8"),
            meta(name := "viewport", content := "width=device-width"),
            titleTag(subject)
          ),
          body(htmlBody)
        )
      )
  }
}
