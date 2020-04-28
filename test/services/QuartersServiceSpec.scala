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

import java.time.LocalDate

import base.SpecBase
import connectors.AFTConnector
import connectors.cache.UserAnswersCacheConnector
import data.SampleData._
import models.{AFTOverview, DisplayQuarter, InProgressHint, LockedHint, SubmittedHint}
import org.mockito.Matchers
import org.mockito.Matchers.any
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.mockito.MockitoSugar
import play.api.mvc.Results
import utils.DateHelper

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class QuartersServiceSpec extends SpecBase with ScalaFutures with BeforeAndAfterEach with MockitoSugar with Results {

  import QuartersServiceSpec._

  private val mockAftConnector: AFTConnector = mock[AFTConnector]

  private val mockUserAnswersConnector: UserAnswersCacheConnector = mock[UserAnswersCacheConnector]

  private val quartersService = new QuartersService(frontendAppConfig, mockAftConnector, mockUserAnswersConnector)

  override def beforeEach(): Unit = {
    reset(mockAftConnector, mockUserAnswersConnector)
    when(mockAftConnector.getAftOverview(any())(any(), any())).thenReturn(Future.successful(Nil))
    when(mockUserAnswersConnector.lockedBy(any(), any())(any(), any())).thenReturn(Future.successful(None))
  }

  "getPastQuarters" must {
    "give all quarters for a given year for which return has been submitted when all returns are for a single year" in {
      when(mockAftConnector.getAftOverview(any())(any(), any())).thenReturn(Future.successful(pastOneYear))
      whenReady(quartersService.getPastQuarters(pstr, year2020)) { result =>
        result mustBe Seq(
          DisplayQuarter(q22020, displayYear = true, None, None),
          DisplayQuarter(q42020, displayYear = true, None, None)
        )
      }
    }

    "give all quarters for a given year for which return has been submitted when returns are for multiple years" in {
      when(mockAftConnector.getAftOverview(any())(any(), any())).thenReturn(Future.successful(pastMultipleYears))
      whenReady(quartersService.getPastQuarters(pstr, year2020)) { result =>
        result mustBe Seq(
          DisplayQuarter(q22020, displayYear = false, None, None),
          DisplayQuarter(q42020, displayYear = false, None, None)
        )
      }
    }

    "give an empty list if overview api returns an empty list" in {
      whenReady(quartersService.getPastQuarters(pstr, year2020)) { result =>
        result mustBe Nil
      }
    }
  }

  "getInProgressQuarters" must {
    "give all quarters which are currently in progress when one of them is locked and one is unlocked and" +
      "skip quarters (q1 2021) which are in their first compile and zeroed out" in {
      when(mockAftConnector.getAftOverview(any())(any(), any())).thenReturn(Future.successful(inProgress))
      when(mockAftConnector.getIsAftNonZero(any(), Matchers.eq(q12021.startDate.toString), any())(any(), any()))
        .thenReturn(Future.successful(false))
      when(mockUserAnswersConnector.lockedBy(any(), Matchers.eq(q32020.startDate.toString))(any(), any()))
        .thenReturn(Future.successful(Some(psaName)))
      whenReady(quartersService.getInProgressQuarters(srn, pstr)) { result =>
        result mustBe Seq(
          DisplayQuarter(q32020, displayYear = true, Some(psaName), Some(LockedHint)),
          DisplayQuarter(q42020, displayYear = true, None, Some(InProgressHint))
        )
      }
    }

    "give an empty list if overview api returns an empty list" in {
      whenReady(quartersService.getInProgressQuarters(srn, pstr)) { result =>
        result mustBe Nil
      }
    }
  }

  "getStartQuarters" must {
    "give all quarters which are not started yet and the ones currently in progress when one of them is locked and one is unlocked" in {
      when(mockAftConnector.getAftOverview(any())(any(), any())).thenReturn(Future.successful(startQuartersInProgress))
      when(mockUserAnswersConnector.lockedBy(any(), Matchers.eq(q32020.startDate.toString))(any(), any()))
        .thenReturn(Future.successful(Some(psaName)))
      DateHelper.setDate(Some(newDate))
      whenReady(quartersService.getStartQuarters(srn, pstr, year2020)) { result =>
        result mustBe Seq(
          DisplayQuarter(q22020, displayYear = false, None, None),
          DisplayQuarter(q32020, displayYear = false, Some(psaName), Some(LockedHint)),
          DisplayQuarter(q42020, displayYear = false, None, Some(InProgressHint))
        )
      }
    }

    "give all quarters submitted in the past" in {
      when(mockAftConnector.getAftOverview(any())(any(), any())).thenReturn(Future.successful(startQuartersCurrentAndPast))
      whenReady(quartersService.getStartQuarters(srn, pstr, year2020)) { result =>
        result mustBe Seq(
          DisplayQuarter(q22020, displayYear = false, None, Some(SubmittedHint)),
          DisplayQuarter(q32020, displayYear = false, None, None),
          DisplayQuarter(q42020, displayYear = false, None, Some(InProgressHint))
        )
      }
    }

    "give display all available quarters to start if overview api returns an empty list" in {
      whenReady(quartersService.getStartQuarters(srn, pstr, year2020)) { result =>
        result mustBe Seq(
          DisplayQuarter(q22020, displayYear = false, None, None),
          DisplayQuarter(q32020, displayYear = false, None, None),
          DisplayQuarter(q42020, displayYear = false, None, None)
        )
      }
    }
  }

}

