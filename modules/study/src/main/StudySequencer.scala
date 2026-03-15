package lila.study

import alleycats.Zero
import scala.concurrent.duration._

import lila.hub.DuctSequencers

final private class StudySequencer(
    studyRepo: StudyRepo,
    chapterRepo: ChapterRepo
)(implicit
    ec: scala.concurrent.ExecutionContext,
    scheduler: org.apache.pekko.actor.Scheduler,
    mode: play.api.Mode
) {

  private val workQueue =
    new DuctSequencers(maxSize = 64, expiration = 1 minute, timeout = 10 seconds, name = "study")

  def sequenceStudy[A: Zero](studyId: Study.Id)(f: Study => Fu[A]): Fu[A] =
    workQueue(studyId.value) {
      studyRepo.byId(studyId) flatMap {
        _ so { f(_) }
      }
    }

  def sequenceStudyWithChapter[A: Zero](studyId: Study.Id, chapterId: Chapter.Id)(
      f: Study.WithChapter => Fu[A]
  ): Fu[A] =
    sequenceStudy(studyId) { study =>
      chapterRepo
        .byId(chapterId)
        .flatMap {
          _.filter(_.studyId == studyId) so { chapter =>
            f(Study.WithChapter(study, chapter))
          }
        }
        .mon(_.study.sequencer.chapterTime)
    }
}
