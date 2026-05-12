package lila.insight

import play.api.i18n.Lang
import play.api.libs.json.*

final class JsonView {

  import lila.insight.{ Dimension as D, Metric as M }
  import writers.*

  case class Categ(name: String, items: List[JsValue])
  implicit private val categWrites: OWrites[Categ] = Json.writes[Categ]

  def ui(ecos: Set[String], asMod: Boolean)(implicit lang: Lang) = {

    val openingJson = Json.obj(
      "key"         -> D.Opening.key,
      "name"        -> D.Opening.name,
      "position"    -> D.Opening.position,
      "description" -> D.Opening.description.render,
      "values"      -> Dimension
        .valuesOf(D.Opening)
        .filter { o =>
          ecos contains o.eco
        }
        .map(Dimension.valueToJson(D.Opening))
    )

    val dimensionCategs = List(
      Categ(
        "Setup",
        List(
          dimensionToJson(D.Date),
          dimensionToJson(D.Period),
          dimensionToJson(D.Perf),
          dimensionToJson(D.PlayerIndex),
          dimensionToJson(D.OpponentStrength)
        )
      ),
      Categ(
        "Game",
        List(
          openingJson,
          dimensionToJson(D.MyCastling),
          dimensionToJson(D.OpCastling),
          dimensionToJson(D.QueenTrade)
        )
      ),
      Categ(
        "Move",
        List(
          dimensionToJson(D.PieceRole),
          dimensionToJson(D.MovetimeRange),
          dimensionToJson(D.MaterialRange),
          dimensionToJson(D.Phase),
          dimensionToJson(D.CplRange)
        ) ::: {
          if (asMod) List(dimensionToJson(D.Blur), dimensionToJson(D.TimeVariance))
          else Nil
        }
      ),
      Categ(
        "Result",
        List(
          dimensionToJson(D.Termination),
          dimensionToJson(D.Result)
        )
      )
    )

    val metricCategs = List(
      Categ(
        "Setup",
        List(
          Json.toJson(M.OpponentRating: Metric)
        )
      ),
      Categ(
        "Move",
        List(
          Json.toJson(M.Movetime: Metric),
          Json.toJson(M.PieceRole: Metric),
          Json.toJson(M.Material: Metric),
          Json.toJson(M.NbMoves: Metric)
        ) ++ {
          if (asMod)
            List(
              Json.toJson(M.Blurs: Metric),
              Json.toJson(M.TimeVariance: Metric)
            )
          else Nil
        }
      ),
      Categ(
        "Evaluation",
        List(
          Json.toJson(M.MeanCpl: Metric),
          Json.toJson(M.CplBucket: Metric),
          Json.toJson(M.Opportunism: Metric),
          Json.toJson(M.Luck: Metric)
        )
      ),
      Categ(
        "Result",
        List(
          Json.toJson(M.Termination: Metric),
          Json.toJson(M.Result: Metric),
          Json.toJson(M.RatingDiff: Metric)
        )
      )
    )

    Json.obj(
      "dimensionCategs" -> dimensionCategs,
      "metricCategs"    -> metricCategs,
      "presets"         -> { if (asMod) Preset.forMod else Preset.base }
    )
  }

  private def dimensionToJson[X](d: Dimension[X])(implicit lang: Lang): JsValue =
    Json.toJson(d)(using writers.dimensionWriter)

  private object writers {

    implicit def presetWriter[X]: Writes[Preset] =
      Writes { p =>
        Json.obj(
          "name"      -> p.name,
          "dimension" -> p.question.dimension.key,
          "metric"    -> p.question.metric.key,
          "filters"   -> JsObject(p.question.filters.map { case Filter(dimension, selected) =>
            dimension.key -> JsArray(selected.map(Dimension.valueKey(dimension)).map(JsString.apply))
          })
        )
      }

    implicit def dimensionWriter[X](implicit lang: Lang): Writes[Dimension[X]] =
      Writes { d =>
        Json.obj(
          "key"         -> d.key,
          "name"        -> d.name,
          "position"    -> d.position,
          "description" -> d.description.render,
          "values"      -> Dimension.valuesOf(d).map(Dimension.valueToJson(d))
        )
      }

    implicit val metricWriter: Writes[Metric] = Writes { m =>
      Json.obj(
        "key"         -> m.key,
        "name"        -> m.name,
        "description" -> m.description.render,
        "position"    -> m.position
      )
    }

    implicit val positionWriter: Writes[Position] = Writes { p =>
      JsString(p.name)
    }
  }

  object chart {
    implicit private val xAxisWrites: OWrites[Chart.Xaxis] = Json.writes[Chart.Xaxis]
    implicit private val yAxisWrites: OWrites[Chart.Yaxis] = Json.writes[Chart.Yaxis]
    implicit private val SerieWrites: OWrites[Chart.Serie] = Json.writes[Chart.Serie]
    implicit private val ChartWrites: OWrites[Chart]       = Json.writes[Chart]

    def apply(c: Chart) = ChartWrites writes c
  }

  def question(metric: String, dimension: String, filters: String) =
    Json.obj(
      "metric"    -> metric,
      "dimension" -> dimension,
      "filters"   -> (filters
        .split('/')
        .view
        .map(_ split ':')
        .collect { case Array(key, values) =>
          key -> JsArray(values.split(',').map(JsString.apply))
        }
        .toMap: Map[String, JsArray])
    )
}
