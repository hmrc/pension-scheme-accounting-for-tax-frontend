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

import base.SpecBase
import data.SampleData.sessionId
import helpers.DeleteChargeHelper
import models.requests.DataRequest
import models.{AccessMode, CheckMode, NormalMode, SessionAccessData, SessionData, UserAnswers}
import org.mockito.Matchers
import org.mockito.Matchers.any
import org.mockito.Mockito.{reset, when}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.mockito.MockitoSugar
import pages.QuestionPage
import play.api.libs.json.{JsPath, Json}
import play.api.mvc.AnyContent
import play.api.test.FakeRequest
import play.api.test.Helpers.GET
import uk.gov.hmrc.domain.PsaId

import scala.concurrent.Future

class UserAnswersServiceSpec extends SpecBase with MockitoSugar with ScalaFutures with BeforeAndAfterEach {

  import UserAnswersServiceSpec._
  val mockDeleteChargeHelper: DeleteChargeHelper = mock[DeleteChargeHelper]
  val service: UserAnswersService = new UserAnswersService(mockDeleteChargeHelper)

  override def beforeEach: Unit = {
    super.beforeEach()
    reset(mockDeleteChargeHelper)
    when(mockDeleteChargeHelper.isLastCharge(any())).thenReturn(false)
  }

  ".removeSchemeBasedCharge" must {
    "FIRST COMPILE - completely remove a scheme level charge being deleted if version is 1 and it is not the last charge" in {

      val result = service.removeSchemeBasedCharge(Page)(dataRequest(ua))

       result mustBe UserAnswers()
    }

    "FIRST COMPILE - zero out a scheme level charge being deleted if version is 1 and it is the last charge" in {
      when(mockDeleteChargeHelper.isLastCharge(any())).thenReturn(true)
      when(mockDeleteChargeHelper.zeroOutCharge(Matchers.eq(Page), any())).thenReturn(uaVersion2)
      val result = service.removeSchemeBasedCharge(Page)(dataRequest(ua))

      result mustBe uaVersion2
    }

    "AMENDMENT - set amended version to null and zero out a scheme level charge being deleted if version is 2" in {
      when(mockDeleteChargeHelper.zeroOutCharge(Page, uaVersion2)).thenReturn(uaVersion2)
      val result = service.removeSchemeBasedCharge(Page)(dataRequest(uaVersion2, 2))

       result mustBe UserAnswers(Json.obj(
        Page.toString -> Json.obj("value" -> pageValue)
      ))
    }
  }

  ".removeMemberBasedCharge" must {
    "FOR FIRST COMPILE - remove the member & set total for a member level charge being deleted if version is 1 and is not last charge" in {
      val userAnswers = UserAnswers().setOrException(MemberPage, pageValue)
        .setOrException(MemberPage2, pageValue)
      val resultFuture = Future.fromTry(service
        .removeMemberBasedCharge(MemberPage, total)(dataRequest(userAnswers), implicitly))

      whenReady(resultFuture) { result =>
        result.get(MemberPage) mustBe Some("value")
        result.get(TotalAmountPage) mustBe Some(total(UserAnswers()))
      }
    }

    "FOR FIRST COMPILE - zero the member & set total for a member level charge being deleted if version is 1 and is last charge" in {
      val pageValueAfterZeroedOut = "zeroed"
      val userAnswers = UserAnswers().setOrException(MemberPage, pageValue)
      val userAnswersAfterZeroedOut = UserAnswers().setOrException(MemberPage, pageValueAfterZeroedOut)
      when(mockDeleteChargeHelper.isLastCharge(any())).thenReturn(true)
      when(mockDeleteChargeHelper.zeroOutLastCharge(any())).thenReturn(userAnswersAfterZeroedOut)
      val resultFuture = Future.fromTry(service
        .removeMemberBasedCharge(MemberPage, total)(dataRequest(userAnswers), implicitly))

      whenReady(resultFuture) { result =>
        result.get(MemberPage) mustBe Some(pageValueAfterZeroedOut)
        result.get(TotalAmountPage) mustBe Some(total(UserAnswers()))
      }
    }

    "FOR AMENDMENT - set amended version, member version to null, status to Deleted and the page value" +
      " for a member level charge if version is 2 for a member being deleted and member AFT version is 1" in {
      val resultFuture = Future.fromTry(service
        .removeMemberBasedCharge(MemberPage, total)(dataRequest(memberUa(status="Deleted"), version = 2), implicitly))

      whenReady(resultFuture){ _ mustBe UserAnswers(Json.obj(
        "chargeType" -> Json.obj(
          "members" -> Json.arr(
            Json.obj(
              MemberPage.toString -> "value",
              "memberStatus" -> "Deleted"
            )
          ))
      )).setOrException(TotalAmountPage, total(UserAnswers()))
      }
    }


    "FOR AMENDMENT - physically remove member" +
      " for a member level charge if version is 2 and member added in this version" in {
      val resultFuture = Future.fromTry(service.removeMemberBasedCharge(MemberPage, total)(dataRequest(memberUaTwoMembers(2), version = 2), implicitly))

      whenReady(resultFuture){ _ mustBe UserAnswers(Json.obj(
        "chargeType" -> Json.obj(
          "members" -> Json.arr(Json.obj(
            MemberPage2.toString -> pageValue,
            "memberAFTVersion"-> 2,
            "memberStatus" -> "New"
          )),
          "amendedVersion" -> 2)
      )).setOrException(TotalAmountPage, total(UserAnswers()))

      }
    }
  }

