/*
 * Copyright 2024 HM Revenue & Customs
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
import models.{AFTQuarter, CommonQuarters, DisplayQuarter, InProgressHint, LockedHint, Quarters, SubmittedHint}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

class QuartersService @Inject()(
                                 config: FrontendAppConfig,
                                 aftConnector: AFTConnector,
                                 userAnswersCacheConnector: UserAnswersCacheConnector
                               ) extends CommonQuarters {

  def getPastQuarters(pstr: String, year: Int, srn: String, isLoggedInAsPsa: Boolean)
                     (implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Seq[DisplayQuarter]] = {
    aftConnector.getAftOverview(pstr, srn, isLoggedInAsPsa).map { aftOverview =>
      if (aftOverview.nonEmpty) {

        aftOverview
          .filter(_.periodStartDate.getYear == year)
          .filter(_.versionDetails.isDefined)
          .map(_.toPodsReport)
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

  def getPastYears(pstr: String, srn: String, isLoggedInAsPsa: Boolean)
    (implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Seq[Int]] = {
    aftConnector.getAftOverview(pstr, srn, isLoggedInAsPsa).map (
      _.filter(_.versionDetails.isDefined)
        .map(_.toPodsReport).filter(_.submittedVersionAvailable)
        .map(overviewElement => Quarters.getQuarter(overviewElement.periodStartDate).startDate.getYear)
        .distinct
    )
  }

  def getInProgressQuarters(srn: String, pstr: String, isLoggedInAsPsa: Boolean)
                           (implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Seq[DisplayQuarter]] = {
    aftConnector.getAftOverview(pstr, srn, isLoggedInAsPsa).flatMap { aftOverview =>
      if (aftOverview.nonEmpty) {

        val displayQuarters: Seq[Future[Seq[DisplayQuarter]]] =
          aftOverview
            .filter(_.versionDetails.isDefined)
            .map(_.toPodsReport)
            .filter(_.compiledVersionAvailable)
            .map { overviewElement =>

              val quarter: AFTQuarter = Quarters.getQuarter(overviewElement.periodStartDate)
              userAnswersCacheConnector.lockDetail(srn, overviewElement.periodStartDate).flatMap {
                case Some(lockDetail) =>
                  Future.successful(Seq(DisplayQuarter(
                    quarter, displayYear = true, Some(lockDetail.name), Some(LockedHint)
                  )))
                case None =>
                  if (overviewElement.numberOfVersions == 1) {
                    aftConnector.getIsAftNonZero(
                      pstr,
                      overviewElement.periodStartDate.toString,
                      "1",
                      srn,
                      isLoggedInAsPsa
                    ).map {
                      case true =>
                        Seq(DisplayQuarter(
                          quarter, displayYear = true, None, Some(InProgressHint)
                        ))
                      case _ =>
                        Nil
                    }
                  } else {
                    Future.successful(Seq(DisplayQuarter(
                      quarter, displayYear = true, None, Some(InProgressHint)
                    )))
                  }
              }
            }

        Future.sequence(displayQuarters).map(_.flatten)
      } else {
        Future.successful(Nil)
      }
    }
  }

  def getStartQuarters(srn: String, pstr: String, year: Int, isLoggedInAsPsa: Boolean)
                      (implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Seq[DisplayQuarter]] = {
    aftConnector.getAftOverview(pstr, srn, isLoggedInAsPsa).flatMap { aftOverview =>
      if (aftOverview.nonEmpty) {

        val displayQuarters: Seq[Future[Seq[DisplayQuarter]]] = availableQuarters(year)(config).map { x => // Q1, Q2
          val availableQuarter = getQuarter(x, year)

            userAnswersCacheConnector.lockDetail(srn, availableQuarter.startDate).flatMap {
              case Some(lockDetail) =>
                Future.successful(Seq(DisplayQuarter(availableQuarter, displayYear = false, Some(lockDetail.name), Some(LockedHint))))
              case _ =>
                val podsReportsForQuarter = aftOverview
                  .filter(_.periodStartDate == availableQuarter.startDate)
                  .filter(_.versionDetails.isDefined).map(_.toPodsReport)

                if (podsReportsForQuarter.nonEmpty) {
                  if (podsReportsForQuarter.head.submittedVersionAvailable) {
                    Future.successful(Seq(DisplayQuarter(availableQuarter, displayYear = false, None, Some(SubmittedHint))))
                  } else {
                    aftConnector.getIsAftNonZero(pstr, podsReportsForQuarter.head.periodStartDate.toString, "1", srn, isLoggedInAsPsa).map {
                      case true =>
                        Seq(DisplayQuarter(availableQuarter, displayYear = false, None, Some(InProgressHint)))
                      case _ =>
                        Seq(DisplayQuarter(availableQuarter, displayYear = false, None, None))
                    }
                  }
                } else {
                  Future.successful(Seq(DisplayQuarter(availableQuarter, displayYear = false, None, None)))
                }
            }

        }
        Future.sequence(displayQuarters).map(_.flatten)
      } else {
        val displayQuarters = availableQuarters(year)(config).map { x =>
          val availableQuarter = getQuarter(x, year)
          DisplayQuarter(availableQuarter, displayYear = false, None, None)
        }
        Future.successful(displayQuarters)
      }
    }
  }


}
