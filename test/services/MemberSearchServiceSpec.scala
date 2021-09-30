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

package services

import java.time.LocalDate
import base.SpecBase
import data.SampleData
import data.SampleData.{accessType, versionInt}
import helpers.FormatHelper
import models.requests.DataRequest
import models.{AccessMode, Member, UserAnswers}
import org.mockito.Matchers.any
import org.mockito.Mockito
import org.mockito.Mockito.when
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.mockito.MockitoSugar
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.{GuiceApplicationBuilder, GuiceableModule}
import play.api.libs.json.Json
import play.api.mvc.{AnyContent, Results}
import services.MemberSearchService.MemberRow
import uk.gov.hmrc.viewmodels.SummaryList.{Action, Key, Row, Value}
import uk.gov.hmrc.viewmodels.Text.{Literal, Message}

class MemberSearchServiceSpec extends SpecBase with ScalaFutures with BeforeAndAfterEach with MockitoSugar with Results {

  import MemberSearchServiceSpec._

  private val mockChargeDService: ChargeDService = mock[ChargeDService]
  private val mockChargeEService: ChargeEService = mock[ChargeEService]
  private val mockChargeGService: ChargeGService = mock[ChargeGService]

  private def application: Application =
    new GuiceApplicationBuilder()
      .overrides(
        Seq[GuiceableModule](
          bind[ChargeDService].toInstance(mockChargeDService),
          bind[ChargeEService].toInstance(mockChargeEService),
          bind[ChargeGService].toInstance(mockChargeGService)
        ): _*
      ).build()

  private implicit val fakeDataRequest: DataRequest[AnyContent] = request()



  override def beforeEach: Unit = {
    Mockito.reset(mockChargeDService, mockChargeEService, mockChargeGService)
    when(mockChargeDService.getLifetimeAllowanceMembers(any(),any(),any(), any(), any())(any()))
      .thenReturn(chargeDMembers)

    when(mockChargeEService.getAnnualAllowanceMembers(any(),any(),any(), any(), any())(any()))
      .thenReturn(chargeEMembers)

    when(mockChargeGService.getOverseasTransferMembers(any(),any(),any(), any(), any())(any()))
      .thenReturn(chargeGMembers)
  }

  private val memberSearchService = application.injector.instanceOf[MemberSearchService]

  "Search" must {
    "return one valid result when searching with a valid name when case not matching" in {
      memberSearchService.search(emptyUserAnswers, srn, startDate, "bloggs", accessType, versionInt) mustBe
        searchResultsMemberDetailsChargeD("Bill Bloggs", "CS121212C", BigDecimal("55.55"))
    }

    "return several valid results when searching across all 3 charge types with a valid name when case not matching" in {
      val expected =
        searchResultsMemberDetailsChargeE("Anne Whizz", "nino1", BigDecimal("102.56")) ++
        searchResultsMemberDetailsChargeD("Billy Whizz", "nino4", BigDecimal("23.78")) ++
        searchResultsMemberDetailsChargeG("Mary Whizz", "nino6", BigDecimal("84.06"))


      val actual = memberSearchService.search(emptyUserAnswers, srn, startDate, "whizz", accessType, versionInt)

      actual.size mustBe expected.size

      actual.head mustBe expected.head
      actual(1) mustBe expected(1)
      actual(2) mustBe expected(2)
    }

    "return valid results when searching with a valid nino when case not matching" in {
      memberSearchService.search(emptyUserAnswers, srn, startDate, "CS121212C", accessType, versionInt) mustBe
        searchResultsMemberDetailsChargeD("Bill Bloggs", "CS121212C", BigDecimal("55.55"))
    }

    "return no results when nothing matches" in {
      memberSearchService.search(emptyUserAnswers, srn, startDate, "ZZ098765A", accessType, versionInt) mustBe Nil
    }

    "return valid results with no remove link when read only" in {
      val fakeDataRequest: DataRequest[AnyContent] = request(sessionAccessData = SampleData.sessionAccessData(accessMode = AccessMode.PageAccessModeViewOnly))

      memberSearchService.search(emptyUserAnswers, srn, startDate, "CS121212C", accessType, versionInt)(fakeDataRequest) mustBe
        searchResultsMemberDetailsChargeD("Bill Bloggs", "CS121212C", BigDecimal("55.55"), removeLink = false)
    }
  }

}

