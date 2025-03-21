import play.sbt.routes.RoutesKeys
import sbt.Def
import scoverage.ScoverageKeys
import uk.gov.hmrc.DefaultBuildSettings
import uk.gov.hmrc.versioning.SbtGitVersioning.autoImport.majorVersion

lazy val appName: String = "pension-scheme-accounting-for-tax-frontend"

lazy val root = (project in file("."))
  .disablePlugins(JUnitXmlReportPlugin) //Required to prevent https://github.com/scalatest/scalatest/issues/1427
  .enablePlugins(PlayScala, SbtDistributablesPlugin)
  .settings(DefaultBuildSettings.scalaSettings: _*)
  .settings(DefaultBuildSettings.defaultSettings(): _*)
  .settings(inConfig(Test)(testSettings): _*)
  .settings(
    scalaVersion := "2.13.16",
    Test / parallelExecution := true,
    majorVersion := 0,
    name := appName,
    RoutesKeys.routesImport ++= Seq("models._", "models.OptionBinder._", "models.LocalDateBinder._", "java.time.LocalDate",
      "models.financialStatement.PsaFSChargeType", "models.financialStatement.PenaltyType._", "models.ChargeType._"),
    TwirlKeys.templateImports ++= Seq(
      "play.twirl.api.HtmlFormat",
      "play.twirl.api.HtmlFormat._",
      "viewmodels.govuk.all._",
      "viewmodels.govuk.all._",
      "uk.gov.hmrc.govukfrontend.views.html.components._",
      "uk.gov.hmrc.hmrcfrontend.views.Implicits._",
      "uk.gov.hmrc.hmrcfrontend.views.html.components._",
      "uk.gov.hmrc.hmrcfrontend.views.html.helpers._",
      "models.Mode",
      "models.Index",
      "models.AccessType",
      "models.ChargeType",
      "models.ChargeType.ChargeTypeAnnualAllowance",
      "models.ChargeType.ChargeTypeLifetimeAllowance",
      "models.ChargeType.ChargeTypeOverseasTransfer",
      "controllers.routes._"
    ),
    PlayKeys.playDefaultPort := 8206,
    ScoverageKeys.coverageExcludedFiles := "<empty>;Reverse.*;.*handlers.*;.*repositories.*;" +
      ".*BuildInfo.*;.*javascript.*;.*Routes.*;.*GuiceInjector;" +
      ".*ControllerConfiguration;.*TestController;.*LanguageSwitchController",
    ScoverageKeys.coverageMinimumStmtTotal := 80,
    ScoverageKeys.coverageFailOnMinimum := true,
    ScoverageKeys.coverageHighlighting := true,
    scalacOptions ++= Seq("-feature", "-deprecation"),
    scalacOptions += "-Wconf:src=routes/.*:s",
    scalacOptions += "-Wconf:src=html/.*:s",
    libraryDependencies ++= AppDependencies(),
    retrieveManaged := true,
    resolvers ++= Seq(
      Resolver.jcenterRepo
    ),
    Concat.groups := Seq(
      "javascripts/application.js" -> group(Seq("lib/govuk-frontend/dist/govuk/all.bundle.js", "lib/hmrc-frontend/hmrc/all.js",
        "javascripts/aft.js"
      ))
    ),
    uglifyCompressOptions := Seq("unused=false", "dead_code=false"),
    // Removed uglify due to node 20 compile issues.
    // Suspected cause minification of already minified location-autocomplete.min.js -Pavel Vjalicin
    Assets / pipelineStages := Seq(concat),
  )

lazy val testSettings: Seq[Def.Setting[_]] = Seq(
  fork := true,
  javaOptions ++= Seq(
    "-Dconfig.resource=test.application.conf"
  )
)

