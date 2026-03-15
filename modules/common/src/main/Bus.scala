package lila.common

import scala.concurrent.duration.*
import scala.concurrent.Promise
import scala.jdk.CollectionConverters.*

import org.apache.pekko.actor.{ ActorRef, Scheduler }

import lila.common.extensions.*

object Bus:

  case class Event(payload: Any, channel: String)
  type Channel    = String
  type Subscriber = Tellable

  def publish(payload: Any, channel: Channel): Unit = bus.publish(payload, channel)

  def subscribe(subscriber: Tellable, to: Channel): Unit = bus.subscribe(subscriber, to)

  def subscribe(ref: ActorRef, to: Channel): Unit = bus.subscribe(Tellable.Actor(ref), to)

  def subscribe(subscriber: Tellable, to: Channel*): Unit = to.foreach(bus.subscribe(subscriber, _))
  def subscribe(ref: ActorRef, to: Channel*): Unit = to.foreach(bus.subscribe(Tellable.Actor(ref), _))
  def subscribe(ref: ActorRef, to: Iterable[Channel]): Unit = to.foreach(bus.subscribe(Tellable.Actor(ref), _))

  def subscribeFun(to: Channel*)(f: PartialFunction[Any, Unit]): Tellable =
    val t = lila.common.Tellable(f)
    subscribe(t, to*)
    t

  def subscribeFuns(subscriptions: (Channel, PartialFunction[Any, Unit])*): Unit =
    subscriptions.foreach: (channel, subscriber) =>
      subscribeFun(channel)(subscriber)

  def unsubscribe(subscriber: Tellable, from: Channel): Unit = bus.unsubscribe(subscriber, from)
  def unsubscribe(ref: ActorRef, from: Channel): Unit = bus.unsubscribe(Tellable.Actor(ref), from)

  def unsubscribe(subscriber: Tellable, from: Iterable[Channel]): Unit =
    from.foreach(bus.unsubscribe(subscriber, _))

  def unsubscribe(ref: ActorRef, from: Iterable[Channel]): Unit =
    from.foreach(bus.unsubscribe(Tellable.Actor(ref), _))

  def ask[A](channel: Channel, timeout: FiniteDuration = 2.second)(makeMsg: Promise[A] => Any)(using
      ec: Executor,
      scheduler: Scheduler
  ): Fu[A] =
    val promise = Promise[A]()
    val msg     = makeMsg(promise)
    publish(msg, channel)
    promise.future
      .withTimeout(timeout, s"Bus.ask timeout: $channel $msg")
      .monSuccess(_.bus.ask(s"${channel}_${msg.getClass}"))

  private val bus = new EventBus[Any, Channel, Tellable](
    initialCapacity = 65535,
    publish = (tellable, event) => tellable ! event
  )

  def keys = bus.keys
  def size = bus.size

  case class AskTimeout(message: String) extends lila.core.lilaism.LilaException

final private class EventBus[Event, Channel, Subscriber](
    initialCapacity: Int,
    publish: (Subscriber, Event) => Unit
):

  import java.util.concurrent.ConcurrentHashMap

  private val entries = new ConcurrentHashMap[Channel, Set[Subscriber]](initialCapacity)

  def subscribe(subscriber: Subscriber, channel: Channel): Unit =
    entries.compute(
      channel,
      (_: Channel, subs: Set[Subscriber]) =>
        Option(subs).fold(Set(subscriber))(_ + subscriber)
    )

  def unsubscribe(subscriber: Subscriber, channel: Channel): Unit =
    entries.computeIfPresent(
      channel,
      (_: Channel, subs: Set[Subscriber]) =>
        val newSubs = subs - subscriber
        if newSubs.isEmpty then null
        else newSubs
    )

  def publish(event: Event, channel: Channel): Unit =
    Option(entries.get(channel)).foreach:
      _.foreach(publish(_, event))

  def keys: Set[Channel]       = entries.keySet.asScala.toSet
  def size                     = entries.size
  def sizeOf(channel: Channel) = Option(entries.get(channel)).fold(0)(_.size)
