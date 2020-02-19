#!/bin/bash

echo ""
echo "Applying migration ConfirmSubmitAFTReturn"

echo "Adding routes to conf/app.routes"

echo "" >> ../conf/app.routes
echo "GET        /:srn/new-return/confirmSubmitAFTReturn                        controllers.ConfirmSubmitAFTReturnController.onPageLoad(mode: Mode = NormalMode, srn: String)" >> ../conf/app.routes
echo "POST       /:srn/new-return/confirmSubmitAFTReturn                        controllers.ConfirmSubmitAFTReturnController.onSubmit(mode: Mode = NormalMode, srn: String)" >> ../conf/app.routes

echo "GET        /:srn/new-return/changeConfirmSubmitAFTReturn                  controllers.ConfirmSubmitAFTReturnController.onPageLoad(mode: Mode = CheckMode, srn: String)" >> ../conf/app.routes
echo "POST       /:srn/new-return/changeConfirmSubmitAFTReturn                  controllers.ConfirmSubmitAFTReturnController.onSubmit(mode: Mode = CheckMode, srn: String)" >> ../conf/app.routes

echo "Adding messages to conf.messages"
echo "" >> ../conf/messages.en
echo "confirmSubmitAFTReturn.title = confirmSubmitAFTReturn" >> ../conf/messages.en
echo "confirmSubmitAFTReturn.heading = confirmSubmitAFTReturn" >> ../conf/messages.en
echo "confirmSubmitAFTReturn.checkYourAnswersLabel = confirmSubmitAFTReturn" >> ../conf/messages.en
echo "confirmSubmitAFTReturn.error.required = Select yes if confirmSubmitAFTReturn" >> ../conf/messages.en

echo "Adding to UserAnswersEntryGenerators"
awk '/trait UserAnswersEntryGenerators/ {\
    print;\
    print "";\
    print "  implicit lazy val arbitraryConfirmSubmitAFTReturnUserAnswersEntry: Arbitrary[(ConfirmSubmitAFTReturnPage.type, JsValue)] =";\
    print "    Arbitrary {";\
    print "      for {";\
    print "        page  <- arbitrary[ConfirmSubmitAFTReturnPage.type]";\
    print "        value <- arbitrary[Boolean].map(Json.toJson(_))";\
    print "      } yield (page, value)";\
    print "    }";\
    next }1' ../test/generators/UserAnswersEntryGenerators.scala > tmp && mv tmp ../test/generators/UserAnswersEntryGenerators.scala

echo "Adding to PageGenerators"
awk '/trait PageGenerators/ {\
    print;\
    print "";\
    print "  implicit lazy val arbitraryConfirmSubmitAFTReturnPage: Arbitrary[ConfirmSubmitAFTReturnPage.type] =";\
    print "    Arbitrary(ConfirmSubmitAFTReturnPage)";\
    next }1' ../test/generators/PageGenerators.scala > tmp && mv tmp ../test/generators/PageGenerators.scala

echo "Adding to UserAnswersGenerator"
awk '/val generators/ {\
    print;\
    print "    arbitrary[(ConfirmSubmitAFTReturnPage.type, JsValue)] ::";\
    next }1' ../test/generators/UserAnswersGenerator.scala > tmp && mv tmp ../test/generators/UserAnswersGenerator.scala

echo "Adding helper method to CheckYourAnswersHelper"
awk '/class CheckYourAnswersHelper/ {\
     print;\
     print "";\
     print "  def confirmSubmitAFTReturn: Option[Row] = userAnswers.get(ConfirmSubmitAFTReturnPage) map {";\
     print "    answer =>";\
     print "      Row(";\
     print "        key     = Key(msg\"confirmSubmitAFTReturn.checkYourAnswersLabel\", classes = Seq(\"govuk-!-width-one-half\")),";\
     print "        value   = Value(yesOrNo(answer)),";\
     print "        actions = List(";\
     print "          Action(";\
     print "            content            = msg\"site.edit\",";\
     print "            href               = controllers.routes.ConfirmSubmitAFTReturnController.onPageLoad(CheckMode, srn).url,";\
     print "            visuallyHiddenText = Some(msg\"site.edit.hidden\".withArgs(msg\"confirmSubmitAFTReturn.checkYourAnswersLabel\"))";\
     print "          )";\
     print "        )";\
     print "      )";\
     print "  }";\
     next }1' ../app/utils/CheckYourAnswersHelper.scala > tmp && mv tmp ../app/utils/CheckYourAnswersHelper.scala

echo "Migration ConfirmSubmitAFTReturn completed"
