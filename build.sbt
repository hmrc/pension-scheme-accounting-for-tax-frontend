import play.sbt.routes.RoutesKeys
import sbt.Def
import scoverage.ScoverageKeys
import uk.gov.hmrc.DefaultBuildSettings
import uk.gov.hmrc.versioning.SbtGitVersioning.autoImport.majorVersion

lazy val appName: String = "pension-scheme-accounting-for-tax-frontend"

resolvers += "hmrc-releases" at "https://artefacts.tax.service.gov.uk/artifactory/hmrc-releases/"

lazy val root = (project in file("."))
  .enablePlugins(PlayScala, SbtAutoBuildPlugin, SbtDistributablesPlugin, SbtArtifactory)
  .settings(DefaultBuildSettings.scalaSettings: _*)
  .settings(DefaultBuildSettings.defaultSettings(): _*)
  .settings(SbtDistributablesPlugin.publishingSettings: _*)
  .settings(scalaVersion := "2.12.11")
  .settings(inConfig(Test)(testSettings): _*)
  .settings(majorVersion := 0)
  .settings(
    name := appName,
    RoutesKeys.routesImport ++= Seq("models._", "models.OptionBinder._", "models.LocalDateBinder._", "java.time.LocalDate",
      "models.financialStatement.PsaFSChargeType"),
    TwirlKeys.templateImports ++= Seq(
      "play.twirl.api.HtmlFormat",
      "play.twirl.api.HtmlFormat._",
      "uk.gov.hmrc.play.views.html.helpers._",
      "uk.gov.hmrc.play.views.html.layouts._",
      "models.Mode",
      "models.Index",
      "models.AccessType",
      "controllers.routes._"
    ),
    PlayKeys.playDefaultPort := 8206,
    ScoverageKeys.coverageExcludedFiles := "<empty>;Reverse.*;.*handlers.*;.*repositories.*;" +
      ".*BuildInfo.*;.*javascript.*;.*Routes.*;.*GuiceInjector;" +
      ".*ControllerConfiguration;.*TestController;.*LanguageSwitchController",
    ScoverageKeys.coverageMinimum := 80,
    ScoverageKeys.coverageFailOnMinimum := true,
    ScoverageKeys.coverageHighlighting := true,
    scalacOptions ++= Seq("-feature", "-deprecation"),
    libraryDependencies ++= AppDependencies(),
    retrieveManaged := true,
    evictionWarningOptions in update :=
      EvictionWarningOptions.default.withWarnScalaVersionEviction(false),
    resolvers ++= Seq(
      Resolver.bintrayRepo("hmrc", "releases"),
      Resolver.jcenterRepo
    ),
    Concat.groups := Seq(
      "javascripts/application.js" -> group(Seq("lib/govuk-frontend/govuk/all.js","lib/hmrc-frontend/hmrc/all.js",
        "javascripts/aft.js"
      ))
    ),
    uglifyCompressOptions := Seq("unused=false", "dead_code=false"),
    pipelineStages in Assets := Seq(concat,uglify)
  )

lazy val testSettings: Seq[Def.Setting[_]] = Seq(
  fork        := true,
  javaOptions ++= Seq(
    "-Dconfig.resource=test.application.conf"
  )
)
