package lila.lobby

import lila.common.ThreadLocalRandom.nextBoolean

sealed abstract class PlayerIndex(val name: String) {

  def resolve: strategygames.Player

  def unary_! : PlayerIndex

  def compatibleWith(c: PlayerIndex) = !c == this
}

object PlayerIndex {

  object P1 extends PlayerIndex("p1") {

    def resolve = strategygames.P1

    def unary_! = P2
  }

  object P2 extends PlayerIndex("p2") {

    def resolve = strategygames.P2

    def unary_! = P1
  }

  object Random extends PlayerIndex("random") {

    def resolve = strategygames.Player.fromP1(nextBoolean())

    def unary_! = this
  }

  def apply(name: String): Option[PlayerIndex] = all find (_.name == name)

  def orDefault(name: String) = apply(name) | default

  def orDefault(name: Option[String]) = name.flatMap(apply) | default

  val all = List(P1, P2, Random)

  val names = all map (_.name)

  val choices = names zip names

  val default = Random
}
