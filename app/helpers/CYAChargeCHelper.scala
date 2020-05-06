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

package helpers

import java.time.LocalDate

import models.LocalDateBinder._
import models.SponsoringEmployerType.{SponsoringEmployerTypeIndividual, SponsoringEmployerTypeOrganisation}
import models.chargeC.{ChargeCDetails, SponsoringEmployerAddress, SponsoringOrganisationDetails}
import models.{CheckMode, SponsoringEmployerType}
import play.api.i18n.Messages
import uk.gov.hmrc.viewmodels.SummaryList.{Action, Key, Row, Value}
import uk.gov.hmrc.viewmodels.Text.Literal
import uk.gov.hmrc.viewmodels.{Content, Html, _}

class CYAChargeCHelper(srn: String, startDate: LocalDate)(implicit messages: Messages) extends CYAHelper {

  def chargeCEmployerDetails(index: Int,
                             sponsorDetails: Either[models.MemberDetails, SponsoringOrganisationDetails]
                            )(implicit messages: Messages): Seq[Row] =
    sponsorDetails.fold(
      individual => chargeCIndividualDetails(index, individual),
      organisation => chargeCOrganisationDetails(index, organisation)
    )

  private def getEmployerName(index: Int,
                              sponsorDetails: Either[models.MemberDetails, SponsoringOrganisationDetails]): String =
    sponsorDetails.fold(
      individual => individual.fullName,
      organisation => organisation.name
    )

  def chargeCWhichTypeOfSponsoringEmployer(index: Int, answer: SponsoringEmployerType): Row =
    Row(
      key = Key(msg"chargeC.whichTypeOfSponsoringEmployer.checkYourAnswersLabel", classes = Seq("govuk-!-width-one-half")),
      value = Value(typeOfSponsoringEmployer(answer)),
      actions = List(
        Action(
          content = msg"site.edit",
          href = controllers.chargeC.routes.WhichTypeOfSponsoringEmployerController.onPageLoad(CheckMode, srn, startDate, index).url,
          visuallyHiddenText = Some(msg"chargeC.whichTypeOfSponsoringEmployer.visuallyHidden.checkYourAnswersLabel"))
      )
    )

  def chargeCIndividualDetails(index: Int, answer: models.MemberDetails): Seq[Row] = {
    Seq(
      Row(
        key = Key(msg"chargeC.sponsoringIndividualName.checkYourAnswersLabel", classes = Seq("govuk-!-width-one-half")),
        value = Value(lit"${answer.fullName}"),
        actions = List(
          Action(
            content = msg"site.edit",
            href = controllers.chargeC.routes.SponsoringIndividualDetailsController.onPageLoad(CheckMode, srn, startDate, index).url,
            visuallyHiddenText = Some(msg"chargeC.sponsoringIndividualName.visuallyHidden.checkYourAnswersLabel")
          )
        )
      ),
      Row(
        key = Key(msg"chargeC.sponsoringIndividualNino.checkYourAnswersLabel".withArgs(answer.fullName), classes = Seq("govuk-!-width-one-half")),
        value = Value(lit"${answer.nino}"),
        actions = List(
          Action(
            content = msg"site.edit",
            href = controllers.chargeC.routes.SponsoringIndividualDetailsController.onPageLoad(CheckMode, srn, startDate, index).url,
            visuallyHiddenText = Some(msg"chargeC.sponsoringIndividualNino.visuallyHidden.checkYourAnswersLabel".withArgs(answer.fullName))
          )
        )
      )
    )
  }

