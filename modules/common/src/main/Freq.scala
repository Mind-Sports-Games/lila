package lila.common

// Scheduled tournament frequency.
sealed abstract class Freq(val id: Int, val importance: Int) extends Ordered[Freq] {

  val name    = toString.toLowerCase
  val display = toString

  def compare(other: Freq) = Integer.compare(importance, other.importance)

  def isDaily          = this == Freq.Daily
  def isDailyOrBetter  = this >= Freq.Daily
  def isWeeklyOrBetter = this >= Freq.Weekly
}
object Freq {
  // ─── ACTIVE ──────────────────────────────────────────────────────────────────
  // importance must stay >= Weekly (40): TournamentRepo.calendar selects on isWeeklyOrBetter.
  case object GroupCycle extends Freq(46, 45) { // scheduler (group cycle blocks)
    override val display = "Group Cycle"
  }
  case object Wildcard     extends Freq(47, 44) // scheduler (random variant in the spare hours)
  case object Shield       extends Freq(51, 51) // scheduler (monthly shields)
  case object MedleyShield extends Freq(52, 52) // scheduler (medley shields)
  case object Yearly       extends Freq(70, 70) // scheduler: routine 24h per-variant rotation (NOT the Annual birthday events)
  case object Unique       extends Freq(90, 59) // CRUD / featuring (admin one-offs)
  case object Annual       extends Freq(75, 80) // marquee once-a-year events: PlayStrategy birthday / end-of-year (distinct from Yearly); set manually
  case object Introductory extends Freq(80, 65) // set manually; used by history + winners
  case object MSOGP extends Freq(122, 75) { // set manually; used by history + auto-analyse
    override val display = "MSO Grand Prix"
  }

  // ─── LEGACY — DO NOT DELETE ───────────────────────────────────────────────────
  // No longer produced by any code path, but historical tournament/swiss documents
  // persist `schedule.freq` by name. Removing a case makes `Freq(name)` return None and
  // BREAKS deserialization of those documents (winners, history, shield tables).
  // `db.tournament2.distinct("schedule.freq")` and `db.swiss.distinct("schedule.freq")`.
  case object Hourly extends Freq(10, 10) // old auto-scheduler slot
  case object Daily  extends Freq(20, 20) // old auto-scheduler slot
  case object Weekly extends Freq(40, 40) // superseded by GroupCycle
  // Eastern is disabled (Lichess vestige: the "Daily" tournaments at Asian-timezone hours).
  // To re-enable: uncomment this case object, add it to `all` below, and uncomment the
  // Eastern logic in the tournament module (Schedule / TournamentScheduler / TournamentRepo).
  // case object Eastern extends Freq(30, 15)
  case object Weekend extends Freq(41, 41)
  case object Monthly extends Freq(50, 50)
  case object Marathon extends Freq(60, 60)
  case object ExperimentalMarathon extends Freq(61, 55) {
    override val display = "Experimental Marathon"
  }
  case object MedleyMarathon extends Freq(65, 70) {
    override val display = "Medley Marathon"
  }
  case object MSOWarmUp extends Freq(120, 41) {
    override val display = "MSO Warm-Up"
  }
  case object MSO21 extends Freq(121, 61) {
    override val display = "MSO 2021"
  }

  val all: List[Freq] = List(
    // active
    GroupCycle,
    Wildcard,
    Shield,
    MedleyShield,
    Yearly,
    Unique,
    Annual,
    Introductory,
    MSOGP,
    // legacy
    Hourly,
    Daily,
    Weekly,
    Weekend,
    Monthly,
    Marathon,
    ExperimentalMarathon,
    MedleyMarathon,
    MSOWarmUp,
    MSO21
  )
  val shields: List[Freq] = List(Shield, MedleyShield)

  val autoAnalyse: Set[Freq] = Set(Annual, Introductory, Shield, MedleyShield, MSOGP)

  def apply(name: String) = all.find(_.name == name)
  def byId(id: Int)       = all.find(_.id == id)
}
