package lila.analyse

import lila.db.dsl.*

final class BackgammonAnalysisRepo(val coll: Coll)(implicit ec: scala.concurrent.ExecutionContext) {

  import BackgammonAnalysis.analysisHandler

  def byId(id: BackgammonAnalysis.ID): Fu[Option[BackgammonAnalysis]] =
    coll.byId[BackgammonAnalysis](id)

  def byIds(ids: Iterable[BackgammonAnalysis.ID]): Fu[Map[BackgammonAnalysis.ID, BackgammonAnalysis]] =
    coll.idsMap[BackgammonAnalysis, BackgammonAnalysis.ID](
      ids,
      readPreference = reactivemongo.api.ReadPreference.secondaryPreferred
    )(_.id)

  def exists(id: String): Fu[Boolean] = coll.exists($id(id))

  def remove(id: String): Funit = coll.delete.one($id(id)).void

  def save(analysis: BackgammonAnalysis): Funit =
    coll.update.one($id(analysis.id), analysis, upsert = true).void
}