object MemberSearchServiceSpec {
  private val startDate = LocalDate.of(2020, 4, 1)
  private val srn = "srn"
  private def emptyUserAnswers: UserAnswers = UserAnswers()

  private def searchResultsMemberDetailsChargeD(name: String, nino: String, totalAmount:BigDecimal, index:Int = 0, removeLink: Boolean = true) = Seq(
    MemberRow(
      name,
      Seq(
        Row(
          Key(Message("memberDetails.nino"), Seq("govuk-!-width-one-half")),
          Value(Literal(nino), Seq("govuk-!-width-one-half"))
        ),
        Row(
          Key(Message("aft.summary.search.chargeType"), Seq("govuk-!-width-one-half")),
          Value(Message("aft.summary.lifeTimeAllowance.description"), Seq("govuk-!-width-one-half"))
        ),
        Row(
          Key(Message("aft.summary.search.amount"), Seq("govuk-!-width-one-half")),

          Value(Literal(s"${FormatHelper.formatCurrencyAmountAsString(totalAmount)}"),
            classes = Seq("govuk-!-width-one-half"))
        )
      ),
      if(removeLink) {
        Seq(
          Action(
            Message("site.view"),
            "view-link",
            None
          ),
          Action(
            Message("site.remove"),
            "remove-link",
            None
          )
        )
      } else {
        Seq(
          Action(
            Message("site.view"),
            "view-link",
            None
          )
        )
      }
    )
  )

  private def searchResultsMemberDetailsChargeE(name: String, nino: String, totalAmount:BigDecimal, index:Int = 0) = Seq(
    MemberRow(
      name,
      Seq(
        Row(
          Key(Message("memberDetails.nino"), Seq("govuk-!-width-one-half")),
          Value(Literal(nino), Seq("govuk-!-width-one-half"))
        ),
        Row(
          Key(Message("aft.summary.search.chargeType"), Seq("govuk-!-width-one-half")),
          Value(Message("aft.summary.annualAllowance.description"), Seq("govuk-!-width-one-half"))
        ),
        Row(
          Key(Message("aft.summary.search.amount"), Seq("govuk-!-width-one-half")),

          Value(Literal(s"${FormatHelper.formatCurrencyAmountAsString(totalAmount)}"),
            classes = Seq("govuk-!-width-one-half"))

        )
      ),
      Seq(
        Action(
          Message("site.view"),
          "view-link",
          None
        ),
        Action(
          Message("site.remove"),
          "remove-link",
          None
        )
      )
    )
  )

  private def searchResultsMemberDetailsChargeG(name: String, nino: String, totalAmount:BigDecimal, index:Int = 0) = Seq(
    MemberRow(
      name,
      Seq(
        Row(
          Key(Message("memberDetails.nino"), Seq("govuk-!-width-one-half")),
          Value(Literal(nino), Seq("govuk-!-width-one-half"))
        ),
        Row(
          Key(Message("aft.summary.search.chargeType"), Seq("govuk-!-width-one-half")),
          Value(Message("aft.summary.overseasTransfer.description"), Seq("govuk-!-width-one-half"))
        ),
        Row(
          Key(Message("aft.summary.search.amount"), Seq("govuk-!-width-one-half")),
          Value(Literal(s"${FormatHelper.formatCurrencyAmountAsString(totalAmount)}"),
            classes = Seq("govuk-!-width-one-half"))
        )
      ),
      Seq(
        Action(
          Message("site.view"),
          "view-link",
          None
        ),
        Action(
          Message("site.remove"),
          "remove-link",
          None
        )
      )
    )
  )



  private val chargeEMembers = Seq(
    Member(0, "Anne Whizz", "nino1", BigDecimal(102.56), "view-link", "remove-link"),
    Member(1, "Sarah Smythe", "nino2", BigDecimal(77.22), "view-link", "remove-link")
  )

