package lila.lobby

import lila.common.ThreadLocalRandom.nextBoolean

sealed abstract class SGPlayer(val name: String) {

  def resolve: strategygames.Player

  def unary_! : SGPlayer

  def compatibleWith(c: SGPlayer) = !c == this
}

object SGPlayer {

  object P1 extends SGPlayer("p1") {

    def resolve = strategygames.P1

    def unary_! = P2
  }

  object P2 extends SGPlayer("p2") {

    def resolve = strategygames.P2

    def unary_! = P1
  }

  object Random extends SGPlayer("random") {

    def resolve = strategygames.Player.fromP1(nextBoolean())

    def unary_! = this
  }

  def apply(name: String): Option[SGPlayer] = all find (_.name == name)

  def orDefault(name: String) = apply(name) | default

  def orDefault(name: Option[String]) = name.flatMap(apply) | default

  val all = List(P1, P2, Random)

  val names = all map (_.name)

  val choices = names zip names

  val default = Random
}
