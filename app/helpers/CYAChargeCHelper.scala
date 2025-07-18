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

package helpers

import models.LocalDateBinder._
import models.SponsoringEmployerType.{SponsoringEmployerTypeIndividual, SponsoringEmployerTypeOrganisation}
import models.chargeC.{ChargeCDetails, SponsoringEmployerAddress, SponsoringOrganisationDetails}
import models.{AccessType, CheckMode, SponsoringEmployerType}
import play.api.i18n.Messages
import uk.gov.hmrc.govukfrontend.views.viewmodels.content.{HtmlContent, Text}
import uk.gov.hmrc.govukfrontend.views.viewmodels.summarylist.{ActionItem, Actions, Key, SummaryListRow, Value}

import java.time.LocalDate

class CYAChargeCHelper(srn: String, startDate: LocalDate, accessType: AccessType, version: Int)(implicit messages: Messages) extends CYAHelper {

  def chargeCEmployerDetails(index: Int,
                             sponsorDetails: Either[models.MemberDetails, SponsoringOrganisationDetails]
                            ): Seq[SummaryListRow] =
    sponsorDetails.fold(
      individual => chargeCIndividualDetails(index, individual),
      organisation => chargeCOrganisationDetails(index, organisation)
    )

  private def getEmployerName(sponsorDetails: Either[models.MemberDetails, SponsoringOrganisationDetails]): String =
    sponsorDetails.fold(
      individual => individual.fullName,
      organisation => organisation.name
    )

  def chargeCWhichTypeOfSponsoringEmployer(index: Int, answer: SponsoringEmployerType): SummaryListRow =
    SummaryListRow(
      key = Key(Text(messages("chargeC.whichTypeOfSponsoringEmployer.checkYourAnswersLabel")), classes = "govuk-!-width-one-half"),
      value = Value(typeOfSponsoringEmployer(answer)),
      actions = Some(
        Actions(
          items = Seq(ActionItem(
            content = HtmlContent(s"<span  aria-hidden=true >${messages("site.edit")}</span>"),
            href = controllers.chargeC.routes.WhichTypeOfSponsoringEmployerController.onPageLoad(CheckMode, srn, startDate, accessType, version, index).url,
            visuallyHiddenText = Some(
              messages("site.edit") + " " + messages("chargeC.whichTypeOfSponsoringEmployer.visuallyHidden.checkYourAnswersLabel")
            )
          ))
      )
    )
    )

  def chargeCIndividualDetails(index: Int, answer: models.MemberDetails): Seq[SummaryListRow] = {
    Seq(
      SummaryListRow(
        key = Key(Text(messages("chargeC.sponsoringIndividualDetails.checkYourAnswersLabel")), classes = "govuk-!-width-one-half"),
        value = Value(HtmlContent(s"""<p class="govuk-body">${answer.fullName}</p>
                                     |<p class="govuk-body">${answer.nino}</p>""".stripMargin)),
        actions = Some(
          Actions(
            items = Seq(ActionItem(
              content = HtmlContent(s"<span  aria-hidden=true >${messages("site.edit")}</span>"),
              href = controllers.chargeC.routes.SponsoringIndividualDetailsController.onPageLoad(CheckMode, srn, startDate, accessType, version, index).url,
              visuallyHiddenText = Some(
                messages("site.edit") + " " + messages("chargeC.sponsoringIndividualDetails.visuallyHidden.checkYourAnswersLabel")
              )
            ))
          )
        )
      )
    )
  }

