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

package models

import play.api.data.Form
import play.api.i18n.Messages
import play.api.mvc.JavascriptLiteral
import uk.gov.hmrc.govukfrontend.views.viewmodels.content.Text
import uk.gov.hmrc.govukfrontend.views.viewmodels.radios.RadioItem

sealed trait ChargeType

object ChargeType extends Enumerable.Implicits {

  implicit val jsLiteral: JavascriptLiteral[ChargeType] = new JavascriptLiteral[ChargeType] {
    override def to(value: ChargeType): String = value match {
      case ChargeTypeAnnualAllowance => "ChargeTypeAnnualAllowance"
      case ChargeTypeLifetimeAllowance => "ChargeTypeLifetimeAllowance"
      case ChargeTypeOverseasTransfer => "ChargeTypeOverseasTransfer"
      case _ => throw new RuntimeException(s"Unimplemented charge type: $value")
    }
  }

  def toRoute(value: ChargeType): String = value match {
    case ChargeTypeAnnualAllowance => "annual-allowance-charge"
    case ChargeTypeLifetimeAllowance => "lifetime-allowance-charge"
    case ChargeTypeOverseasTransfer => "overseas-transfer-charge"
    case _ => throw new RuntimeException(s"Unimplemented charge type: $value")
  }

  case object ChargeTypeAnnualAllowance extends WithName("annualAllowance") with ChargeType

  case object ChargeTypeAuthSurplus extends WithName("authSurplus") with ChargeType

  case object ChargeTypeDeRegistration extends WithName("deRegistration") with ChargeType

  case object ChargeTypeLifetimeAllowance extends WithName("lifeTimeAllowance") with ChargeType

  case object ChargeTypeOverseasTransfer extends WithName("overseasTransfer") with ChargeType

  case object ChargeTypeShortService extends WithName("shortService") with ChargeType

  case object ChargeTypeLumpSumDeath extends WithName("lumpSumDeath") with ChargeType

  val values: Seq[ChargeType] = Seq(
    ChargeTypeAnnualAllowance,
    ChargeTypeAuthSurplus,
    ChargeTypeDeRegistration,
    ChargeTypeLifetimeAllowance,
    ChargeTypeOverseasTransfer,
    ChargeTypeShortService,
    ChargeTypeLumpSumDeath
  )

  def radios(form: Form[_])(implicit messages: Messages): Seq[RadioItem] = {

    val field = form("value")
    val items = Seq(
      RadioItem(Text(messages("chargeType.radio.annualAllowance")), id = Some("value"), value = Some(ChargeTypeAnnualAllowance.toString)),
      RadioItem(Text(messages("chargeType.radio.authSurplus")), id = Some("value_1"), value = Some(ChargeTypeAuthSurplus.toString)),
      RadioItem(Text(messages("chargeType.radio.deRegistration")), id = Some("value_2"), value = Some(ChargeTypeDeRegistration.toString)),
      RadioItem(Text(messages("chargeType.radio.lifeTimeAllowance")), id = Some("value_3"), value = Some(ChargeTypeLifetimeAllowance.toString)),
      RadioItem(Text(messages("chargeType.radio.overseasTransfer")), id = Some("value_4"), value = Some(ChargeTypeOverseasTransfer.toString)),
      RadioItem(Text(messages("chargeType.radio.shortService")), id = Some("value_5"), value = Some(ChargeTypeShortService.toString)),
      RadioItem(Text(messages("chargeType.radio.lumpSumDeath")), id = Some("value_6"), value = Some(ChargeTypeLumpSumDeath.toString))
    )

    items
  }

  def fileUploadText(chargeType: ChargeType)(implicit messages: Messages): String = {
    chargeType match {
      case ChargeTypeAnnualAllowance => messages("fileupload.annualAllowance")
      case ChargeTypeLifetimeAllowance => messages("fileupload.lifeTimeAllowance")
      case ChargeTypeOverseasTransfer => messages("fileupload.overseasTransfer")
      case ChargeTypeAuthSurplus => messages("fileupload.authSurplus")
      case ChargeTypeDeRegistration => messages("fileupload.deRegistration")
      case ChargeTypeShortService => messages("fileupload.shortService")
      case ChargeTypeLumpSumDeath => messages("fileupload.lumpSumDeath")
      case _ => chargeType.toString
    }
  }

  implicit val enumerable: Enumerable[ChargeType] =
    Enumerable(values.map(v => v.toString -> v): _*)

  def chargeBaseNode(chargeType: ChargeType): String =
    chargeType match {
      case ChargeTypeAnnualAllowance => "chargeEDetails"
      case ChargeTypeLifetimeAllowance => "chargeDDetails"
      case _ => throw new RuntimeException(s"Invalid charge type $chargeType")
    }

  def chargeTypeNode(chargeType: ChargeType): String =
    chargeType match {
      case ChargeTypeAnnualAllowance    => "chargeEDetails"
      case ChargeTypeLifetimeAllowance  => "chargeDDetails"
      case ChargeTypeOverseasTransfer   => "chargeGDetails"
      case ChargeTypeAuthSurplus        => "chargeCDetails"
      case ChargeTypeDeRegistration     => "chargeFDetails"
      case ChargeTypeShortService       => "chargeADetails"
      case ChargeTypeLumpSumDeath       => "chargeBDetails"
      case _ => throw new RuntimeException(s"Invalid charge type $chargeType")
    }

  val chargeTypeValues: Seq[String] = values.map(chargeTypeNode)
}
