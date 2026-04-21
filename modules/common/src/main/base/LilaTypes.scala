package lila.base

// Re-export core types
export lila.core.lilaism.Lilaism.{ fuFalse, fuTrue, fuccess, fufail, funit, Fu, Funit }
export lila.core.lilaism.{ LilaException, LilaInvalid, LilaNoStackTrace, LilaTimeout }

// PlayStrategy-specific value classes
trait IntValue extends Any {
  def value: Int
  override def toString = value.toString
}

trait BooleanValue extends Any {
  def value: Boolean
  override def toString = value.toString
}

trait DoubleValue extends Any {
  def value: Double
  override def toString = value.toString
}

trait StringValue extends Any {
  def value: String
  override def toString = value
}
