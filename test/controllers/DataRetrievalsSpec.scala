/*
 * Copyright 2023 HM Revenue & Customs
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

package controllers

import data.SampleData
import data.SampleData.{accessType, versionInt}
import models.SponsoringEmployerType.{SponsoringEmployerTypeIndividual, SponsoringEmployerTypeOrganisation}
import models.chargeC.{ChargeCDetails, SponsoringEmployerAddress, SponsoringOrganisationDetails}
import models.chargeG.{MemberDetails => ChargeGMemberDetails}
import models.requests.DataRequest
import models.{AFTQuarter, Draft, MemberDetails, SponsoringEmployerType, UserAnswers}
import org.scalatest.OptionValues
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import pages._
import pages.chargeC._
import pages.chargeE.MemberDetailsPage
import pages.chargeG.{MemberDetailsPage => ChargeGMemberDetailsPage}
import play.api.mvc.Results.Ok
import play.api.mvc.{AnyContent, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers.{OK, status, _}
import uk.gov.hmrc.domain.PsaId

import java.time.LocalDate
import scala.concurrent.Future

class DataRetrievalsSpec extends AnyFreeSpec with Matchers with OptionValues {

  private val startDate = LocalDate.of(2020, 1, 1)
  private val endDate = LocalDate.of(2020, 3, 31)

  "retrieveSchemeName must" - {
    val result: String => Future[Result] = {_ => Future.successful(Ok("success result"))}
    "return successful result when scheme name is successfully retrieved from user answers" in {
      val ua = UserAnswers().set(SchemeNameQuery, value = "schemeName").getOrElse(UserAnswers())
      val request: DataRequest[AnyContent] = DataRequest(FakeRequest(GET, "/"), "test-internal-id", Some(PsaId("A2100000")), None, ua, SampleData.sessionData())
      val res = DataRetrievals.retrieveSchemeName(result)(request)
      status(res) must be(OK)
    }

    "return session expired when there is no scheme name in user answers" in {
      val request: DataRequest[AnyContent] = DataRequest(FakeRequest(GET, "/"),
        "test-internal-id", Some(PsaId("A2100000")), None, UserAnswers(), SampleData.sessionData())
      val res = DataRetrievals.retrieveSchemeName(result)(request)
      redirectLocation(res).value mustBe controllers.routes.SessionExpiredController.onPageLoad.url
    }
  }

  "retrieveSchemeNameWithEmailAndQuarter must" - {
    val result: (String, String, AFTQuarter) => Future[Result] = { (_, _, _) => Future.successful(Ok("success result"))}

    "return successful result when scheme name, email and quarter is successfully retrieved from user answers" in {
      val ua = UserAnswers().set(SchemeNameQuery, value = "schemeName").flatMap(_.set(EmailQuery, value = "test@test.com")).
        flatMap(_.set(QuarterPage, AFTQuarter(startDate, endDate))).getOrElse(UserAnswers())
      val request: DataRequest[AnyContent] = DataRequest(FakeRequest(GET, "/"), "test-internal-id", Some(PsaId("A2100000")), None, ua, SampleData.sessionData())
      val res = DataRetrievals.retrieveSchemeNameWithEmailAndQuarter(result)(request)
      status(res) must be(OK)
    }

    "return session expired when there is no scheme name or email or quarter in user answers" in {
      val request: DataRequest[AnyContent] = DataRequest(FakeRequest(GET, "/"),
        "test-internal-id", Some(PsaId("A2100000")), None, UserAnswers(), SampleData.sessionData())
      val res = DataRetrievals.retrieveSchemeNameWithEmailAndQuarter(result)(request)
      redirectLocation(res).value mustBe controllers.routes.SessionExpiredController.onPageLoad.url
    }
  }

  "retrievePSAAndSchemeDetailsWithAmendment must" - {
    val result: (String, String, String, AFTQuarter, Boolean, Int) => Future[Result] = { (_, _, _, _, _, _) => Future.successful(Ok("success result"))}

    "return successful result when scheme name, email and quarter is successfully retrieved from user answers" in {
      val ua = UserAnswers().set(SchemeNameQuery, value = "schemeName").flatMap(_.set(EmailQuery, value = "test@test.com")).
        flatMap(_.set(QuarterPage, AFTQuarter(startDate, endDate))).flatMap(_.set(PSTRQuery, value = "test-pstr")).getOrElse(UserAnswers())
      val request: DataRequest[AnyContent] = DataRequest(FakeRequest(GET, "/"), "test-internal-id", Some(PsaId("A2100000")), None, ua, SampleData.sessionData())
      val res = DataRetrievals.retrievePSAAndSchemeDetailsWithAmendment(result)(request)
      status(res) must be(OK)
    }

    "return session expired when there is no scheme name or email or quarter in user answers" in {
      val request: DataRequest[AnyContent] = DataRequest(FakeRequest(GET, "/"), "test-internal-id",
        Some(PsaId("A2100000")), None, UserAnswers(), SampleData.sessionData())
      val res = DataRetrievals.retrievePSAAndSchemeDetailsWithAmendment(result)(request)
      redirectLocation(res).value mustBe controllers.routes.SessionExpiredController.onPageLoad.url
    }
  }

  "retrieveSchemeAndQuarter must" - {
    val result: (String, AFTQuarter) => Future[Result] = { (_, _) => Future.successful(Ok("success result"))}

    "return successful result when scheme name and quarter is successfully retrieved from user answers" in {
      val ua = UserAnswers().set(SchemeNameQuery, value = "schemeName").
        flatMap(_.set(QuarterPage, AFTQuarter(startDate, endDate))).getOrElse(UserAnswers())
      val request: DataRequest[AnyContent] = DataRequest(FakeRequest(GET, "/"), "test-internal-id",
        Some(PsaId("A2100000")), None, ua, SampleData.sessionData())
      val res = DataRetrievals.retrieveSchemeAndQuarter(result)(request)
      status(res) must be(OK)
    }

    "return session expired when there is no scheme name or quarter in user answers" in {
      val request: DataRequest[AnyContent] = DataRequest(FakeRequest(GET, "/"), "test-internal-id",
        Some(PsaId("A2100000")), None, UserAnswers(), SampleData.sessionData())
      val res = DataRetrievals.retrieveSchemeAndQuarter(result)(request)
      redirectLocation(res).value mustBe controllers.routes.SessionExpiredController.onPageLoad.url
    }
  }

  "retrievePSTR must" - {
    val result: String => Future[Result] = {_ => Future.successful(Ok("success result"))}
    "return successful result when pstr is successfully retrieved from user answers" in {
      val ua = UserAnswers().set(PSTRQuery, value = "test pstr").getOrElse(UserAnswers())
      val request: DataRequest[AnyContent] = DataRequest(FakeRequest(GET, "/"), "test-internal-id",
        Some(PsaId("A2100000")), None, ua, SampleData.sessionData())
      val res = DataRetrievals.retrievePSTR(result)(request)
      status(res) must be(OK)
    }

    "return session expired when there is no pstr in user answers" in {
      val request: DataRequest[AnyContent] = DataRequest(FakeRequest(GET, "/"), "test-internal-id",
        Some(PsaId("A2100000")), None, UserAnswers(), SampleData.sessionData())
      val res = DataRetrievals.retrievePSTR(result)(request)
      redirectLocation(res).value mustBe controllers.routes.SessionExpiredController.onPageLoad.url
    }
  }

  "retrieveSchemeAndMember must" - {
    val result: (String, String) => Future[Result] = { (_, _) => Future.successful(Ok("success result"))}

    "return successful result when scheme name and member is successfully retrieved from user answers" in {
      val ua = UserAnswers().set(SchemeNameQuery, value = "schemeName").
        flatMap(_.set(MemberDetailsPage(0), MemberDetails("test", "name", "ab200100a"))).getOrElse(UserAnswers())
      val request: DataRequest[AnyContent] = DataRequest(FakeRequest(GET, "/"), "test-internal-id", Some(PsaId("A2100000")), None, ua, SampleData.sessionData())
      val res = DataRetrievals.retrieveSchemeAndMember(MemberDetailsPage(0))(result)(request)
      status(res) must be(OK)
    }

    "return session expired when there is no scheme name or member in user answers" in {
      val request: DataRequest[AnyContent] = DataRequest(FakeRequest(GET, "/"), "test-internal-id",
        Some(PsaId("A2100000")), None, UserAnswers(), SampleData.sessionData())
      val res = DataRetrievals.retrieveSchemeAndMember(MemberDetailsPage(0))(result)(request)
      redirectLocation(res).value mustBe controllers.routes.SessionExpiredController.onPageLoad.url
    }
  }

  "retrieveSchemeMemberChargeG must" - {
    val result: (String, String) => Future[Result] = { (_, _) => Future.successful(Ok("success result"))}

    "return successful result when scheme name and member is successfully retrieved from user answers" in {
      val ua = UserAnswers().set(SchemeNameQuery, value = "schemeName").
        flatMap(_.set(ChargeGMemberDetailsPage(0), ChargeGMemberDetails("test", "name", LocalDate.now(), "ab200100a")))
        .getOrElse(UserAnswers())
      val request: DataRequest[AnyContent] = DataRequest(FakeRequest(GET, "/"), "test-internal-id",
        Some(PsaId("A2100000")), None, ua, SampleData.sessionData())
      val res = DataRetrievals.retrieveSchemeMemberChargeG(ChargeGMemberDetailsPage(0))(result)(request)
      status(res) must be(OK)
    }

    "return session expired when there is no scheme name or member in user answers" in {
      val request: DataRequest[AnyContent] = DataRequest(FakeRequest(GET, "/"), "test-internal-id",
        Some(PsaId("A2100000")), None, UserAnswers(), SampleData.sessionData())
      val res = DataRetrievals.retrieveSchemeMemberChargeG(ChargeGMemberDetailsPage(0))(result)(request)
      redirectLocation(res).value mustBe controllers.routes.SessionExpiredController.onPageLoad.url
    }
  }

  "retrieveSchemeAndSponsoringEmployer must" - {
    val result: (String, String) => Future[Result] = { (_, _) => Future.successful(Ok("success result"))}

    "return successful result when scheme name and company name is successfully retrieved from user answers" in {
      val ua = UserAnswers().set(SchemeNameQuery, value = "schemeName").
        flatMap(_.set(WhichTypeOfSponsoringEmployerPage(0), SponsoringEmployerTypeOrganisation)).
        flatMap(_.set(SponsoringOrganisationDetailsPage(0), SponsoringOrganisationDetails("company", "test crn")))
        .getOrElse(UserAnswers())
      val request: DataRequest[AnyContent] = DataRequest(FakeRequest(GET, "/"), "test-internal-id",
        Some(PsaId("A2100000")), None, ua, SampleData.sessionData())
      val res = DataRetrievals.retrieveSchemeAndSponsoringEmployer(index = 0)(result)(request)
      status(res) must be(OK)
    }

    "return successful result when scheme name and individual name is successfully retrieved from user answers" in {
      val ua = UserAnswers().set(SchemeNameQuery, value = "schemeName").
        flatMap(_.set(WhichTypeOfSponsoringEmployerPage(0), SponsoringEmployerTypeIndividual)).
        flatMap(_.set(SponsoringIndividualDetailsPage(0), MemberDetails("first", "last", "ab100100a")))
        .getOrElse(UserAnswers())
      val request: DataRequest[AnyContent] = DataRequest(FakeRequest(GET, "/"), "test-internal-id",
        Some(PsaId("A2100000")), None, ua, SampleData.sessionData())
      val res = DataRetrievals.retrieveSchemeAndSponsoringEmployer(index = 0)(result)(request)
      status(res) must be(OK)
    }

    "return session expired when there is no scheme name or company name or individual name in user answers" in {
      val request: DataRequest[AnyContent] = DataRequest(FakeRequest(GET, "/"), "test-internal-id",
        Some(PsaId("A2100000")), None, UserAnswers(), SampleData.sessionData())
      val res = DataRetrievals.retrieveSchemeAndSponsoringEmployer(index = 0)(result)(request)
      redirectLocation(res).value mustBe controllers.routes.SessionExpiredController.onPageLoad.url
    }
  }

  "retrieveSchemeEmployerTypeAndSponsoringEmployer must" - {
    val result: (String, String, SponsoringEmployerType) => Future[Result] = { (_, _, _) => Future.successful(Ok("success result"))}

    "return successful result when scheme name and company name is successfully retrieved from user answers" in {
      val ua = UserAnswers().set(SchemeNameQuery, value = "schemeName").
        flatMap(_.set(WhichTypeOfSponsoringEmployerPage(0), SponsoringEmployerTypeOrganisation)).
        flatMap(_.set(SponsoringOrganisationDetailsPage(0), SponsoringOrganisationDetails("company", "test crn")))
        .getOrElse(UserAnswers())
      val request: DataRequest[AnyContent] = DataRequest(FakeRequest(GET, "/"), "test-internal-id",
        Some(PsaId("A2100000")), None, ua, SampleData.sessionData())
      val res = DataRetrievals.retrieveSchemeEmployerTypeAndSponsoringEmployer(index = 0)(result)(request)
      status(res) must be(OK)
    }

    "return successful result when scheme name and individual name is successfully retrieved from user answers" in {
      val ua = UserAnswers().set(SchemeNameQuery, value = "schemeName").
        flatMap(_.set(WhichTypeOfSponsoringEmployerPage(0), SponsoringEmployerTypeIndividual)).
        flatMap(_.set(SponsoringIndividualDetailsPage(0), MemberDetails("first", "last", "ab100100a")))
        .getOrElse(UserAnswers())
      val request: DataRequest[AnyContent] = DataRequest(FakeRequest(GET, "/"), "test-internal-id",
        Some(PsaId("A2100000")), None, ua, SampleData.sessionData())
      val res = DataRetrievals.retrieveSchemeEmployerTypeAndSponsoringEmployer(index = 0)(result)(request)
      status(res) must be(OK)
    }

    "return session expired when there is no scheme name or company name or individual name in user answers" in {
      val request: DataRequest[AnyContent] = DataRequest(FakeRequest(GET, "/"), "test-internal-id",
        Some(PsaId("A2100000")), None, UserAnswers(), SampleData.sessionData())
      val res = DataRetrievals.retrieveSchemeEmployerTypeAndSponsoringEmployer(index = 0)(result)(request)
      redirectLocation(res).value mustBe controllers.routes.SessionExpiredController.onPageLoad.url
    }
  }

  "cyaChargeC must" - {
    val result: (SponsoringEmployerType, Either[models.MemberDetails, SponsoringOrganisationDetails], SponsoringEmployerAddress, ChargeCDetails, String) =>
      Future[Result] = { (_, _, _, _, _) => Future.successful(Ok("success result"))}
    val startDate = LocalDate.of(2020, 1, 1)

    "return successful result when sponsoring individual is successfully retrieved from user answers" in {
      val ua = UserAnswers().set(SchemeNameQuery, value = "schemeName").
        flatMap(_.set(WhichTypeOfSponsoringEmployerPage(0), SponsoringEmployerTypeIndividual)).
        flatMap(_.set(SponsoringIndividualDetailsPage(0), MemberDetails("first", "last", "nino"))).
        flatMap(_.set(SponsoringEmployerAddressPage(0), SponsoringEmployerAddress("line1", "line2", None, None, "GB", None))).
        flatMap(_.set(ChargeCDetailsPage(0), ChargeCDetails(LocalDate.now(), 100.00)))
        .getOrElse(UserAnswers())
      val request: DataRequest[AnyContent] = DataRequest(FakeRequest(GET, "/"), "test-internal-id",
        Some(PsaId("A2100000")), None, ua, SampleData.sessionData())
      val res = DataRetrievals.cyaChargeC(index = 0, "test-srn", LocalDate.now(), Draft, 1)(result)(request)
      status(res) must be(OK)
    }

    "return successful result when sponsoring organisation is successfully retrieved from user answers" in {
      val ua = UserAnswers().set(SchemeNameQuery, value = "schemeName").
        flatMap(_.set(WhichTypeOfSponsoringEmployerPage(0), SponsoringEmployerTypeOrganisation)).
        flatMap(_.set(SponsoringOrganisationDetailsPage(0), SponsoringOrganisationDetails("name", "test-crn"))).
        flatMap(_.set(SponsoringEmployerAddressPage(0), SponsoringEmployerAddress("line1", "line2", None, None, "GB", None))).
        flatMap(_.set(ChargeCDetailsPage(0), ChargeCDetails(LocalDate.now(), 100.00)))
        .getOrElse(UserAnswers())
      val request: DataRequest[AnyContent] = DataRequest(FakeRequest(GET, "/"), "test-internal-id",
        Some(PsaId("A2100000")), None, ua, SampleData.sessionData())
      val res = DataRetrievals.cyaChargeC(index = 0, "test-srn", LocalDate.now(), Draft, 1)(result)(request)
      status(res) must be(OK)
    }

    "return aft summary when there is no complete sponsoring employer details in user answers" in {
      val ua = UserAnswers().set(SchemeNameQuery, value = "schemeName").
        flatMap(_.set(WhichTypeOfSponsoringEmployerPage(0), SponsoringEmployerTypeOrganisation)).
        flatMap(_.set(SponsoringEmployerAddressPage(0), SponsoringEmployerAddress("line1", "line2", None, None, "GB", None))).
        flatMap(_.set(ChargeCDetailsPage(0), ChargeCDetails(LocalDate.now(), 100.00)))
        .getOrElse(UserAnswers())
      val request: DataRequest[AnyContent] = DataRequest(FakeRequest(GET, "/"), "test-internal-id",
        Some(PsaId("A2100000")), None, ua, SampleData.sessionData())
      val res = DataRetrievals.cyaChargeC(index = 0, "test-srn", startDate, Draft, 1)(result)(request)
      redirectLocation(res).value mustBe controllers.routes.AFTSummaryController.onPageLoad("test-srn", "2020-01-01", accessType, versionInt).url
    }

    "return aft summary when there is no sponsoring employer details in user answers" in {
      val request: DataRequest[AnyContent] = DataRequest(FakeRequest(GET, "/"), "test-internal-id",
        Some(PsaId("A2100000")), None, UserAnswers(), SampleData.sessionData())
      val res = DataRetrievals.cyaChargeC(index = 0, "test-srn", startDate, Draft, 1)(result)(request)
      redirectLocation(res).value mustBe controllers.routes.AFTSummaryController.onPageLoad("test-srn", "2020-01-01", accessType, versionInt).url
    }
  }
}