  private val chargeGMembers = Seq(
    Member(0, "Phil Hollins", "nino5", BigDecimal(69.15), "view-link", "remove-link"),
    Member(1, "Mary Whizz", "nino6", BigDecimal(84.06), "view-link", "remove-link")
  )

  private val chargeDMembers = Seq(
    Member(0, "Bill Bloggs", "CS121212C", BigDecimal(55.55), "view-link", "remove-link"),
    Member(1, "Billy Whizz", "nino4", BigDecimal(23.78), "view-link", "remove-link")
  )
    val uaJs = Json.obj(
            "chargeCDetails" -> Json.obj(
                "employers" -> Json.arr(
                    Json.obj(
                        "sponsoringIndividualDetails" -> Json.obj(
                            "firstName" -> "Ray",
                            "lastName" -> "Golding",
                            "nino" -> "AA000020A"
                        ),
                        "memberStatus" -> "New",
                        "sponsoringEmployerAddress" -> Json.obj(
                            "country" -> "GB",
                            "line4" -> "Shropshire",
                            "postcode" -> "TF3 4NT",
                            "line3" -> "Telford",
                            "line2" -> "Ironmasters Way",
                            "line1" -> "Plaza 2 "
                        ),
                        "memberAFTVersion" -> 1,
                        "chargeDetails" -> Json.obj(
                            "amountTaxDue" -> 80.02,
                            "paymentDate" -> "2020-04-28"
                        ),
                        "whichTypeOfSponsoringEmployer" -> "individual"
                    ),
                    Json.obj(
                        "sponsoringIndividualDetails" -> Json.obj(
                            "firstName" -> "Aaa",
                            "lastName" -> "Golding",
                            "nino" -> "AA000620A"
                        ),
                        "memberStatus" -> "New",
                        "sponsoringEmployerAddress" -> Json.obj(
                            "country" -> "GB",
                            "line4" -> "Shropshire",
                            "postcode" -> "TF3 4NT",
                            "line3" -> "Telford",
                            "line2" -> "Ironmasters Way",
                            "line1" -> "Plaza 2 "
                        ),
                        "memberAFTVersion" -> 1,
                        "chargeDetails" -> Json.obj(
                            "amountTaxDue" -> 80.02,
                            "paymentDate" -> "2020-04-28"
                        ),
                        "whichTypeOfSponsoringEmployer" -> "company"
                    ),
                    Json.obj(
                        "sponsoringIndividualDetails" -> Json.obj(
                            "firstName" -> "Bay",
                            "lastName" -> "Golding",
                            "nino" -> "AA000620A"
                        ),
                        "memberStatus" -> "New",
                        "sponsoringEmployerAddress" -> Json.obj(
                            "country" -> "GB",
                            "line4" -> "Shropshire",
                            "postcode" -> "TF3 4NT",
                            "line3" -> "Telford",
                            "line2" -> "Ironmasters Way",
                            "line1" -> "Plaza 2 "
                        ),
                        "memberAFTVersion" -> 1,
                        "chargeDetails" -> Json.obj(
                            "amountTaxDue" -> 80.02,
                            "paymentDate" -> "2020-04-28"
                        ),
                        "whichTypeOfSponsoringEmployer" -> "individual"
                    ),
                    Json.obj(
                        "sponsoringIndividualDetails" -> Json.obj(
                            "firstName" -> "Cay",
                            "lastName" -> "McMillan",
                            "nino" -> "AA000620A"
                        ),
                        "memberStatus" -> "New",
                        "sponsoringEmployerAddress" -> Json.obj(
                            "country" -> "GB",
                            "line4" -> "Warwickshire",
                            "postcode" -> "B1 1LA",
                            "line3" -> "Birmingham",
                            "line2" -> "Post Box APTS",
                            "line1" -> "45 UpperMarshall Street"
                        ),
                        "memberAFTVersion" -> 1,
                        "chargeDetails" -> Json.obj(
                            "amountTaxDue" -> 10,
                            "paymentDate" -> "2020-04-28"
                        ),
                        "whichTypeOfSponsoringEmployer" -> "individual"
                    )
                ),
                "totalChargeAmount" -> 90.02,
                "amendedVersion" -> 1
            ),
            "submitterDetails" -> Json.obj(
                "submitterType" -> "PSP",
                "submitterID" -> "10000240",
                "authorisingPsaId" -> "A2100005",
                "receiptDate" -> "2016-12-17",
                "submitterName" -> "Nigel"
            ),
            "minimalFlags" -> Json.obj(
                "deceasedFlag" -> false,
                "rlsFlag" -> false
            ),
            "chargeADetails" -> Json.obj(
                "amendedVersion" -> 1,
                "chargeDetails" -> Json.obj(
                    "totalAmtOfTaxDueAtHigherRate" -> 2500.02,
                    "totalAmount" -> 4500.04,
                    "numberOfMembers" -> 2,
                    "totalAmtOfTaxDueAtLowerRate" -> 2000.02
                )
            ),
            "chargeBDetails" -> Json.obj(
                "amendedVersion" -> 1,
                "chargeDetails" -> Json.obj(
                    "totalAmount" -> 1064.92,
                    "numberOfDeceased" -> 2
                )
            ),
            "chargeDDetails" -> Json.obj(
                "totalChargeAmount" -> 2345.02,
                "members" -> Json.arr(
                    Json.obj(
                        "memberStatus" -> "New",
                        "memberDetails" -> Json.obj(
                            "firstName" -> "Bill",
                            "lastName" -> "Bloggs",
                            "nino" -> "CS121212C"
                        ),
                        "memberAFTVersion" -> 1,
                        "chargeDetails" -> Json.obj(
                            "dateOfEvent" -> "2020-04-28",
                            "taxAt55Percent" -> 55.55,
                            "taxAt25Percent" -> 0.00
                        )
                    ),
                    Json.obj(
                        "memberStatus" -> "New",
                        "memberDetails" -> Json.obj(
                            "firstName" -> "Billy",
                            "lastName" -> "Whizz",
                            "nino" -> "nino4"
                        ),
                        "memberAFTVersion" -> 1,
                        "chargeDetails" -> Json.obj(
                            "dateOfEvent" -> "2020-04-28",
                            "taxAt55Percent" -> 45,
                            "taxAt25Percent" -> 300.01
                        )
                    )
                ),
                "amendedVersion" -> 1
            ),
            "schemeName" -> "Open Scheme Overview API Test",
            "loggedInPersonEmail" -> "nigel@test.com",
            "aftStatus" -> "Submitted",
            "schemeStatus" -> "Open",
            "loggedInPersonName" -> "Nigel Robert Smith",
            "pstr" -> "24000040IN",
            "chargeGDetails" -> Json.obj(
                "totalChargeAmount" -> 10000,
                "members" -> Json.arr(
                    Json.obj(
                        "memberStatus" -> "New",
                        "memberDetails" -> Json.obj(
                            "firstName" -> "Phil",
                            "lastName" -> "Hollins",
                            "dob" -> "1950-08-29",
                            "nino" -> "nino5"
                        ),
                        "chargeDetails" -> Json.obj(
                            "qropsReferenceNumber" -> "000000",
                            "qropsTransferDate" -> "2016-02-29"
                        ),
                        "memberAFTVersion" -> 1,
                        "chargeAmounts" -> Json.obj(
                            "amountTaxDue" -> 10000,
                            "amountTransferred" -> 10000
                        )
                    ),
                  Json.obj(
                    "memberStatus" -> "New",
                    "memberDetails" -> Json.obj(
                      "firstName" -> "Mary",
                      "lastName" -> "Whizz",
                      "dob" -> "1950-08-29",
                      "nino" -> "nino6"
                    ),
                    "chargeDetails" -> Json.obj(
                      "qropsReferenceNumber" -> "000000",
                      "qropsTransferDate" -> "2016-02-29"
                    ),
                    "memberAFTVersion" -> 1,
                    "chargeAmounts" -> Json.obj(
                      "amountTaxDue" -> 10000,
                      "amountTransferred" -> 10000
                    )
                  )
                ),
                "amendedVersion" -> 1
            ),
            "quarter" -> Json.obj(
                "endDate" -> "2020-06-30",
                "startDate" -> "2020-04-01"
            )
        )

}
