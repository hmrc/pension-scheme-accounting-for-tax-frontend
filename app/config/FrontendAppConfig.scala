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

package config

import java.time.LocalDate
import java.time.format.DateTimeFormatter

import com.google.inject.{Inject, Singleton}
import controllers.routes
import play.api.Configuration
import play.api.i18n.Lang
import play.api.mvc.Call
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

@Singleton
class FrontendAppConfig @Inject() (configuration: Configuration, servicesConfig: ServicesConfig) {

  private def loadConfig(key: String): String =
    configuration.getOptional[String](key).getOrElse(throw new Exception(s"Missing configuration key: $key"))

  private def baseUrl(serviceName: String) = {
    val protocol = configuration.getOptional[String](s"microservice.services.$serviceName.protocol").getOrElse("http")
    val host = configuration.get[String](s"microservice.services.$serviceName.host")
    val port = configuration.get[String](s"microservice.services.$serviceName.port")
    s"$protocol://$host:$port"
  }

  private def getConfigString(key: String) = servicesConfig.getConfString(key, throw new Exception(s"Could not find config '$key'"))

  lazy val contactHost: String = baseUrl("contact-frontend")

  private val contactFormServiceIdentifier = "pensionSchemeAccountingForTaxFrontend"

  lazy val appName: String = configuration.get[String](path = "appName")
  val analyticsToken: String = configuration.get[String](s"google-analytics.token")
  val analyticsHost: String = configuration.get[String](s"google-analytics.host")

  val reportAProblemPartialUrl: String = getConfigString("contact-frontend.report-problem-url.with-js")
  val reportAProblemNonJSUrl: String = getConfigString("contact-frontend.report-problem-url.non-js")
  val betaFeedbackUrl: String = getConfigString("contact-frontend.beta-feedback-url.authenticated")
  val betaFeedbackUnauthenticatedUrl: String = getConfigString("contact-frontend.beta-feedback-url.unauthenticated")

  lazy val authUrl: String = configuration.get[Service]("auth").baseUrl
  lazy val loginUrl: String = configuration.get[String]("urls.login")
  lazy val loginContinueUrl: String = configuration.get[String]("urls.loginContinue")
  lazy val signOutUrl: String = loadConfig("urls.logout")

  lazy val timeoutSeconds: String = configuration.get[String]("session.timeoutSeconds")
  lazy val countdownSeconds: String = configuration.get[String]("session.countdownSeconds")

  lazy val languageTranslationEnabled: Boolean =
    configuration.get[Boolean]("microservice.services.features.welsh-translation")

  def languageMap: Map[String, Lang] = Map(
    "english" -> Lang("en"),
    "cymraeg" -> Lang("cy")
  )

  lazy val emailApiUrl: String = servicesConfig.baseUrl("email")
  lazy val emailSendForce: Boolean = configuration.getOptional[Boolean]("email.force").getOrElse(false)
  lazy val aftUrl: String = servicesConfig.baseUrl("pension-scheme-accounting-for-tax")
  def aftEmailCallback(journeyType: String, requestId: String, encryptedEmail: String, encryptedPsaId: String) =
    s"$aftUrl${configuration.get[String](path = "urls.emailCallback").format(journeyType, requestId, encryptedEmail, encryptedPsaId)}"

  lazy val pensionSchemeUrl: String = servicesConfig.baseUrl("pensions-scheme")
  lazy val pensionsAdministratorUrl:String = servicesConfig.baseUrl("pension-administrator")
  lazy val aftFileReturn: String = s"$aftUrl${configuration.get[String](path = "urls.aftFileReturn")}"
  lazy val aftListOfVersions: String = s"$aftUrl${configuration.get[String](path = "urls.aftListOfVersions")}"
  lazy val getAftDetails: String = s"$aftUrl${configuration.get[String](path = "urls.getAFTDetails")}"
  lazy val aftOverviewUrl: String = s"$aftUrl${configuration.get[String](path = "urls.aftOverview")}"
  lazy val isAftNonZero: String = s"$aftUrl${configuration.get[String](path = "urls.isAftNonZero")}"
  lazy val psaFinancialStatementUrl: String = s"$aftUrl${configuration.get[String](path = "urls.psaFinancialStatement")}"
  lazy val viewPenaltiesUrl: String = configuration.get[String](path = "urls.partials.psaFinancialStatementLink")
    .format(configuration.get[Int](path = "minimumYear"))
  lazy val schemeFinancialStatementUrl: String = s"$aftUrl${configuration.get[String](path = "urls.schemeFinancialStatement")}"

