/*
 * Copyright 2022 HM Revenue & Customs
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

import com.google.inject.Inject
import config.FrontendAppConfig
import forms.MemberDetailsFormProvider
import forms.chargeE.ChargeDetailsFormProvider
import models.UserAnswers
import models.chargeE.ChargeEDetails
import pages.chargeE.{ChargeDetailsPage, MemberDetailsPage}
import play.api.data.Form

import java.time.LocalDate

class AnnualAllowanceParser @Inject()(
                                       memberDetailsFormProvider: MemberDetailsFormProvider,
                                       chargeDetailsFormProvider: ChargeDetailsFormProvider,
                                       config: FrontendAppConfig
                                     ) extends Parser {
  //scalastyle:off magic.number
  override protected val totalFields: Int = 7

  private def memberDetailsValidation(ua: UserAnswers, index: Int, chargeFields: Array[String]): Either[ParserValidationErrors, UserAnswers] = {
    val memberDetailsForm = memberDetailsFormProvider()
    memberDetailsForm.bind(
      Map(
        "firstName" -> firstNameField(chargeFields),
        "lastName" -> lastNameField(chargeFields),
        "nino" -> ninoField(chargeFields)
      )
    ).fold(
      formWithErrors => Left(ParserValidationErrors(index, formWithErrors.errors.map(_.message))),
      value => Right(ua.setOrException(MemberDetailsPage(index), value))
    )
  }

  private def splitDayMonthYear(date:String):(String, String, String) = {
    date.split("/").toSeq match {
      case Seq(d,m,y) => Tuple3(d,m,y)
      case Seq(d,m) => Tuple3(d,m,"")
      case Seq(d) => Tuple3(d, "", "")
      case _ => Tuple3("", "", "")
    }
  }

  private def stringToBoolean(s:String): String = if (s == "yes") "true" else "false"

  private def chargeDetailsValidation(ua: UserAnswers, index: Int, chargeFields: Array[String]): Either[ParserValidationErrors, UserAnswers] = {
    val chargeDetailsForm: Form[ChargeEDetails] = chargeDetailsFormProvider(
      minimumChargeValueAllowed = BigDecimal("0.01"),
      minimumDate = config.earliestDateOfNotice
    )

    splitDayMonthYear(chargeFields(5)) match {
      case Tuple3(day, month, year) =>
        chargeDetailsForm.bind(
          Map(
            "chargeAmount" -> chargeFields(4),
            "dateNoticeReceived.day" -> day,
            "dateNoticeReceived.month" -> month,
            "dateNoticeReceived.year" -> year,
            "isPaymentMandatory" -> stringToBoolean(chargeFields(6))
          )
        ).fold(
          formWithErrors => Left(ParserValidationErrors(index, formWithErrors.errors.map(_.message))),
          value => {
            Right(ua.setOrException(ChargeDetailsPage(index), value))
          }
        )
    }


  }

  override protected def validateFields(ua: UserAnswers, index: Int, chargeFields: Array[String]): Either[ParserValidationErrors, UserAnswers] = {
    memberDetailsValidation(ua, index, chargeFields) match {
      case memberErrors@Left(memberDetailsErrors) =>
        chargeDetailsValidation(ua, index, chargeFields) match {
          case Left(chargeDetailsErrors) =>
            Left(ParserValidationErrors(memberDetailsErrors.row, memberDetailsErrors.errors ++ chargeDetailsErrors.errors))
          case _ => memberErrors
        }
      case Right(updatedUA) =>
        chargeDetailsValidation(updatedUA, index, chargeFields)
    }
  }
}
