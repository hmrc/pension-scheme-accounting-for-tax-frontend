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

import base.SpecBase
import data.SampleData
import data.SampleData.{accessType, versionInt}
import helpers.{DeleteChargeHelper, FormatHelper}
import models.LocalDateBinder._
import models.requests.DataRequest
import models.{AccessMode, UserAnswers}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito
import org.mockito.Mockito.when
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.mockito.MockitoSugar
import play.api.Application
import play.api.i18n.Messages
import play.api.inject.bind
import play.api.inject.guice.{GuiceApplicationBuilder, GuiceableModule}
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.{AnyContent, Results}
import services.MemberSearchService.MemberRow
import uk.gov.hmrc.govukfrontend.views.viewmodels.content.Text
import uk.gov.hmrc.govukfrontend.views.viewmodels.summarylist.{ActionItem, Actions, Key, SummaryListRow, Value}

import java.time.LocalDate

class MemberSearchServiceSpec extends SpecBase with ScalaFutures with BeforeAndAfterEach with MockitoSugar with Results {

  import MemberSearchServiceSpec._

  private val deleteChargeHelper: DeleteChargeHelper = mock[DeleteChargeHelper]

  private def application: Application =
    new GuiceApplicationBuilder()
      .overrides(
        Seq[GuiceableModule](
          bind[DeleteChargeHelper].toInstance(deleteChargeHelper)
        ): _*
      ).build()

  private implicit val fakeDataRequest: DataRequest[AnyContent] = request()


  override def beforeEach(): Unit = {
    Mockito.reset(deleteChargeHelper)
    when(deleteChargeHelper.isLastCharge(any())).thenReturn(false)
  }

  private val memberSearchService = application.injector.instanceOf[MemberSearchService]

  "jsonSearch" must {
    "return one valid result when searching with a valid name when case not matching and not all memberChargeTypes have members" in {

      memberSearchService.jsonSearch("bloggs", uaJs - "chargeDDetails") mustBe
        Some(chargeA ++ expectedSearchBloggs ++ Json.obj("chargeGNoMatch" -> true))
    }

    "return several valid results when searching across all 3 charge types with a valid name when case not matching" in {
      memberSearchService.jsonSearch("whizz", uaJs) mustBe
        Some(chargeA ++ expectedSearchWhizz)
    }

    "return valid results when searching with a valid nino when case not matching" in {
      memberSearchService.jsonSearch("CS121212C", uaJs) mustBe
        Some(chargeA ++ expectedSearchBloggs ++ (expectedSearchWhizz - "chargeEDetails" - "chargeGDetails") ++ Json.obj("chargeGNoMatch" -> true))
    }
  }

  "search" must {
    "return member row for member found in search" in {

      memberSearchService.search(UserAnswers(uaJs), srn, startDate, "bloggs", accessType, versionInt) mustBe
        searchResultsMemberDetailsChargeE("Bill Bloggs", "CS121212C", BigDecimal("110.02"))
    }

    "return no results when nothing matches" in {
      memberSearchService.search(emptyUserAnswers, srn, startDate, "ZZ098765A", accessType, versionInt) mustBe Nil
    }

    "return valid results with no remove link when read only" in {
      val fakeDataRequest: DataRequest[AnyContent] = request(sessionAccessData = SampleData.sessionAccessData(accessMode = AccessMode.PageAccessModeViewOnly))
      List(memberSearchService.search(UserAnswers(uaJs), srn, startDate, "CS121212C", accessType, versionInt)(fakeDataRequest, messages)(1)) mustBe
        searchResultsMemberDetailsChargeE("Bill Bloggs", "CS121212C", BigDecimal("110.02"), removeLink = false)
    }

    "return no results when memberFormCompleted is false" in {
      memberSearchService.search(UserAnswers(incompleteChargeDDetails), srn, startDate, "ZZ098765A", accessType, versionInt) mustBe Nil
    }

    "return no results when memberFormCompleted is not defined" in {
      memberSearchService.search(UserAnswers(chargeDDetailsWithoutMemberFormCompletedFlag), srn, startDate, "ZZ098765A", accessType, versionInt) mustBe Nil
    }

  }

}

object MemberSearchServiceSpec {
  private val startDate = LocalDate.of(2020, 4, 1)
  private val srn = "srn"

  private def emptyUserAnswers: UserAnswers = UserAnswers()