  lazy val schemeDetailsUrl: String = s"$pensionSchemeUrl${configuration.get[String](path = "urls.schemeDetails")}"
  lazy val checkAssociationUrl: String = s"$pensionSchemeUrl${configuration.get[String](path = "urls.checkPsaAssociation")}"
  lazy val managePensionsSchemeSummaryUrl: String = loadConfig("urls.schemesSummary")
  lazy val yourPensionSchemesUrl: String = loadConfig("urls.yourPensionSchemes")
  lazy val minimalPsaDetailsUrl: String = s"$pensionsAdministratorUrl${configuration.get[String](path = "urls.minimalPsaDetails")}"
  lazy val validCountryCodes: Seq[String] = configuration.get[String]("validCountryCodes").split(",").toSeq
  lazy val minimumYear: Int = configuration.get[Int]("minimumYear")

  lazy val earliestStartDate: String = configuration.get[String]("earliestStartDate")
  lazy val earliestEndDate: String = configuration.get[String]("earliestEndDate")
  lazy val aftNoOfYearsDisplayed: Int = configuration.get[Int]("aftNoOfYearsDisplayed")
  lazy val fileAFTReturnTemplateId: String = configuration.get[String]("email.fileAftReturnTemplateId")
  lazy val amendAftReturnDecreaseTemplateIdId: String = configuration.get[String]("email.amendAftReturnDecreaseTemplateId")
  lazy val amendAftReturnNoChangeTemplateIdId: String = configuration.get[String]("email.amendAftReturnNoChangeTemplateId")
  lazy val amendAftReturnIncreaseTemplateIdId: String = configuration.get[String]("email.amendAftReturnIncreaseTemplateId")

  lazy val aftLoginUrl: String = s"${configuration.get[String](path = "urls.partials.aftLoginLink")}"
  lazy val aftSummaryPageUrl: String = s"${configuration.get[String](path = "urls.partials.aftSummaryPageLink")}"
  lazy val aftReturnHistoryUrl: String = s"${configuration.get[String](path = "urls.partials.aftReturnHistoryLink")}"
  lazy val aftContinueReturnUrl: String = s"${configuration.get[String](path = "urls.partials.aftContinueReturn")}"
  lazy val aftAmendUrl: String = s"${configuration.get[String](path = "urls.partials.aftAmendLink")}"
  lazy val paymentsAndChargesUrl: String = s"${configuration.get[String](path = "urls.partials.paymentsAndChargesLink")}"

  lazy val listOfSchemesUrl: String = s"$pensionSchemeUrl${configuration.get[String](path = "urls.listOfSchemes")}"
  lazy val listOfSchemesIFUrl: String = s"$pensionSchemeUrl${configuration.get[String](path = "urls.if-listOfSchemes")}"
  lazy val earliestDateOfNotice: LocalDate = LocalDate
    .parse(
      configuration.get[String]("earliestDateOfNotice"),
      DateTimeFormatter.ofPattern("yyyy-MM-dd")
    )

  lazy val addressLookUp = s"${servicesConfig.baseUrl("address-lookup")}"

  def routeToSwitchLanguage: String => Call =
    (lang: String) => routes.LanguageSwitchController.switchToLanguage(lang)

  lazy val isFSEnabled: Boolean = configuration.get[Boolean]("features.is-fs-enabled")
  lazy val listOfSchemesIFEnabled: Boolean = configuration.get[Boolean]("features.list-of-schemes-IF-enabled")
}
