package lila.plan

import play.api.libs.json._

final class PlanWebhook(api: PlanApi)(implicit ec: scala.concurrent.ExecutionContext) {

  import JsonHandlers._

  // Never trust an incoming webhook call.
  // Only read the Event ID from it,
  // then fetch the event from the stripe API.
  def stripe(js: JsValue): Funit = {
    def log = logger branch "stripe.webhook"
    (js \ "id").asOpt[String] ?? api.stripe.getEvent flatMap {
      case None =>
        log.warn(s"Forged webhook $js")
        funit
      case Some(event) =>
        import JsonHandlers.stripe._
        ~(for {
          id   <- (event \ "id").asOpt[String]
          name <- (event \ "type").asOpt[String]
          data <- (event \ "data" \ "object").asOpt[JsObject]
        } yield {
          lila.mon.plan.webhook("stripe", name).increment()
          log.debug(s"WebHook $name $id ${Json.stringify(data).take(100)}")
          name match {
            case "charge.succeeded" =>
              val charge = data.asOpt[StripeCharge] err s"Invalid charge $data"
              api.stripe.onCharge(charge)
            case "customer.subscription.deleted" =>
              val sub = data.asOpt[StripeSubscription] err s"Invalid subscription $data"
              api.stripe.onSubscriptionDeleted(sub)
            case "checkout.session.completed" =>
              val sub = data.asOpt[StripeCompletedSession] err s"Invalid session completed $data"
              api.stripe.onCompletedSession(sub)
            case _ => funit
          }
        })
    }
  }

  def payPal(js: JsValue): Funit = {
    def log = logger branch "payPal.webhook"
    import JsonHandlers.payPal._
    js.get[PayPalEventId]("id") ?? api.payPal.getEvent flatMap {
      case None =>
        log.warn(s"Forged event ${js str "id"} ${Json.stringify(js).take(2000)}")
        funit
      case Some(event) =>
        lila.mon.plan.webhook("payPal", event.tpe).increment()
        log.info(
          s"${event.tpe}: ${event.id} / ${event.resourceTpe}: ${event.resourceId} / ${Json.stringify(event.resource).take(2000)}"
        )
        event.tpe match {
          case "BILLING.SUBSCRIPTION.ACTIVATED" => funit
          case "BILLING.SUBSCRIPTION.CANCELLED" =>
            event.resourceId.map(PayPalSubscriptionId) ?? api.payPal.subscriptionUser flatMap {
              _ ?? api.cancel
            }
          case _ => funit
        }
    }
  }

}