  private def searchResultsMemberDetailsChargeE(name: String, nino: String, totalAmount: BigDecimal, index: Int = 0, removeLink: Boolean = true)
                                               (implicit messages: Messages) =
    Seq(MemberRow(name,
      Seq(
        SummaryListRow(Key(Text(messages("memberDetails.nino")), "govuk-!-width-one-half"),
          Value(Text(nino), "govuk-!-width-one-half")),
        SummaryListRow(Key(Text(messages("aft.summary.search.chargeType")), "govuk-!-width-one-half"),
          Value(Text(messages("aft.summary.annualAllowance.description")), "govuk-!-width-one-half")
        ),
        SummaryListRow(Key(Text(messages("aft.summary.search.amount")), "govuk-!-width-one-half"),
          Value(Text(s"${FormatHelper.formatCurrencyAmountAsString(totalAmount)}"), classes = "govuk-!-width-one-half"))
      ),
      if (removeLink) {
        Seq(Actions(
          items = Seq(ActionItem(
            content = Text(messages("site.view")),
            href = controllers.chargeE.routes.CheckYourAnswersController.onPageLoad(srn, startDate, accessType, 1, index).url
          ))
        ),
          Actions(
            items = Seq(ActionItem(
              content = Text(messages("site.remove")),
              href = controllers.chargeE.routes.DeleteMemberController.onPageLoad(srn, startDate, accessType, 1, index).url
            ))
          )
        )
      } else {
        Seq(
          Actions(
            items = Seq(ActionItem(
              content = Text(messages("site.view")),
              href = controllers.chargeE.routes.CheckYourAnswersController.onPageLoad(srn, startDate, accessType, 1, index).url
            ))
          )
        )
      }
    ))

