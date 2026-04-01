package lila.study

import strategygames.format.UciCharPair

case class Path(ids: Vector[UciCharPair]) extends AnyVal {

  def head: Option[UciCharPair] = ids.headOption

  // def tail: Path = Path(ids drop 1)

  def parent: Path = Path(ids dropRight 1)

  def split: Option[(UciCharPair, Path)] = head.map(_ -> Path(ids.drop(1)))

  def isEmpty = ids.isEmpty

  def +(id: UciCharPair): Path = Path(ids appended id)
  def +(node: Node): Path      = Path(ids appended node.id)
  def +(more: Path): Path      = Path(ids appendedAll more.ids)

  def prepend(id: UciCharPair) = Path(ids prepended id)

  def intersect(other: Path): Path =
    Path {
      ids zip other.ids takeWhile { case (a, b) =>
        a == b
      } map (_._1)
    }

  def toDbField =
    if (ids.isEmpty) s"root.${Path.rootDbKey}"
    else s"root.${Path encodeDbKey this}"

  def depth = ids.size

  override def toString = ids.mkString
}

object Path {

  def apply(str: String): Path =
    Path {
      str
        .grouped(2)
        .flatMap { p =>
          p lift 1 map { b =>
            UciCharPair(p(0), b)
          }
        }
        .toVector
    }

  def fromDbKey(key: String): Path = apply(decodeDbKey(key))

  val root = Path("")

  // mongodb objects don't support empty keys
  val rootDbKey = "_"

  // mongodb objects don't support '.' and '$' in keys.
  // We escape to chars below charShift (35), which can never appear naturally in any UciCharPair encoding.
  // chars 144 and 145 are also escaped because they are valid position encodings (Grand Abalone k10/k11,
  // Go positions 109/110, etc.) and were previously used as the escape targets for '.' and '$'.
  // decodeDbKey handles both the new encoding and the original one (backward compat).
  def encodeDbKey(path: Path): String        = encodeDbKey(path.ids.mkString)
  def encodeDbKey(pair: UciCharPair): String = encodeDbKey(pair.toString)
  def encodeDbKey(pathStr: String): String   = pathStr.map {
    case '.'       => '\u0001' // '.' forbidden in MongoDB keys
    case '$'       => '\u0002' // '$' forbidden in MongoDB keys
    case '\u0090'  => '\u0003' // char(144): valid pos encoding (e.g. Grand Abalone k10, Go pos 109)
    case '\u0091'  => '\u0004' // char(145): valid pos encoding (e.g. Grand Abalone k11, Go pos 110)
    case c         => c
  }
  def decodeDbKey(key: String): String       = key.map {
    case '\u0001' => '.'
    case '\u0002' => '$'
    case '\u0003' => '\u0090'
    case '\u0004' => '\u0091'
    case '\u0090' => '.'  // backward compat: original encoding stored '.' as char(144)
    case '\u0091' => '$'  // backward compat: original encoding stored '$' as char(145)
    case c        => c
  }

  def isMainline(node: RootOrNode, path: Path): Boolean =
    path.split.fold(true) { case (id, rest) =>
      node.children.first ?? { child =>
        child.id == id && isMainline(child, rest)
      }
    }
}
