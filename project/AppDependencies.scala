import sbt._

object AppDependencies {
  private val bootstrapVersion = "8.4.0"
  val compile = Seq(
    play.sbt.PlayImport.ws,
    "uk.gov.hmrc"                   %%  "play-conditional-form-mapping-play-30"  % "2.0.0",
    "uk.gov.hmrc"                   %%  "bootstrap-frontend-play-30"             % bootstrapVersion,
    "uk.gov.hmrc"                   %%  "play-nunjucks-play-30"                  % "1.0.0",
    "uk.gov.hmrc"                   %%  "play-nunjucks-viewmodel"                % "0.17.0-play-28",
    "org.webjars.npm"               %   "govuk-frontend"                         % "4.7.0",
    "org.webjars.npm"               %   "hmrc-frontend"                          % "1.35.2",
    "com.google.inject.extensions"  %   "guice-multibindings"                    % "4.2.3",
    "uk.gov.hmrc"                   %%  "domain-play-30"                         % "9.0.0",
    "com.univocity"                 %   "univocity-parsers"                      % "2.9.1",
    "com.fasterxml.jackson.module"  %%  "jackson-module-scala"                   % "2.16.1",
    "org.typelevel"                 %%  "cats-core"                              % "2.9.0"
  )

  val test: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"                 %% "bootstrap-test-play-30"     % bootstrapVersion,
    "org.scalatest"               %% "scalatest"                  % "3.2.15",
    "org.scalatestplus.play"      %% "scalatestplus-play"         % "5.1.0",
    "org.scalatestplus"           %% "mockito-4-6"                % "3.2.15.0",
    "org.scalatestplus"           %% "scalacheck-1-17"            % "3.2.15.0",
    "org.pegdown"                 %  "pegdown"                    % "1.6.0",
    "com.vladsch.flexmark"        %  "flexmark-all"               % "0.64.6"
  ).map(_ % Test)

  def apply(): Seq[ModuleID] = compile ++ test
}
