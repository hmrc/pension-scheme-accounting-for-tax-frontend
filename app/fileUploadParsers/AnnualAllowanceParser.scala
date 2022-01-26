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
import models.MemberDetails
import models.chargeE.ChargeEDetails
import pages.chargeE.{ChargeDetailsPage, MemberDetailsPage}
import play.api.data.Form
import play.api.libs.json.Json

class AnnualAllowanceParser @Inject()(
                                       memberDetailsFormProvider: MemberDetailsFormProvider,
                                       chargeDetailsFormProvider: ChargeDetailsFormProvider,
                                       config: FrontendAppConfig
                                     ) extends Parser {
  //scalastyle:off magic.number
  override protected val totalFields: Int = 7

  private def memberDetailsValidation(index: Int, chargeFields: Array[String]): Either[ParserValidationErrors, MemberDetails] = {
    val memberDetailsForm = memberDetailsFormProvider()
    memberDetailsForm.bind(
      Map(
        "firstName" -> firstNameField(chargeFields),
        "lastName" -> lastNameField(chargeFields),
        "nino" -> ninoField(chargeFields)
      )
    ).fold(
      formWithErrors => Left(ParserValidationErrors(index, formWithErrors.errors.map(_.message))),
      value => Right(value)
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

  private def stringToBoolean(s:String): String =
    s match {
      case "yes" => "true"
      case "no" => "false"
      case _ => s
    }

  private def chargeDetailsValidation(index: Int, chargeFields: Array[String]): Either[ParserValidationErrors, ChargeEDetails] = {
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
            Right(value)
          }
        )
    }


  }

  override protected def validateFields(index: Int, chargeFields: Array[String]): Either[ParserValidationErrors, Seq[CommitItem]] = {
    memberDetailsValidation(index, chargeFields) match {
      case Left(memberDetailsErrors) =>
        chargeDetailsValidation(index, chargeFields) match {
          case Left(chargeDetailsErrors) =>
            Left(ParserValidationErrors(memberDetailsErrors.row, memberDetailsErrors.errors ++ chargeDetailsErrors.errors))
          case _ => Left(memberDetailsErrors)
        }

      case Right(memberDetails) =>
        chargeDetailsValidation(index, chargeFields) match {
          case Left(errors) => Left(errors)
          case Right(chargeDetails) =>
          Right(
            Seq(
              CommitItem(MemberDetailsPage(index).path, Json.toJson(memberDetails)),
              CommitItem(ChargeDetailsPage(index).path, Json.toJson(chargeDetails))
            )
          )
        }
    }
  }
}
