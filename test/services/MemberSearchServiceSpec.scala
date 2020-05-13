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
import data.SampleData._
import models.SponsoringEmployerType.{SponsoringEmployerTypeIndividual, SponsoringEmployerTypeOrganisation}
import models.{UserAnswers, YearRange}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.mockito.MockitoSugar
import pages.chargeC._
import pages.chargeD._
import pages.chargeE._
import pages.chargeG.{ChargeAmountsPage, MemberDetailsPage, TotalChargeAmountPage}
import play.api.mvc.Results

class MemberSearchServiceSpec extends SpecBase with ScalaFutures  with BeforeAndAfterEach with MockitoSugar with Results {

  private val memberSearchService = new MemberSearchService

  private def ua: UserAnswers = userAnswersWithSchemeNamePstrQuarter
    .setOrException(WhichTypeOfSponsoringEmployerPage(0), SponsoringEmployerTypeIndividual)
    .setOrException(WhichTypeOfSponsoringEmployerPage(1), SponsoringEmployerTypeOrganisation)
    .setOrException(SponsoringIndividualDetailsPage(0), sponsoringIndividualDetails)
    .setOrException(SponsoringOrganisationDetailsPage(1), sponsoringOrganisationDetails)
    .setOrException(ChargeCDetailsPage(0), chargeCDetails)
    .setOrException(ChargeCDetailsPage(1), chargeCDetails)
    .setOrException(pages.chargeC.TotalChargeAmountPage, BigDecimal(66.88))
    .setOrException(pages.chargeD.MemberDetailsPage(0), memberDetails)
    .setOrException(pages.chargeD.MemberDetailsPage(1), memberDetails2)
    .setOrException(pages.chargeD.ChargeDetailsPage(0), chargeDDetails)
    .setOrException(pages.chargeD.ChargeDetailsPage(1), chargeDDetails)
    .setOrException(pages.chargeD.TotalChargeAmountPage, BigDecimal(66.88))
    .setOrException(pages.chargeE.MemberDetailsPage(0), memberDetails)
    .setOrException(pages.chargeE.MemberDetailsPage(1), memberDetails2)
    .setOrException(pages.chargeE.AnnualAllowanceYearPage(0), YearRange.currentYear)
    .setOrException(pages.chargeE.AnnualAllowanceYearPage(1), YearRange.currentYear)
    .setOrException(pages.chargeE.ChargeDetailsPage(0), chargeEDetails)
    .setOrException(pages.chargeE.ChargeDetailsPage(1), chargeEDetails)
    .setOrException(pages.chargeE.TotalChargeAmountPage, BigDecimal(66.88))
    .setOrException(pages.chargeG.MemberDetailsPage(0), memberGDetails)
    .setOrException(pages.chargeG.MemberDetailsPage(1), memberGDetails2)
    .setOrException(pages.chargeG.ChargeAmountsPage(0), chargeAmounts)
    .setOrException(pages.chargeG.ChargeAmountsPage(1), chargeAmounts2)
    .setOrException(pages.chargeG.TotalChargeAmountPage, BigDecimal(66.88))


  "Search" must {
    "return valid results" in {
    }

    "return no results when nothing matches" in {
      val nino = "ZZ098765A"

      memberSearchService.search(ua, "srn" , LocalDate.of(2020,4,1) ,nino) mustBe Nil

    }
  }



}
