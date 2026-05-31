package lila.analyse

import org.joda.time.DateTime

import lila.db.dsl.*

final class BackgammonAnalysisRepo(val coll: Coll)(implicit ec: scala.concurrent.ExecutionContext) {

  import BackgammonAnalysis.analysisHandler

  def byId(id: BackgammonAnalysis.ID): Fu[Option[BackgammonAnalysis]] =
    coll.byId[BackgammonAnalysis](id)

  def exists(id: String): Fu[Boolean] = coll.exists($id(id))

  def remove(id: String): Funit = coll.delete.one($id(id)).void

  /** Read-merge-write, so the worker can post decisions progressively (each post
    * adds/updates infos by index). Returns the merged analysis. */
  def merge(
      id: BackgammonAnalysis.ID,
      studyId: Option[String],
      more: List[BackgammonInfo],
      date: DateTime
  ): Fu[BackgammonAnalysis] =
    byId(id).flatMap { existing =>
      val merged = existing.getOrElse(BackgammonAnalysis.empty(id, studyId, date)).merge(more).copy(date = date)
      coll.update.one($id(id), merged, upsert = true).inject(merged)
    }
}
