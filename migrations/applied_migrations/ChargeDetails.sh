#!/bin/bash

echo ""
echo "Applying migration ChargeDetails"

echo "Adding routes to conf/app.routes"

echo "" >> ../conf/app.routes
echo "GET        /:srn/new-return/chargeDetails                  controllers.chargeG.ChargeDetailsController.onPageLoad(mode: Mode = NormalMode, srn: String)" >> ../conf/app.routes
echo "POST       /:srn/new-return/chargeDetails                  controllers.chargeG.ChargeDetailsController.onSubmit(mode: Mode = NormalMode, srn: String)" >> ../conf/app.routes

echo "GET        /:srn/new-return/changeChargeDetails                        controllers.chargeG.ChargeDetailsController.onPageLoad(mode: Mode = CheckMode, srn: String)" >> ../conf/app.routes
echo "POST       /:srn/new-return/changeChargeDetails                        controllers.chargeG.ChargeDetailsController.onSubmit(mode: Mode = CheckMode, srn: String)" >> ../conf/app.routes

echo "Adding messages to conf.messages"
echo "" >> ../conf/messages.en
echo "chargeDetails.title = ChargeDetails" >> ../conf/messages.en
echo "chargeDetails.heading = ChargeDetails" >> ../conf/messages.en
echo "chargeDetails.hint = For example, 12 11 2007" >> ../conf/messages.en
echo "chargeDetails.checkYourAnswersLabel = ChargeDetails" >> ../conf/messages.en
echo "chargeDetails.error.required.all = Enter the chargeDetails" >> ../conf/messages.en
echo "chargeDetails.error.required.two = The chargeDetails" must include {0} and {1} >> ../conf/messages.en
echo "chargeDetails.error.required = The chargeDetails must include {0}" >> ../conf/messages.en
echo "chargeDetails.error.invalid = Enter a real ChargeDetails" >> ../conf/messages.en

echo "Adding to UserAnswersEntryGenerators"
awk '/trait UserAnswersEntryGenerators/ {\
    print;\
    print "";\
    print "  implicit lazy val arbitraryChargeDetailsUserAnswersEntry: Arbitrary[(ChargeDetailsPage.type, JsValue)] =";\
    print "    Arbitrary {";\
    print "      for {";\
    print "        page  <- arbitrary[ChargeDetailsPage.type]";\
    print "        value <- arbitrary[Int].map(Json.toJson(_))";\
    print "      } yield (page, value)";\
    print "    }";\
    next }1' ../test/generators/UserAnswersEntryGenerators.scala > tmp && mv tmp ../test/generators/UserAnswersEntryGenerators.scala

echo "Adding to PageGenerators"
awk '/trait PageGenerators/ {\
    print;\
    print "";\
    print "  implicit lazy val arbitraryChargeDetailsPage: Arbitrary[ChargeDetailsPage.type] =";\
    print "    Arbitrary(ChargeDetailsPage)";\
    next }1' ../test/generators/PageGenerators.scala > tmp && mv tmp ../test/generators/PageGenerators.scala

echo "Adding to UserAnswersGenerator"
awk '/val generators/ {\
    print;\
    print "    arbitrary[(ChargeDetailsPage.type, JsValue)] ::";\
    next }1' ../test/generators/UserAnswersGenerator.scala > tmp && mv tmp ../test/generators/UserAnswersGenerator.scala

echo "Adding helper method to CheckYourAnswersHelper"
awk '/class CheckYourAnswersHelper/ {\
     print;\
     print "";\
     print "  def chargeDetails: Option[Row] = userAnswers.get(ChargeDetailsPage) map {";\
     print "    answer =>";\
     print "      Row(";\
     print "        key     = Key(msg\"chargeDetails.checkYourAnswersLabel\", classes = Seq(\"govuk-!-width-one-half\")),";\
     print "        value   = Value(Literal(answer.format(dateFormatter))),";\
     print "        actions = List(";\
     print "          Action(";\
     print "            content            = msg\"site.edit\",";\
     print "            href               = controllers.routes.ChargeDetailsController.onPageLoad(CheckMode, srn).url,";\
     print "            visuallyHiddenText = Some(msg\"site.edit.hidden\".withArgs(msg\"chargeDetails.checkYourAnswersLabel\"))";\
     print "          )";\
     print "        )";\
     print "      )";\
     print "  }";\
     next }1' ../app/utils/CheckYourAnswersHelper.scala > tmp && mv tmp ../app/utils/CheckYourAnswersHelper.scala

echo "Migration ChargeDetails completed"
