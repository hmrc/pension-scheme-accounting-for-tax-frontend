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

package models

import java.time.LocalDate

import config.FrontendAppConfig
import org.mockito.Mockito.when
import org.scalatest.{BeforeAndAfterEach, MustMatchers, WordSpec}
import org.scalatestplus.mockito.MockitoSugar
import utils.DateHelper

class QuartersSpec extends WordSpec with MustMatchers with MockitoSugar with BeforeAndAfterEach {

  private val mockAppConfig: FrontendAppConfig = mock[FrontendAppConfig]

  override def beforeEach: Unit = {
    when(mockAppConfig.minimumYear).thenReturn(2019)
  }

  private val quarters = new CommonQuarters {}

  "Quarters" when {

    "on calling getCurrentYearQuarters" must {
      "return only Q1 if the current date falls in the Q1" in {
        DateHelper.setDate(Some(LocalDate.of(2020, 2, 12)))

        quarters.getCurrentYearQuarters(mockAppConfig) mustBe Seq(quarters.Q1)
      }

      "return Q1 and Q2 if the current date falls in the Q2" in {
        DateHelper.setDate(Some(LocalDate.of(2020, 4, 12)))

        quarters.getCurrentYearQuarters(mockAppConfig) mustBe Seq(quarters.Q1, quarters.Q2)
      }

      "return Q1, Q2 and Q3 if the current date falls in the Q3" in {
        DateHelper.setDate(Some(LocalDate.of(2020, 7, 12)))

        quarters.getCurrentYearQuarters(mockAppConfig) mustBe Seq(quarters.Q1, quarters.Q2, quarters.Q3)
      }

      "return Q1, Q2, Q3 and Q4 if the current date falls in the Q4" in {
        DateHelper.setDate(Some(LocalDate.of(2020, 10, 12)))

        quarters.getCurrentYearQuarters(mockAppConfig) mustBe Seq(quarters.Q1, quarters.Q2, quarters.Q3, quarters.Q4)
      }

      "return only Q2 if the minimum year is 2020 and the current date falls in the Q2" in {
        when(mockAppConfig.minimumYear).thenReturn(2020)
        DateHelper.setDate(Some(LocalDate.of(2020, 4, 12)))

        quarters.getCurrentYearQuarters(mockAppConfig) mustBe Seq(quarters.Q2)
      }
    }

    "on calling getQuartersFromDate" must {
      "return Q1 if the current date falls in Quarter 1" in {
        val date = LocalDate.of(2020, 2, 12)
        val result = quarters.getQuartersFromDate(date)

        result mustBe quarters.Q1
        result.startDay mustBe 1
        result.startMonth mustBe 1
        result.endDay mustBe 31
        result.endMonth mustBe 3
      }

      "return Q2 if the current date falls in Quarter 2" in {
        val date = LocalDate.of(2020, 5, 12)
        val result = quarters.getQuartersFromDate(date)

        result mustBe quarters.Q2
        result.startDay mustBe 1
        result.startMonth mustBe 4
        result.endDay mustBe 30
        result.endMonth mustBe 6
      }

      "return Q3 if the current date falls in Quarter 3" in {
        val date = LocalDate.of(2020, 8, 12)
        val result = quarters.getQuartersFromDate(date)

        result mustBe quarters.Q3
        result.startDay mustBe 1
        result.startMonth mustBe 7
        result.endDay mustBe 30
        result.endMonth mustBe 9
      }

      "return Q4 if the current date falls in Quarter 3" in {
        val date = LocalDate.of(2020, 12, 12)
        val result = quarters.getQuartersFromDate(date)
        result mustBe quarters.Q4

        result.startDay mustBe 1
        result.startMonth mustBe 10
        result.endDay mustBe 31
        result.endMonth mustBe 12
      }
    }
  }
}
