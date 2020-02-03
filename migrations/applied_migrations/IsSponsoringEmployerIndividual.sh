#!/bin/bash

echo ""
echo "Applying migration IsSponsoringEmployerIndividual"

echo "Adding routes to conf/app.routes"

echo "" >> ../conf/app.routes
echo "GET        /:srn/new-return/isSponsoringEmployerIndividual                        controllers.chargeC.IsSponsoringEmployerIndividualController.onPageLoad(mode: Mode = NormalMode, srn: String)" >> ../conf/app.routes
echo "POST       /:srn/new-return/isSponsoringEmployerIndividual                        controllers.chargeC.IsSponsoringEmployerIndividualController.onSubmit(mode: Mode = NormalMode, srn: String)" >> ../conf/app.routes

echo "GET        /:srn/new-return/changeIsSponsoringEmployerIndividual                  controllers.chargeC.IsSponsoringEmployerIndividualController.onPageLoad(mode: Mode = CheckMode, srn: String)" >> ../conf/app.routes
echo "POST       /:srn/new-return/changeIsSponsoringEmployerIndividual                  controllers.chargeC.IsSponsoringEmployerIndividualController.onSubmit(mode: Mode = CheckMode, srn: String)" >> ../conf/app.routes

echo "Adding messages to conf.messages"
echo "" >> ../conf/messages.en
echo "isSponsoringEmployerIndividual.title = isSponsoringEmployerIndividual" >> ../conf/messages.en
echo "isSponsoringEmployerIndividual.heading = isSponsoringEmployerIndividual" >> ../conf/messages.en
echo "isSponsoringEmployerIndividual.checkYourAnswersLabel = isSponsoringEmployerIndividual" >> ../conf/messages.en
echo "isSponsoringEmployerIndividual.error.required = Select yes if isSponsoringEmployerIndividual" >> ../conf/messages.en

echo "Adding to UserAnswersEntryGenerators"
awk '/trait UserAnswersEntryGenerators/ {\
    print;\
    print "";\
    print "  implicit lazy val arbitraryIsSponsoringEmployerIndividualUserAnswersEntry: Arbitrary[(IsSponsoringEmployerIndividualPage.type, JsValue)] =";\
    print "    Arbitrary {";\
    print "      for {";\
    print "        page  <- arbitrary[IsSponsoringEmployerIndividualPage.type]";\
    print "        value <- arbitrary[Boolean].map(Json.toJson(_))";\
    print "      } yield (page, value)";\
    print "    }";\
    next }1' ../test/generators/UserAnswersEntryGenerators.scala > tmp && mv tmp ../test/generators/UserAnswersEntryGenerators.scala

echo "Adding to PageGenerators"
awk '/trait PageGenerators/ {\
    print;\
    print "";\
    print "  implicit lazy val arbitraryIsSponsoringEmployerIndividualPage: Arbitrary[IsSponsoringEmployerIndividualPage.type] =";\
    print "    Arbitrary(IsSponsoringEmployerIndividualPage)";\
    next }1' ../test/generators/PageGenerators.scala > tmp && mv tmp ../test/generators/PageGenerators.scala

echo "Adding to UserAnswersGenerator"
awk '/val generators/ {\
    print;\
    print "    arbitrary[(IsSponsoringEmployerIndividualPage.type, JsValue)] ::";\
    next }1' ../test/generators/UserAnswersGenerator.scala > tmp && mv tmp ../test/generators/UserAnswersGenerator.scala

echo "Adding helper method to CheckYourAnswersHelper"
awk '/class CheckYourAnswersHelper/ {\
     print;\
     print "";\
     print "  def isSponsoringEmployerIndividual: Option[Row] = addRequiredDetailsToUserAnswers.get(IsSponsoringEmployerIndividualPage) map {";\
     print "    answer =>";\
     print "      Row(";\
     print "        key     = Key(msg\"isSponsoringEmployerIndividual.checkYourAnswersLabel\", classes = Seq(\"govuk-!-width-one-half\")),";\
     print "        value   = Value(yesOrNo(answer)),";\
     print "        actions = List(";\
     print "          Action(";\
     print "            content            = msg\"site.edit\",";\
     print "            href               = controllers.routes.IsSponsoringEmployerIndividualController.onPageLoad(CheckMode, srn).url,";\
     print "            visuallyHiddenText = Some(msg\"site.edit.hidden\".withArgs(msg\"isSponsoringEmployerIndividual.checkYourAnswersLabel\"))";\
     print "          )";\
     print "        )";\
     print "      )";\
     print "  }";\
     next }1' ../app/utils/CheckYourAnswersHelper.scala > tmp && mv tmp ../app/utils/CheckYourAnswersHelper.scala

echo "Migration IsSponsoringEmployerIndividual completed"
