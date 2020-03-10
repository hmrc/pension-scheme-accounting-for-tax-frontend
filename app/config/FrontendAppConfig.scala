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

import com.google.inject.Inject
import com.google.inject.Singleton
import controllers.routes
import play.api.Configuration
import play.api.i18n.Lang
import play.api.mvc.Call
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

@Singleton
class FrontendAppConfig @Inject()(configuration: Configuration, servicesConfig: ServicesConfig) {

  lazy val appName: String = configuration.get[String](path = "appName")
  lazy val authUrl: String = configuration.get[Service]("auth").baseUrl
  lazy val loginUrl: String = configuration.get[String]("urls.login")
  lazy val loginContinueUrl: String = configuration.get[String]("urls.loginContinue")
  lazy val signOutUrl = loadConfig("urls.logout")
  lazy val languageTranslationEnabled: Boolean =
    configuration.get[Boolean]("microservice.services.features.welsh-translation")
  lazy val aftUrl: String = servicesConfig.baseUrl("pension-scheme-accounting-for-tax")
  lazy val pensionSchemeUrl: String = servicesConfig.baseUrl("pensions-scheme")
  lazy val pensionsAdministratorUrl: String = servicesConfig.baseUrl("pension-administrator")
  lazy val aftFileReturn: String = s"$aftUrl${configuration.get[String](path = "urls.aftFileReturn")}"
  lazy val aftListOfVersions: String = s"$aftUrl${configuration.get[String](path = "urls.aftListOfVersions")}"
  lazy val getAftDetails: String = s"$aftUrl${configuration.get[String](path = "urls.getAFTDetails")}"
  lazy val schemeDetailsUrl: String = s"$pensionSchemeUrl${configuration.get[String](path = "urls.schemeDetails")}"
  lazy val checkAssociationUrl: String = s"$pensionSchemeUrl${configuration.get[String](path = "urls.checkPsaAssociation")}"
  lazy val managePensionsSchemeSummaryUrl: String = loadConfig("urls.schemesSummary")
  lazy val yourPensionSchemesUrl: String = loadConfig("urls.yourPensionSchemes")
  lazy val minimalPsaDetailsUrl: String = s"$pensionsAdministratorUrl${configuration.get[String](path = "urls.minimalPsaDetails")}"
  lazy val validCountryCodes: Seq[String] = configuration.get[String]("validCountryCodes").split(",").toSeq
  lazy val minimumYear: Int = configuration.get[Int]("minimumYear")
  val analyticsToken: String = configuration.get[String](s"google-analytics.token")
  val analyticsHost: String = configuration.get[String](s"google-analytics.host")
  val reportAProblemPartialUrl = s"$contactHost/contact/problem_reports_ajax?service=$contactFormServiceIdentifier"
  val reportAProblemNonJSUrl = s"$contactHost/contact/problem_reports_nonjs?service=$contactFormServiceIdentifier"
  val betaFeedbackUrl = s"$contactHost/contact/beta-feedback"
  val betaFeedbackUnauthenticatedUrl = s"$contactHost/contact/beta-feedback-unauthenticated"
  private val contactHost = configuration.get[String]("contact-frontend.host")
  private val contactFormServiceIdentifier = "pensionSchemeAccountingForTaxFrontend"

  def languageMap: Map[String, Lang] = Map(
    "english" -> Lang("en"),
    "cymraeg" -> Lang("cy")
  )

  def routeToSwitchLanguage: String => Call =
    (lang: String) => routes.LanguageSwitchController.switchToLanguage(lang)

  private def loadConfig(key: String): String =
    configuration.getOptional[String](key).getOrElse(throw new Exception(s"Missing configuration key: $key"))
}
