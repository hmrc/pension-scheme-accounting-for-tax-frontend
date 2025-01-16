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

  private def getEmployerName(index: Int,
                              sponsorDetails: Either[models.MemberDetails, SponsoringOrganisationDetails]): String =
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
        key = Key(Text(messages("chargeC.sponsoringIndividualName.checkYourAnswersLabel")), classes = "govuk-!-width-one-half"),
        value = Value(Text(s"${answer.fullName}")),
        actions = Some(
          Actions(
            items = Seq(ActionItem(
              content = HtmlContent(s"<span  aria-hidden=true >${messages("site.edit")}</span>"),
              href = controllers.chargeC.routes.SponsoringIndividualDetailsController.onPageLoad(CheckMode, srn, startDate, accessType, version, index).url,
              visuallyHiddenText = Some(
                messages("site.edit") + " " + messages("chargeC.sponsoringIndividualName.visuallyHidden.checkYourAnswersLabel")
              )
            ))
          )
        )
      ),
      SummaryListRow(
        key = Key(Text(messages("chargeC.sponsoringIndividualNino.checkYourAnswersLabel", answer.fullName)), classes = "govuk-!-width-one-half"),
        value = Value(Text(s"${answer.nino}")),
        actions = Some(
          Actions(
            items = Seq(ActionItem(
              content = HtmlContent(s"<span  aria-hidden=true >${messages("site.edit")}</span>"),
              href = controllers.chargeC.routes.SponsoringIndividualDetailsController.onPageLoad(CheckMode, srn, startDate, accessType, version, index).url,
              visuallyHiddenText = Some(
                messages("site.edit") + " " + messages("chargeC.sponsoringIndividualNino.visuallyHidden.checkYourAnswersLabel",answer.fullName)
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
        key = Key(Text(messages("chargeC.sponsoringOrganisationName.checkYourAnswersLabel")), classes = "govuk-!-width-one-half"),
        value = Value(Text(s"${answer.name}")),
        actions = Some(
          Actions(
            items = Seq(ActionItem(
              content = HtmlContent(s"<span  aria-hidden=true >${messages("site.edit")}</span>"),
              href = controllers.chargeC.routes.SponsoringOrganisationDetailsController.onPageLoad(CheckMode, srn, startDate, accessType, version, index).url,
              visuallyHiddenText = Some(
                messages("site.edit") + " " + messages("chargeC.sponsoringOrganisationName.visuallyHidden.checkYourAnswersLabel")
              )
            ))
          )
        )
      ),
      SummaryListRow(
        key = Key(Text(messages("chargeC.sponsoringOrganisationCrn.checkYourAnswersLabel", answer.name)), classes = "govuk-!-width-one-half"),
        value = Value(Text(s"${answer.crn}")),
        actions = Some(
          Actions(
            items = Seq(ActionItem(
              content = HtmlContent(s"<span  aria-hidden=true >${messages("site.edit")}</span>"),
              href = controllers.chargeC.routes.SponsoringOrganisationDetailsController.onPageLoad(CheckMode, srn, startDate, accessType, version, index).url,
              visuallyHiddenText = Some(
                messages("site.edit") + " " + messages("chargeC.sponsoringOrganisationCrn.visuallyHidden.checkYourAnswersLabel", answer.name)
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
      key = Key(Text(messages("chargeC.sponsoringEmployerAddress.checkYourAnswersLabel", getEmployerName(index, sponsorDetails))),
        classes = "govuk-!-width-one-half"),
      value = Value(addressAnswer(address)),
      actions = Some(
        Actions(
          items = Seq(ActionItem(
            content = HtmlContent(s"<span  aria-hidden=true >${messages("site.edit")}</span>"),
            href = controllers.chargeC.routes.SponsoringEmployerAddressController.onPageLoad(CheckMode, srn, startDate, accessType, version, index).url,
            visuallyHiddenText = Some(
              messages("site.edit") + " " + messages("chargeC.sponsoringEmployerAddress.checkYourAnswersLabel", getEmployerName(index, sponsorDetails))
            )
          ))
        )
      )
    )

  def chargeCChargeDetails(index: Int, chargeDetails: ChargeCDetails): Seq[SummaryListRow] =
    Seq(
      SummaryListRow(
        key = Key(Text(messages("chargeC.paymentDate.checkYourAnswersLabel")), classes = "govuk-!-width-one-half"),
        value = Value(Text(chargeDetails.paymentDate.format(FormatHelper.dateFormatter)), classes = "govuk-!-width-one-quarter"),
        actions = Some(
          Actions(
            items = Seq(ActionItem(
              content = HtmlContent(s"<span  aria-hidden=true >${messages("site.edit")}</span>"),
              href = controllers.chargeC.routes.ChargeDetailsController.onPageLoad(CheckMode, srn, startDate, accessType, version, index).url,
              visuallyHiddenText = Some(
                messages("site.edit") + " " + messages("chargeC.paymentDate.visuallyHidden.checkYourAnswersLabel")
              )
            ))
          )
        )
      ),
      SummaryListRow(
        key = Key(Text(messages("chargeC.totalTaxDue.checkYourAnswersLabel")), classes = "govuk-!-width-one-half"),
        value = Value(Text(s"${FormatHelper.formatCurrencyAmountAsString(chargeDetails.amountTaxDue)}"), classes = "govuk-!-width-one-quarter"),
        actions = Some(
          Actions(
            items = Seq(ActionItem(
              content = HtmlContent(s"<span  aria-hidden=true >${messages("site.edit")}</span>"),
              href = controllers.chargeC.routes.ChargeDetailsController.onPageLoad(CheckMode, srn, startDate, accessType, version, index).url,
              visuallyHiddenText = Some(
                messages("site.edit") + " " + messages("chargeC.totalTaxDue.visuallyHidden.checkYourAnswersLabel")
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
        addrLineToHtml(addr.line2) +
        optionalAddrLineToHtml(addr.line3) +
        optionalAddrLineToHtml(addr.line4) +
        optionalAddrLineToHtml(addr.postcode) +
        addrLineToHtml(messages("country." + addr.country))
    )
  }

}
