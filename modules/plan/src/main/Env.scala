package lila.plan

import com.softwaremill.macwire._
import io.methvin.play.autoconfig._
import com.softwaremill.tagging._
import play.api.Configuration
import play.api.libs.ws.StandaloneWSClient
import scala.concurrent.duration._

import lila.common.config._

@Module
private class PlanConfig(
    @ConfigName("collection.patron") val patronColl: CollName,
    @ConfigName("collection.charge") val chargeColl: CollName,
    val stripe: StripeClient.Config,
    val payPal: PayPalClient.Config,
    @ConfigName("payPal.ipn_key") val payPalIpnKey: Secret
)

final class Env(
    appConfig: Configuration,
    db: lila.db.Db,
    ws: StandaloneWSClient,
    timeline: lila.hub.actors.Timeline,
    cacheApi: lila.memo.CacheApi,
    mongoCache: lila.memo.MongoCache.Api,
    lightUserApi: lila.user.LightUserApi,
    userRepo: lila.user.UserRepo,
    settingStore: lila.memo.SettingStore.Builder
)(implicit
    ec: scala.concurrent.ExecutionContext,
    system: akka.actor.ActorSystem
) {

  private val config = appConfig.get[PlanConfig]("plan")(AutoConfig.loader)

  val stripePublicKey = config.stripe.publicKey
  val payPalPublicKey = config.payPal.publicKey

  val payPalCheckoutSetting = settingStore[Boolean](
    "payPalCheckout",
    default = false,
    text = "Use paypal checkout".some
  )

  private lazy val patronColl = db(config.patronColl).taggedWith[PatronColl]
  private lazy val chargeColl = db(config.chargeColl).taggedWith[ChargeColl]

  private lazy val stripeClient: StripeClient = wire[StripeClient]

  private lazy val payPalClient: PayPalClient = wire[PayPalClient]

  private lazy val notifier: PlanNotifier = wire[PlanNotifier]

  private lazy val monthlyGoalApi = new MonthlyGoalApi(
    getGoal = () => Usd(donationGoalSetting.get()),
    chargeColl = chargeColl
  )

  val donationGoalSetting = settingStore[Int](
    "donationGoal",
    default = 0,
    text = "Monthly donation goal in USD from https://playstrategy.org/costs".some
  )

  lazy val api: PlanApi = wire[PlanApi]

  lazy val webhook = wire[PlanWebhook]

  private lazy val expiration = new Expiration(
    userRepo,
    patronColl,
    notifier
  )

  system.scheduler.scheduleWithFixedDelay(15 minutes, 15 minutes) { () =>
    expiration.run.unit
  }

  lila.common.Bus.subscribeFun("email") { case lila.hub.actorApi.user.ChangeEmail(userId, email) =>
    api.onEmailChange(userId, email).unit
  }

  def cli =
    new lila.common.Cli {
      def process = {
        case "patron" :: "lifetime" :: user :: Nil =>
          userRepo named user flatMap { _ ?? api.setLifetime } inject "ok"
        case "patron" :: "month" :: user :: Nil =>
          userRepo named user flatMap { _ ?? api.giveMonth } inject "ok"
        case "patron" :: "remove" :: user :: Nil =>
          userRepo named user flatMap { _ ?? api.remove } inject "ok"
      }
    }
}

private trait PatronColl
private trait ChargeColl