  val chargeDDetailsWithoutMemberFormCompletedFlag: JsObject = Json.obj("chargeDDetails" -> Json.obj(
    "totalChargeAmount" -> 2345.02,
    "members" -> Json.arr(
      Json.obj(
        "memberStatus" -> "New",
        "memberDetails" -> Json.obj(
          "firstName" -> "Anne ",
          "lastName" -> "Whizz",
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
          "firstName" -> "Sarah",
          "lastName" -> "Smythe",
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
  ))

  val incompleteChargeDDetails: JsObject = Json.obj("chargeDDetails" -> Json.obj(
    "totalChargeAmount" -> 2345.02,
    "members" -> Json.arr(
      Json.obj(
        "memberStatus" -> "New",
        "memberDetails" -> Json.obj(
          "firstName" -> "Anne ",
          "lastName" -> "Whizz",
          "nino" -> "CS121212C"
        ),
        "memberAFTVersion" -> 1,
        "chargeDetails" -> Json.obj(
          "dateOfEvent" -> "2020-04-28",
          "taxAt55Percent" -> 55.55,
          "taxAt25Percent" -> 0.00
        ),
        "memberFormCompleted" -> "false"
      ),
      Json.obj(
        "memberStatus" -> "New",
        "memberDetails" -> Json.obj(
          "firstName" -> "Sarah",
          "lastName" -> "Smythe",
          "nino" -> "nino4"
        ),
        "memberAFTVersion" -> 1,
        "chargeDetails" -> Json.obj(
          "dateOfEvent" -> "2020-04-28",
          "taxAt55Percent" -> 45,
          "taxAt25Percent" -> 300.01
        ),
        "memberFormCompleted" -> true
      )
    ),
    "amendedVersion" -> 1
  ))

  val chargeA: JsObject = Json.obj("chargeADetails" -> Json.obj(
    "amendedVersion" -> 1,
    "chargeDetails" -> Json.obj(
      "totalAmtOfTaxDueAtHigherRate" -> 2500.02,
      "totalAmount" -> 4500.04,
      "numberOfMembers" -> 2,
      "totalAmtOfTaxDueAtLowerRate" -> 2000.02
    )
  ))
  val chargeD: JsObject = Json.obj("chargeDDetails" -> Json.obj(
    "totalChargeAmount" -> 2345.02,
    "members" -> Json.arr(
      Json.obj(
        "memberStatus" -> "New",
        "memberDetails" -> Json.obj(
          "firstName" -> "Anne ",
          "lastName" -> "Whizz",
          "nino" -> "CS121212C"
        ),
        "memberAFTVersion" -> 1,
        "chargeDetails" -> Json.obj(
          "dateOfEvent" -> "2020-04-28",
          "taxAt55Percent" -> 55.55,
          "taxAt25Percent" -> 0.00
        ),
        "memberFormCompleted" -> true
      ),
      Json.obj(
        "memberStatus" -> "New",
        "memberDetails" -> Json.obj(
          "firstName" -> "Sarah",
          "lastName" -> "Smythe",
          "nino" -> "nino4"
        ),
        "memberAFTVersion" -> 1,
        "chargeDetails" -> Json.obj(
          "dateOfEvent" -> "2020-04-28",
          "taxAt55Percent" -> 45,
          "taxAt25Percent" -> 300.01
        ),
        "memberFormCompleted" -> true
      )
    ),
    "amendedVersion" -> 1
  ))
  val chargeE: JsObject = Json.obj("chargeEDetails" -> Json.obj(
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
          "dateNoticeReceived" -> "2018-02-28",
          "isPaymentMandatory" -> true,
          "chargeAmount" -> 110.02
        ),
        "memberFormCompleted" -> true
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
          "dateNoticeReceived" -> "2018-02-28",
          "isPaymentMandatory" -> true,
          "chargeAmount" -> 110.02
        ),
        "memberFormCompleted" -> true
      )
    ),
    "amendedVersion" -> 1
  ))
  val chargeG: JsObject = Json.obj("chargeGDetails" -> Json.obj(
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
        ),
        "memberFormCompleted" -> true
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
        ),
        "memberFormCompleted" -> true
      )
    ),
    "amendedVersion" -> 1
  ))
  val uaJs: JsObject = chargeA ++ chargeD ++ chargeE ++ chargeG
  val expectedSearchBloggs: JsObject = Json.obj(
    "chargeEDetails" -> Json.obj(
      "totalChargeAmount" -> 2345.02,
      "members" -> Json.arr(
        Json.obj(
          "memberStatus" -> "New",
          "memberDetails" -> Json.obj(
            "firstName" -> "Bill",
            "lastName" -> "Bloggs",
            "nino" -> "CS121212C"
          ),
          "idx" -> 0,
          "memberAFTVersion" -> 1,
          "chargeDetails" -> Json.obj(
            "dateNoticeReceived" -> "2018-02-28",
            "isPaymentMandatory" -> true,
            "chargeAmount" -> 110.02
          ),
          "memberFormCompleted" -> true
        )
      ),
      "amendedVersion" -> 1
    )
  )
  val expectedSearchWhizz: JsObject = Json.obj(
    "chargeDDetails" -> Json.obj(
      "totalChargeAmount" -> 2345.02,
      "members" -> Json.arr(
        Json.obj(
          "memberStatus" -> "New",
          "memberDetails" -> Json.obj(
            "firstName" -> "Anne ",
            "lastName" -> "Whizz",
            "nino" -> "CS121212C"
          ),
          "idx" -> 0,
          "memberAFTVersion" -> 1,
          "chargeDetails" -> Json.obj(
            "dateOfEvent" -> "2020-04-28",
            "taxAt55Percent" -> 55.55,
            "taxAt25Percent" -> 0.00
          ),
          "memberFormCompleted" -> true
        )
      ),
      "amendedVersion" -> 1
    ),
    "chargeEDetails" -> Json.obj(
      "totalChargeAmount" -> 2345.02,
      "members" -> Json.arr(
        Json.obj(
          "memberStatus" -> "New",
          "memberDetails" -> Json.obj(
            "firstName" -> "Billy",
            "lastName" -> "Whizz",
            "nino" -> "nino4"
          ),
          "idx" -> 1,
          "memberAFTVersion" -> 1,
          "chargeDetails" -> Json.obj(
            "dateNoticeReceived" -> "2018-02-28",
            "isPaymentMandatory" -> true,
            "chargeAmount" -> 110.02
          ),
          "memberFormCompleted" -> true
        )
      ),
      "amendedVersion" -> 1
    ),
    "chargeGDetails" -> Json.obj(
      "totalChargeAmount" -> 10000,
      "members" -> Json.arr(
        Json.obj(
          "memberStatus" -> "New",
          "memberDetails" -> Json.obj(
            "firstName" -> "Mary",
            "lastName" -> "Whizz",
            "dob" -> "1950-08-29",
            "nino" -> "nino6"
          ),
          "idx" -> 1,
          "chargeDetails" -> Json.obj(
            "qropsReferenceNumber" -> "000000",
            "qropsTransferDate" -> "2016-02-29"
          ),
          "memberAFTVersion" -> 1,
          "chargeAmounts" -> Json.obj(
            "amountTaxDue" -> 10000,
            "amountTransferred" -> 10000
          ),
          "memberFormCompleted" -> true
        )
      ),
      "amendedVersion" -> 1
    )
  )

}