  def chargeCOrganisationDetails(index: Int, answer: SponsoringOrganisationDetails): Seq[Row] = {
    Seq(
      Row(
        key = Key(msg"chargeC.sponsoringOrganisationName.checkYourAnswersLabel", classes = Seq("govuk-!-width-one-half")),
        value = Value(lit"${answer.name}"),
        actions = List(
          Action(
            content = msg"site.edit",
            href = controllers.chargeC.routes.SponsoringOrganisationDetailsController.onPageLoad(CheckMode, srn, startDate, index).url,
            visuallyHiddenText = Some(msg"chargeC.sponsoringOrganisationName.visuallyHidden.checkYourAnswersLabel")
          )
        )
      ),
      Row(
        key = Key(msg"chargeC.sponsoringOrganisationCrn.checkYourAnswersLabel".withArgs(answer.name), classes = Seq("govuk-!-width-one-half")),
        value = Value(lit"${answer.crn}"),
        actions = List(
          Action(
            content = msg"site.edit",
            href = controllers.chargeC.routes.SponsoringOrganisationDetailsController.onPageLoad(CheckMode, srn, startDate, index).url,
            visuallyHiddenText = Some(msg"chargeC.sponsoringOrganisationCrn.visuallyHidden.checkYourAnswersLabel".withArgs(answer.name))
          )
        )
      )
    )
  }

  def chargeCAddress(index: Int,
                     address: SponsoringEmployerAddress,
                     sponsorDetails: Either[models.MemberDetails, SponsoringOrganisationDetails])
                    (implicit messages: Messages): Row =
    Row(
      key = Key(msg"chargeC.sponsoringEmployerAddress.checkYourAnswersLabel".withArgs(getEmployerName(index, sponsorDetails)),
        classes = Seq("govuk-!-width-one-half")),
      value = Value(addressAnswer(address)),
      actions = List(
        Action(
          content = msg"site.edit",
          href = controllers.chargeC.routes.SponsoringEmployerAddressController.onPageLoad(CheckMode, srn, startDate, index).url,
          visuallyHiddenText = Some(msg"chargeC.sponsoringEmployerAddress.checkYourAnswersLabel".withArgs(getEmployerName(index, sponsorDetails)))
        )
      )
    )

  def chargeCChargeDetails(index: Int, chargeDetails: ChargeCDetails): Seq[Row] =
    Seq(
      Row(
        key = Key(msg"chargeC.paymentDate.checkYourAnswersLabel", classes = Seq("govuk-!-width-one-half")),
        value = Value(Literal(chargeDetails.paymentDate.format(FormatHelper.dateFormatter)), classes = Seq("govuk-!-width-one-quarter")),
        actions = List(
          Action(
            content = msg"site.edit",
            href = controllers.chargeC.routes.ChargeDetailsController.onPageLoad(CheckMode, srn, startDate, index).url,
            visuallyHiddenText = Some(msg"chargeC.paymentDate.visuallyHidden.checkYourAnswersLabel")
          )
        )
      ),
      Row(
        key = Key(msg"chargeC.totalTaxDue.checkYourAnswersLabel", classes = Seq("govuk-!-width-one-half")),
        value = Value(Literal(s"${FormatHelper.formatCurrencyAmountAsString(chargeDetails.amountTaxDue)}"), classes = Seq("govuk-!-width-one-quarter")),
        actions = List(
          Action(
            content = msg"site.edit",
            href = controllers.chargeC.routes.ChargeDetailsController.onPageLoad(CheckMode, srn, startDate, index).url,
            visuallyHiddenText = Some(msg"chargeC.totalTaxDue.visuallyHidden.checkYourAnswersLabel")
          )
        )
      )
    )

  private def typeOfSponsoringEmployer(answer: SponsoringEmployerType): Content =
    answer match {
      case SponsoringEmployerTypeIndividual => msg"chargeC.whichTypeOfSponsoringEmployer.individual"
      case SponsoringEmployerTypeOrganisation => msg"chargeC.whichTypeOfSponsoringEmployer.organisation"
    }

  private def addressAnswer(addr: SponsoringEmployerAddress)(implicit messages: Messages): Html = {
    def addrLineToHtml(l: String): String = s"""<span class="govuk-!-display-block">$l</span>"""

    def optionalAddrLineToHtml(optionalAddrLine: Option[String]): String = optionalAddrLine match {
      case Some(l) => addrLineToHtml(l)
      case None => ""
    }

    Html(
      addrLineToHtml(addr.line1) +
        addrLineToHtml(addr.line2) +
        optionalAddrLineToHtml(addr.line3) +
        optionalAddrLineToHtml(addr.line4) +
        optionalAddrLineToHtml(addr.postcode) +
        addrLineToHtml(messages("country." + addr.country))
    )
  }

}
