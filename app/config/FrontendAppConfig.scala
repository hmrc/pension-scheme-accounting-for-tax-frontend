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

package config

import com.google.inject.{Inject, Singleton}
import models.AdministratorOrPractitioner.Administrator
import models.ChargeType.toRoute
import models.requests.{DataRequest, IdentifierRequest}
import models.{AccessType, AdministratorOrPractitioner, ChargeType, JourneyType, UploadId}
import play.api.Configuration
import play.api.i18n.Lang
import play.api.mvc.Call
import uk.gov.hmrc.domain.{PsaId, PspId}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import scala.concurrent.duration.Duration

@Singleton
class FrontendAppConfig @Inject()(configuration: Configuration, servicesConfig: ServicesConfig) {

  private def loadConfig(key: String): String =
    configuration.getOptional[String](key).getOrElse(throw new Exception(s"Missing configuration key: $key"))

  private def getConfigString(key: String) = servicesConfig.getConfString(key, throw new Exception(s"Could not find config '$key'"))


  lazy val appName: String = configuration.get[String](path = "appName")

  val ifsTimeout: Duration = configuration.get[Duration]("ifs.timeout")

  val betaFeedbackUnauthenticatedUrl: String = getConfigString("contact-frontend.beta-feedback-url.unauthenticated")

  lazy val loginUrl: String = configuration.get[String]("urls.login")
  lazy val loginContinueUrl: String = configuration.get[String]("urls.loginContinue")
  lazy val signOutUrl: String = loadConfig("urls.logout")

  lazy val psaUpdateContactDetailsUrl: String = loadConfig("urls.psaUpdateContactDetails")
  lazy val pspUpdateContactDetailsUrl: String = loadConfig("urls.pspUpdateContactDetails")

  lazy val timeoutSeconds: Int = configuration.get[Int]("session.timeoutSeconds")
  lazy val countdownSeconds: Int = configuration.get[Int]("session.countdownSeconds")

  def languageMap: Map[String, Lang] = Map(
    "english" -> Lang("en"),
    "cymraeg" -> Lang("cy")
  )

  lazy val emailApiUrl: String = servicesConfig.baseUrl("email")
  lazy val emailSendForce: Boolean = configuration.getOptional[Boolean]("email.force").getOrElse(false)
  lazy val aftUrl: String = servicesConfig.baseUrl("pension-scheme-accounting-for-tax")