  ".set" must {

    "AMENDMENT - set amended version to null and the page value for a scheme level charge being added if version is 2" in {

      val resultFuture = Future.fromTry(service.set(Page, pageValue, NormalMode, isMemberBased = false)(dataRequest(version =  2), implicitly))

      whenReady(resultFuture){ _ mustBe UserAnswers(Json.obj(
        Page.toString -> pageValue
      ))}
    }

    "FIRST COMPILE - set only the page value for a scheme level charge being added if version is 1" in {
      val resultFuture = Future.fromTry(service.set(Page, pageValue, NormalMode, isMemberBased = false)(dataRequest(), implicitly))

      whenReady(resultFuture){ _ mustBe UserAnswers(Json.obj(
        Page.toString -> pageValue
      ))}
    }

    "FIRST COMPILE - set only the page value for a member level charge being added if version is 1" in {
      val resultFuture = Future.fromTry(service.set(MemberPage, pageValue, NormalMode)(dataRequest(), implicitly))

      whenReady(resultFuture){ _ mustBe UserAnswers(Json.obj(
        "chargeType" -> Json.obj("members" -> Json.arr(Json.obj(MemberPage.toString -> "value")))
      ))}
    }

    "FIRST COMPILE - set only the page value for a member level charge being changed if version is 1" in {
      val resultFuture = Future.fromTry(service.set(MemberPage, pageValue, CheckMode)(dataRequest(), implicitly))

      whenReady(resultFuture){ _ mustBe UserAnswers(Json.obj(
        "chargeType" -> Json.obj("members" -> Json.arr(Json.obj(MemberPage.toString -> "value")))
      ))}
    }



    "AMENDMENT - set amended version, member version to null, status to New and the page value" +
      " for a scheme level charge if version is 2 for a new member being added" in {
      val resultFuture = Future.fromTry(service.set(MemberPage, pageValue, NormalMode)(dataRequest(version = 2), implicitly))

      whenReady(resultFuture){ _ mustBe UserAnswers(Json.obj(
        "chargeType" -> Json.obj(
          "members" -> Json.arr(
                    Json.obj(
                        MemberPage.toString -> "value",
                        "memberStatus" -> "New"
                    )
          )
      )))}
    }

    "AMENDMENT - set amended version, member version to null, status to Changed and the page value" +
      " for a scheme level charge if version is 2 for a member being changed after the last submission" in {
      val resultFuture = Future.fromTry(service.set(MemberPage, pageValue, CheckMode)(dataRequest(memberUa(), 2), implicitly))

      whenReady(resultFuture){ _ mustBe UserAnswers(Json.obj(
        "chargeType" -> Json.obj(
          "members" -> Json.arr(
            Json.obj(
              MemberPage.toString -> "value",
              "memberStatus" -> "Changed"
            )
          ))
      ))}
    }

    "AMENDMENT - set amended version, member version to null, status to New and the page value" +
      " for a scheme level charge if version is 2 if a member that was added after the last submission is being changed" in {
      val resultFuture = Future.fromTry(service.set(MemberPage, pageValue, CheckMode)(dataRequest(memberUa(2), 2), implicitly))

      whenReady(resultFuture){ _ mustBe UserAnswers(Json.obj(
        "chargeType" -> Json.obj(
          "members" -> Json.arr(
            Json.obj(
              MemberPage.toString -> "value",
              "memberStatus" -> "New"
            )
          ))
      ))}
    }
  }
}

object UserAnswersServiceSpec {

  case object Page extends QuestionPage[String] {
    override def path: JsPath = JsPath \ toString

    override def toString: String = "page"
  }

  case object MemberPage extends QuestionPage[String] {
    override def path: JsPath = JsPath \ "chargeType" \ "members" \ 0 \ toString

    override def toString: String = "memberPage"
  }

  case object MemberPage2 extends QuestionPage[String] {
    override def path: JsPath = JsPath \ "chargeType" \ "members" \ 1 \ toString

    override def toString: String = "memberPage"
  }

  case object TotalAmountPage extends QuestionPage[BigDecimal] {
    override def path: JsPath = JsPath \ "chargeType" \ toString

    override def toString: String = "totalChargeAmount"
  }

  val pageValue: String = "value"
  val total: UserAnswers => BigDecimal = _ => BigDecimal(100.00)
  val totalZero: UserAnswers => BigDecimal = _ => BigDecimal(0.00)

  def sessionData(version: Int): SessionData =
    SessionData(sessionId, None, SessionAccessData(version, AccessMode.PageAccessModeCompile))

  def dataRequest(ua: UserAnswers = UserAnswers(), version: Int = 1): DataRequest[AnyContent] =
    DataRequest(FakeRequest(GET, "/"), "test-internal-id", PsaId("A2100000"), ua, sessionData(version))

  val ua: UserAnswers = UserAnswers(Json.obj(Page.toString -> pageValue))



  val uaVersion2: UserAnswers = UserAnswers(Json.obj(Page.toString -> Json.obj("value" -> pageValue, "amendedVersion" -> 1)))

  def memberUa(version: Int = 1, status: String = "New"): UserAnswers = UserAnswers(Json.obj(
    "chargeType" -> Json.obj(
      "members" -> Json.arr(
        Json.obj(
          MemberPage.toString -> pageValue,
          "memberAFTVersion"-> version,
          "memberStatus" -> status
        )
      ),
      "amendedVersion" -> version)
  ))

  def memberUaTwoMembers(version: Int = 1, status: String = "New"): UserAnswers = UserAnswers(Json.obj(
    "chargeType" -> Json.obj(
      "members" -> Json.arr(
        Json.obj(
          MemberPage.toString -> pageValue,
          "memberAFTVersion"-> version,
          "memberStatus" -> status
        ),
        Json.obj(
          MemberPage2.toString -> pageValue,
          "memberAFTVersion"-> version,
          "memberStatus" -> status
        )
      ),
      "amendedVersion" -> version)
  ))
}