object QuartersServiceSpec {

  val pstr: String = "pstr"
  val psaName: String = "Psa Name"
  val year2020: Int = 2020
  val newDate: LocalDate = LocalDate.of(2021,4,1)

  val pastOneYear: Seq[AFTOverview] = Seq(
    AFTOverview(q22020.startDate, q22020.endDate, 1, submittedVersionAvailable = true, compiledVersionAvailable = false),
    AFTOverview(q32020.startDate, q32020.endDate, 2, submittedVersionAvailable = false, compiledVersionAvailable = true),
    AFTOverview(q42020.startDate, q42020.endDate, 3, submittedVersionAvailable = true, compiledVersionAvailable = true)
  )

  val pastMultipleYears: Seq[AFTOverview] =
    pastOneYear ++
    Seq(
    AFTOverview(q12021.startDate, q12021.endDate, 1, submittedVersionAvailable = true, compiledVersionAvailable = false)
  )

  val inProgress: Seq[AFTOverview] = Seq(
    AFTOverview(q22020.startDate, q22020.endDate, 1, submittedVersionAvailable = true, compiledVersionAvailable = false),
    AFTOverview(q32020.startDate, q32020.endDate, 2, submittedVersionAvailable = true, compiledVersionAvailable = true),
    AFTOverview(q42020.startDate, q42020.endDate, 3, submittedVersionAvailable = false, compiledVersionAvailable = true),
    AFTOverview(q12021.startDate, q12021.endDate, 1, submittedVersionAvailable = false, compiledVersionAvailable = true)
  )

  val startQuartersInProgress: Seq[AFTOverview] = Seq(
    AFTOverview(q32020.startDate, q32020.endDate, 2, submittedVersionAvailable = true, compiledVersionAvailable = true),
    AFTOverview(q42020.startDate, q42020.endDate, 3, submittedVersionAvailable = false, compiledVersionAvailable = true)
  )

  val startQuartersCurrentAndPast: Seq[AFTOverview] = Seq(
    AFTOverview(q22020.startDate, q22020.endDate, 1, submittedVersionAvailable = true, compiledVersionAvailable = false),
    AFTOverview(q42020.startDate, q42020.endDate, 3, submittedVersionAvailable = false, compiledVersionAvailable = true))

}