  def chargeCOrganisationDetails(index: Int, answer: SponsoringOrganisationDetails): Seq[SummaryListRow] = {
    Seq(
      SummaryListRow(
        key = Key(Text(messages("chargeC.sponsoringOrganisationDetails.checkYourAnswersLabel")), classes = "govuk-!-width-one-half"),
        value = Value(HtmlContent(s"""<p class="govuk-body">${answer.name}</p>
                                     |<p class="govuk-body">${answer.crn}</p>""".stripMargin)),
        actions = Some(
          Actions(
            items = Seq(ActionItem(
              content = HtmlContent(s"<span  aria-hidden=true >${messages("site.edit")}</span>"),
              href = controllers.chargeC.routes.SponsoringOrganisationDetailsController.onPageLoad(CheckMode, srn, startDate, accessType, version, index).url,
              visuallyHiddenText = Some(
                messages("site.edit") + " " + messages("chargeC.sponsoringOrganisationDetails.visuallyHidden.checkYourAnswersLabel")
              )
            ))
          )
        )
      )
    )
  }

  def chargeCAddress(index: Int,
                     address: SponsoringEmployerAddress,
                     sponsorDetails: Either[models.MemberDetails, SponsoringOrganisationDetails])
                    (implicit messages: Messages): SummaryListRow =
    SummaryListRow(
      key = Key(Text(messages("chargeC.sponsoringEmployerAddress.checkYourAnswersLabel", getEmployerName(sponsorDetails))),
        classes = "govuk-!-width-one-half"),
      value = Value(addressAnswer(address)),
      actions = Some(
        Actions(
          items = Seq(ActionItem(
            content = HtmlContent(s"<span  aria-hidden=true >${messages("site.edit")}</span>"),
            href = controllers.chargeC.routes.SponsoringEmployerAddressController.onPageLoad(CheckMode, srn, startDate, accessType, version, index).url,
            visuallyHiddenText = Some(
              messages("site.edit") + " " + messages("chargeC.sponsoringEmployerAddress.checkYourAnswersLabel", getEmployerName(sponsorDetails))
            )
          ))
        )
      )
    )

  def chargeCChargeDetails(index: Int, chargeDetails: ChargeCDetails): Seq[SummaryListRow] =
    Seq(
      SummaryListRow(
        key = Key(Text(messages("chargeC.paymentDetails.checkYourAnswersLabel")), classes = "govuk-!-width-one-half"),
        value = Value(HtmlContent(s"""<p class="govuk-body">${chargeDetails.paymentDate.format(FormatHelper.dateFormatter)}</p>
                                     |<p class="govuk-body">${FormatHelper.formatCurrencyAmountAsString(chargeDetails.amountTaxDue)}</p>""".stripMargin),
          classes = "govuk-!-width-one-quarter"),
        actions = Some(
          Actions(
            items = Seq(ActionItem(
              content = HtmlContent(s"<span  aria-hidden=true >${messages("site.edit")}</span>"),
              href = controllers.chargeC.routes.ChargeDetailsController.onPageLoad(CheckMode, srn, startDate, accessType, version, index).url,
              visuallyHiddenText = Some(
                messages("site.edit") + " " + messages("chargeC.paymentDetails.visuallyHidden.checkYourAnswersLabel")
              )
            ))
          )
        )
      )
    )

  private def typeOfSponsoringEmployer(answer: SponsoringEmployerType): Text =
    answer match {
      case SponsoringEmployerTypeIndividual => Text(messages("chargeC.whichTypeOfSponsoringEmployer.individual"))
      case SponsoringEmployerTypeOrganisation => Text(messages("chargeC.whichTypeOfSponsoringEmployer.organisation"))
    }

  private def addressAnswer(addr: SponsoringEmployerAddress)(implicit messages: Messages): HtmlContent = {
    def addrLineToHtml(l: String): String = s"""<span class="govuk-!-display-block">$l</span>"""

    def optionalAddrLineToHtml(optionalAddrLine: Option[String]): String = optionalAddrLine match {
      case Some(l) => addrLineToHtml(l)
      case None => ""
    }

    HtmlContent(
      addrLineToHtml(addr.line1) +
        optionalAddrLineToHtml(addr.line2) +
        addrLineToHtml(addr.townOrCity) +
        optionalAddrLineToHtml(addr.county) +
        optionalAddrLineToHtml(addr.postcode) +
        addrLineToHtml(messages("country." + addr.country))
    )
  }

}
