#!/bin/bash

echo ""
echo "Applying migration AFTSummary"

echo "Adding routes to conf/app.routes"

echo "" >> ../conf/app.routes
echo "GET        /aFTSummary                        controllers.AFTSummaryController.onPageLoad(mode: Mode = NormalMode)" >> ../conf/app.routes
echo "POST       /aFTSummary                        controllers.AFTSummaryController.onSubmit(mode: Mode = NormalMode)" >> ../conf/app.routes

echo "GET        /changeAFTSummary                  controllers.AFTSummaryController.onPageLoad(mode: Mode = CheckMode)" >> ../conf/app.routes
echo "POST       /changeAFTSummary                  controllers.AFTSummaryController.onSubmit(mode: Mode = CheckMode)" >> ../conf/app.routes

echo "Adding messages to conf.messages"
echo "" >> ../conf/messages.en
echo "aFTSummary.title = aFTSummary" >> ../conf/messages.en
echo "aFTSummary.heading = aFTSummary" >> ../conf/messages.en
echo "aFTSummary.checkYourAnswersLabel = aFTSummary" >> ../conf/messages.en
echo "aft.summary.error.required = Select yes if aFTSummary" >> ../conf/messages.en

echo "Adding to UserAnswersEntryGenerators"
awk '/trait UserAnswersEntryGenerators/ {\
    print;\
    print "";\
    print "  implicit lazy val arbitraryAFTSummaryUserAnswersEntry: Arbitrary[(AFTSummaryPage.type, JsValue)] =";\
    print "    Arbitrary {";\
    print "      for {";\
    print "        page  <- arbitrary[AFTSummaryPage.type]";\
    print "        value <- arbitrary[Boolean].map(Json.toJson(_))";\
    print "      } yield (page, value)";\
    print "    }";\
    next }1' ../test/generators/UserAnswersEntryGenerators.scala > tmp && mv tmp ../test/generators/UserAnswersEntryGenerators.scala

echo "Adding to PageGenerators"
awk '/trait PageGenerators/ {\
    print;\
    print "";\
    print "  implicit lazy val arbitraryAFTSummaryPage: Arbitrary[AFTSummaryPage.type] =";\
    print "    Arbitrary(AFTSummaryPage)";\
    next }1' ../test/generators/PageGenerators.scala > tmp && mv tmp ../test/generators/PageGenerators.scala

echo "Adding to UserAnswersGenerator"
awk '/val generators/ {\
    print;\
    print "    arbitrary[(AFTSummaryPage.type, JsValue)] ::";\
    next }1' ../test/generators/UserAnswersGenerator.scala > tmp && mv tmp ../test/generators/UserAnswersGenerator.scala

echo "Adding helper method to CheckYourAnswersHelper"
awk '/class CheckYourAnswersHelper/ {\
     print;\
     print "";\
     print "  def aFTSummary: Option[Row] = userAnswers.get(AFTSummaryPage) map {";\
     print "    answer =>";\
     print "      Row(";\
     print "        key     = Key(msg\"aFTSummary.checkYourAnswersLabel\", classes = Seq(\"govuk-!-width-one-half\")),";\
     print "        value   = Value(yesOrNo(answer)),";\
     print "        actions = List(";\
     print "          Action(";\
     print "            content            = msg\"site.edit\",";\
     print "            href               = routes.AFTSummaryController.onPageLoad(CheckMode).url,";\
     print "            visuallyHiddenText = Some(msg\"site.edit.hidden\".withArgs(msg\"aFTSummary.checkYourAnswersLabel\"))";\
     print "          )";\
     print "        )";\
     print "      )";\
     print "  }";\
     next }1' ../app/utils/CheckYourAnswersHelper.scala > tmp && mv tmp ../app/utils/CheckYourAnswersHelper.scala

echo "Migration AFTSummary completed"