  def aftEmailCallback(
                        schemeAdministratorType: AdministratorOrPractitioner,
                        journeyType: JourneyType.Value,
                        requestId: String,
                        encryptedEmail: String,
                        encryptedPsaId: String
                      ) = s"$aftUrl${
    configuration.get[String](path = "urls.emailCallback")
      .format(
        if (schemeAdministratorType == Administrator) "PSA" else "PSP",
        journeyType.toString,
        requestId,
        encryptedEmail,
        encryptedPsaId
      )
  }"

  lazy val managePensionsSchemeOverviewUrl: String = Call("GET", loadConfig("urls.manage-pensions-frontend.schemesOverview")).url
  lazy val youMustContactHMRCUrl: String = loadConfig("urls.manage-pensions-frontend.youMustContactHMRC")
  lazy val pensionSchemeUrl: String = servicesConfig.baseUrl("pensions-scheme")
  lazy val pensionsAdministratorUrl: String = servicesConfig.baseUrl("pension-administrator")
  lazy val aftFileReturn: String = s"$aftUrl${configuration.get[String](path = "urls.aftFileReturn")}"
  lazy val aftListOfVersions: String = s"$aftUrl${configuration.get[String](path = "urls.aftListOfVersions")}"
  lazy val getAftDetails: String = s"$aftUrl${configuration.get[String](path = "urls.getAFTDetails")}"
  lazy val aftOverviewUrl: String = s"$aftUrl${configuration.get[String](path = "urls.aftOverview")}"
  lazy val isAftNonZero: String = s"$aftUrl${configuration.get[String](path = "urls.isAftNonZero")}"
  lazy val psaFinancialStatementUrl: String = s"$aftUrl${configuration.get[String](path = "urls.psaFinancialStatement")}"
  lazy val podsNewFinancialCredits: Boolean = configuration.getOptional[Boolean]("pods.new.financial.credits").getOrElse(false)

  def financialInfoCreditAccessSchemePsaUrl(psaId: String, srn: String): String =
    s"$aftUrl${configuration.get[String](path = "urls.financialInfoCreditAccessConnectorSchemePsa").format(psaId, srn)}"

  def financialInfoCreditAccessSchemePspUrl(pspId: String, srn: String): String =
    s"$aftUrl${configuration.get[String](path = "urls.financialInfoCreditAccessConnectorSchemePsp").format(pspId, srn)}"

  def financialInfoCreditAccessPsaUrl(psaId: String): String =
    s"$aftUrl${configuration.get[String](path = "urls.financialInfoCreditAccessConnectorPsa").format(psaId)}"

  lazy val viewPenaltiesUrl: String = configuration.get[String](path = "urls.partials.psaFinancialStatementLink")
  lazy val viewAllPenaltiesForFinancialOverviewUrl: String = configuration.get[String](path = "urls.partials.psaAllPenaltiesLink")
  lazy val viewUpcomingPenaltiesUrl: String = configuration.get[String](path = "urls.partials.upcomingPenaltiesLink")
  lazy val schemeFinancialStatementUrl: String = s"$aftUrl${configuration.get[String](path = "urls.schemeFinancialStatement")}"

  lazy val schemeDetailsUrl: String = s"$pensionSchemeUrl${configuration.get[String](path = "urls.schemeDetails")}"
  lazy val pspSchemeDetailsUrl: String = s"$pensionSchemeUrl${configuration.get[String](path = "urls.pspSchemeDetails")}"

  lazy val checkAssociationUrl: String = s"$pensionSchemeUrl${configuration.get[String](path = "urls.checkPsaAssociation")}"

  lazy val fileUploadOutcomeUrl: String = s"$aftUrl${configuration.get[String](path = "urls.fileUploadOutcome")}"

  def schemeDashboardUrl(request: DataRequest[_]): String = schemeDashboardUrl(request.psaId, request.pspId)

  def schemeDashboardUrl(request: IdentifierRequest[_]): String = schemeDashboardUrl(request.psaId, request.pspId)

  def schemeDashboardUrl(psaId: Option[PsaId], pspId: Option[PspId]): String =
    (psaId, pspId) match {
      case (Some(_), None) => managePensionsSchemeSummaryUrl
      case (None, Some(_)) => managePensionsSchemePspUrl
      case _ => managePensionsSchemeSummaryUrl
    }

  lazy val administratorOrPractitionerUrl: String = loadConfig("urls.administratorOrPractitioner")
  lazy val managePensionsSchemeSummaryUrl: String = loadConfig("urls.schemesSummary")
  lazy val delimitedPsaUrl: String = loadConfig("urls.delimitedPsa")
  lazy val managePensionsSchemePspUrl: String = loadConfig("urls.psp.schemesSummary")
  lazy val yourPensionSchemesUrl: String = loadConfig("urls.yourPensionSchemes")
  lazy val minimalPsaDetailsUrl: String = s"$pensionsAdministratorUrl${configuration.get[String](path = "urls.minimalPsaDetails")}"
  lazy val validCountryCodes: Seq[String] = configuration.get[String]("validCountryCodes").split(",").toSeq
  lazy val minimumYear: Int = configuration.get[Int]("minimumYear")

  lazy val earliestStartDate: String = configuration.get[String]("earliestStartDate")
  lazy val aftNoOfYearsDisplayed: Int = configuration.get[Int]("aftNoOfYearsDisplayed")
  lazy val fileAFTReturnTemplateId: String = configuration.get[String]("email.fileAftReturnTemplateId")
  lazy val amendAftReturnDecreaseTemplateIdId: String = configuration.get[String]("email.amendAftReturnDecreaseTemplateId")
  lazy val amendAftReturnNoChangeTemplateIdId: String = configuration.get[String]("email.amendAftReturnNoChangeTemplateId")
  lazy val amendAftReturnIncreaseTemplateIdId: String = configuration.get[String]("email.amendAftReturnIncreaseTemplateId")

  lazy val aftLoginUrl: String = s"${configuration.get[String](path = "urls.partials.aftLoginLink")}"
  lazy val aftSummaryPageUrl: String = s"${configuration.get[String](path = "urls.partials.aftSummaryPageLink")}"
  lazy val aftContinueReturnUrl: String = s"${configuration.get[String](path = "urls.partials.aftContinueReturn")}"
  lazy val aftAmendUrl: String = s"${configuration.get[String](path = "urls.partials.aftAmendLink")}"
  lazy val paymentsAndChargesUrl: String = s"${configuration.get[String](path = "urls.partials.paymentsAndChargesLogicLink")}"
  lazy val financialPaymentsAndChargesUrl: String = s"${configuration.get[String](path = "urls.partials.financialPaymentsAndChargesLogicLink")}"
  lazy val upcomingChargesUrl: String = s"${configuration.get[String](path = "urls.partials.upcomingChargesLogicLink")}"
  lazy val overdueChargesUrl: String = s"${configuration.get[String](path = "urls.partials.overdueChargesLogicLink")}"

  lazy val financialOverviewUrl: String = s"${configuration.get[String](path = "urls.partials.schemeFinancialOverviewLink")}"
  lazy val listOfSchemesUrl: String = s"$pensionSchemeUrl${configuration.get[String](path = "urls.listOfSchemes")}"
  lazy val psafinancialOverviewUrl: String = s"${configuration.get[String](path = "urls.partials.psaFinancialOverviewLink")}"
  lazy val earliestDateOfNotice: LocalDate = LocalDate
    .parse(
      configuration.get[String]("earliestDateOfNotice"),
      DateTimeFormatter.ofPattern("yyyy-MM-dd")
    )
  lazy val mccloudPsrStartDate: LocalDate = LocalDate
    .parse(
      configuration.get[String]("mccloudPsrStartDate"),
      DateTimeFormatter.ofPattern("yyyy-MM-dd")
    )

  lazy val mccloudPsrAlwaysTrueStartDate: LocalDate = LocalDate
    .parse(
      configuration.get[String]("mccloudPsrAlwaysTrueStartDate"),
      DateTimeFormatter.ofPattern("yyyy-MM-dd")
    )

  lazy val addressLookUp = s"${servicesConfig.baseUrl("address-lookup")}"

  lazy val membersPageSize: Int = configuration.get[Int]("members.pageSize")

  lazy val initiateV2Url:String            = servicesConfig.baseUrl("upscan-initiate") + "/upscan/v2/initiate"

  lazy val upScanCallBack:String   = s"${servicesConfig.baseUrl("aft-frontend")}${configuration.underlying
    .getString("urls.upscan-callback-endpoint")}"

  lazy val creditBalanceRefundLink: String = loadConfig("urls.creditBalanceRefundLink")

  def successEndpointTarget(srn: String, startDate: LocalDate, accessType:AccessType, version: Int, chargeType: ChargeType, uploadId: UploadId):String   = {
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    loadConfig("upscan.success-endpoint")
      .format(srn, formatter.format(startDate), accessType.toString, version.toString, toRoute(chargeType), uploadId.value)
  }

  def failureEndpointTarget(srn: String, startDate: LocalDate, accessType:AccessType, version: Int, chargeType: ChargeType):String   = {
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    loadConfig("upscan.failure-endpoint")
      .format(srn, formatter.format(startDate), accessType.toString, version.toString, toRoute(chargeType))
  }

  lazy val maxUploadFileSize: Int = configuration.getOptional[Int]("upscan.maxUploadFileSizeMb").getOrElse(1)

  lazy val validAnnualAllowanceNonMcCloudHeader: String = configuration.get[String]("validAnnualAllowanceNonMcCloudHeader")
  lazy val validAnnualAllowanceMcCloudHeader: String = configuration.get[String]("validAnnualAllowanceMcCloudHeader")
  lazy val validLifetimeAllowanceHeader: String = configuration.get[String]("validLifetimeAllowanceHeader")
  lazy val validLifetimeAllowanceMcCloudHeader: String = configuration.get[String]("validLifetimeAllowanceMcCloudHeader")
  lazy val validOverseasTransferHeader: String = configuration.get[String]("validOverseasTransferHeader")
}
