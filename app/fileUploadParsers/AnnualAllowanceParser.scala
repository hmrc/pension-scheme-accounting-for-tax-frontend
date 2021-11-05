/*
 * Copyright 2021 HM Revenue & Customs
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

package fileUploadParsers

import models.{MemberDetails, UserAnswers, YearRange}
import models.chargeE.ChargeEDetails
import pages.chargeE.{AddMembersPage, AnnualAllowanceYearPage, ChargeDetailsPage, MemberDetailsPage, TotalChargeAmountPage}

import java.time.LocalDate

object AnnualAllowanceParser {
  def parse(request: UserAnswers, lines: List[String]): UserAnswers = {

    val memberDetails = lines.map { case line =>
      val items = line.split(",")
      val a = MemberDetails(items(0), items(1), items(2))
      val b = ChargeEDetails(BigDecimal(items(4)), LocalDate.parse(items(5)), items(6).trim.toBoolean)
      val c = YearRange(items(3))
      (a, b, c)
    }

    add(request, memberDetails)
  }

  def add(userAnswers: UserAnswers, memberDetails: List[(MemberDetails, ChargeEDetails, YearRange)], index: Int = 0): UserAnswers = {

    memberDetails.length match {
      case 0 => userAnswers
      case _ => add(userAnswers
        .setOrException(AddMembersPage, true)
        .setOrException(MemberDetailsPage(index), memberDetails.head._1)
        .setOrException(ChargeDetailsPage(index), memberDetails.head._2)
        .setOrException(AnnualAllowanceYearPage(index), memberDetails.head._3),
        memberDetails.tail,
        index + 1)
    }
  }
}
