import sbt._

object AppDependencies {
  import play.core.PlayVersion

  val compile = Seq(
    play.sbt.PlayImport.ws,
    "uk.gov.hmrc"                   %%  "play-conditional-form-mapping"  % "1.9.0-play-28",
    "uk.gov.hmrc"                   %%  "bootstrap-frontend-play-28"     % "5.16.0",
    "uk.gov.hmrc"                   %%  "play-nunjucks"                  % "0.40.0-play-28",
    "uk.gov.hmrc"                   %%  "play-nunjucks-viewmodel"        % "0.15.0-play-28",
    "org.webjars.npm"               %   "govuk-frontend"                 % "3.5.0",
    "org.webjars.npm"               %   "hmrc-frontend"                  % "1.19.0",
    "com.google.inject.extensions"  %   "guice-multibindings"            % "4.2.2",
    "uk.gov.hmrc"                   %%  "domain"                         % "6.2.0-play-28"
  )

  val test = Seq(
    "org.scalatest"               %% "scalatest"          % "3.2.3",
    "org.scalatestplus.play"      %% "scalatestplus-play" % "5.1.0",
    "org.pegdown"                 %  "pegdown"            % "1.6.0",
    "org.jsoup"                   %  "jsoup"              % "1.10.3",
    "com.typesafe.play"           %% "play-test"          % PlayVersion.current,
    "org.mockito"                 % "mockito-core"        % "4.0.0",
    "org.mockito"                 %% "mockito-scala"      % "1.17.5",
    "org.scalacheck"              %% "scalacheck"         % "1.15.2",
    "org.scalatestplus"           %% "scalatestplus-scalacheck"   % "3.1.0.0-RC2",
    "com.github.tomakehurst"      %  "wiremock-jre8"      % "2.26.0",
    "com.vladsch.flexmark"        % "flexmark-all"        % "0.36.8"
  ).map(_ % Test)

  def apply(): Seq[ModuleID] = compile ++ test
}
