/*
 * Copyright 2020 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package services

import com.google.inject.Inject
import config.FrontendAppConfig
import connectors.AFTConnector
import connectors.cache.UserAnswersCacheConnector
import models.LocalDateBinder._
import models.{Quarters, CommonQuarters, DisplayQuarter, InProgressHint, LockedHint, Quarter, SubmittedHint}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

class QuartersService @Inject()(
                                config: FrontendAppConfig,
                                 aftConnector: AFTConnector,
                                userAnswersCacheConnector: UserAnswersCacheConnector
                               ) extends CommonQuarters {

  def getPastQuarters(pstr: String, year: Int)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Seq[DisplayQuarter]] = {
    aftConnector.getAftOverview(pstr).map { aftOverview =>
      if (aftOverview.nonEmpty) {

        aftOverview
          .filter(_.periodStartDate.getYear == year)
          .filter(_.submittedVersionAvailable)
          .map { overviewElement =>
          val quarter = Quarters.getQuarter(overviewElement.periodStartDate)
          val displayYear = aftOverview.filter(_.periodStartDate.getYear != year).distinct.isEmpty
            DisplayQuarter(quarter, displayYear, None, None)
        }
      } else {
        Nil
      }
    }
  }

  def getInProgressQuarters(srn: String, pstr: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Seq[DisplayQuarter]] = {
    aftConnector.getAftOverview(pstr).flatMap { aftOverview =>
      if (aftOverview.nonEmpty) {

        val displayQuarters: Seq[Future[Seq[DisplayQuarter]]] =
          aftOverview
            .filter(_.compiledVersionAvailable)
            .map { overviewElement =>

              val quarter: Quarter = Quarters.getQuarter(overviewElement.periodStartDate)
              userAnswersCacheConnector.lockedBy(srn, overviewElement.periodStartDate).flatMap {
                case Some(lockingPsa) =>
                  Future.successful(Seq(DisplayQuarter(quarter, displayYear = true, Some(lockingPsa), Some(LockedHint))))
                case None =>
                  if(overviewElement.numberOfVersions == 1) {
                    aftConnector.getIsAftNonZero(pstr, overviewElement.periodStartDate.toString, "1").map {
                      case true => Seq(DisplayQuarter(quarter, displayYear = true, None, Some(InProgressHint)))
                      case _ => Nil
                    }
                  } else {
                    Future.successful(Seq(DisplayQuarter(quarter, displayYear = true, None, Some(InProgressHint))))
                  }
              }
            }

        Future.sequence(displayQuarters).map(_.flatten)
      } else {
        Future.successful(Nil)
      }
    }
  }

  def getStartQuarters(srn: String, pstr: String, year: Int)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Seq[DisplayQuarter]] = {
    aftConnector.getAftOverview(pstr).flatMap { aftOverview =>
      if (aftOverview.nonEmpty) {

        val displayQuarters =  availableQuarters(year)(config).map { x =>
            val availableQuarter = getQuarter(x, year)

              userAnswersCacheConnector.lockedBy(srn, availableQuarter.startDate).map {
                case Some(lockingPsa) => DisplayQuarter(availableQuarter, displayYear = false, Some(lockingPsa), Some(LockedHint))
                case _ =>
                  val overviewElementForAvailableQuarter = aftOverview.filter(_.periodStartDate == availableQuarter.startDate)
                  if (overviewElementForAvailableQuarter.nonEmpty) {
                    if(overviewElementForAvailableQuarter.head.submittedVersionAvailable) {
                      DisplayQuarter(availableQuarter, displayYear = false, None, Some(SubmittedHint))
                    } else {
                      DisplayQuarter(availableQuarter, displayYear = false, None, Some(InProgressHint))
                    }
                  } else {
                    DisplayQuarter(availableQuarter, displayYear = false, None, None)
                  }
              }
          }
        Future.sequence(displayQuarters)
      } else {
        val displayQuarters =  availableQuarters(year)(config).map { x =>
          val availableQuarter = getQuarter(x, year)
          DisplayQuarter(availableQuarter, displayYear = false, None, None)
        }
        Future.successful(displayQuarters)
      }
    }
  }


}
