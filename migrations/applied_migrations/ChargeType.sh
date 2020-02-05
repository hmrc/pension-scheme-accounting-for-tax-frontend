#!/bin/bash

echo ""
echo "Applying migration ChargeType"

echo "Adding routes to conf/app.routes"

echo "" >> ../conf/app.routes
echo "GET        /chargeType                        controllers.ChargeTypeController.onPageLoad(mode: Mode = NormalMode)" >> ../conf/app.routes
echo "POST       /chargeType                        controllers.ChargeTypeController.onSubmit(mode: Mode = NormalMode)" >> ../conf/app.routes

echo "GET        /changeChargeType                  controllers.ChargeTypeController.onPageLoad(mode: Mode = CheckMode)" >> ../conf/app.routes
echo "POST       /changeChargeType                  controllers.ChargeTypeController.onSubmit(mode: Mode = CheckMode)" >> ../conf/app.routes

echo "Adding messages to conf.messages"
echo "" >> ../conf/messages.en
echo "chargeType.title = ChargeType" >> ../conf/messages.en
echo "chargeType.heading = ChargeType" >> ../conf/messages.en
echo "chargeType.chargeType.annulaAllowance = Annual allowance charge" >> ../conf/messages.en
echo "chargeType.chargeType.authSurplus = Authorised surplus payments charge" >> ../conf/messages.en
echo "chargeType.checkYourAnswersLabel = ChargeType" >> ../conf/messages.en
echo "chargeType.error.required = Select chargeType" >> ../conf/messages.en

echo "Adding to UserAnswersEntryGenerators"
awk '/trait UserAnswersEntryGenerators/ {\
    print;\
    print "";\
    print "  implicit lazy val arbitraryChargeTypeUserAnswersEntry: Arbitrary[(ChargeTypePage.type, JsValue)] =";\
    print "    Arbitrary {";\
    print "      for {";\
    print "        page  <- arbitrary[ChargeTypePage.type]";\
    print "        value <- arbitrary[ChargeType].map(Json.toJson(_))";\
    print "      } yield (page, value)";\
    print "    }";\
    next }1' ../test/generators/UserAnswersEntryGenerators.scala > tmp && mv tmp ../test/generators/UserAnswersEntryGenerators.scala

echo "Adding to PageGenerators"
awk '/trait PageGenerators/ {\
    print;\
    print "";\
    print "  implicit lazy val arbitraryChargeTypePage: Arbitrary[ChargeTypePage.type] =";\
    print "    Arbitrary(ChargeTypePage)";\
    next }1' ../test/generators/PageGenerators.scala > tmp && mv tmp ../test/generators/PageGenerators.scala

echo "Adding to ModelGenerators"
awk '/trait ModelGenerators/ {\
    print;\
    print "";\
    print "  implicit lazy val arbitraryChargeType: Arbitrary[ChargeType] =";\
    print "    Arbitrary {";\
    print "      Gen.oneOf(ChargeType.values.toSeq)";\
    print "    }";\
    next }1' ../test/generators/ModelGenerators.scala > tmp && mv tmp ../test/generators/ModelGenerators.scala

echo "Adding to UserAnswersGenerator"
awk '/val generators/ {\
    print;\
    print "    arbitrary[(ChargeTypePage.type, JsValue)] ::";\
    next }1' ../test/generators/UserAnswersGenerator.scala > tmp && mv tmp ../test/generators/UserAnswersGenerator.scala

echo "Adding helper method to CheckYourAnswersHelper"
awk '/class CheckYourAnswersHelper/ {\
     print;\
     print "";\
     print "  def chargeType: Option[Row] = addRequiredDetailsToUserAnswers.get(ChargeTypePage) map {";\
     print "    answer =>";\
     print "      Row(";\
     print "        key     = Key(msg\"chargeType.checkYourAnswersLabel\", classes = Seq(\"govuk-!-width-one-half\")),";\
     print "        value   = Value(msg\"chargeType.$answer\"),";\
     print "        actions = List(";\
     print "          Action(";\
     print "            content            = msg\"site.edit\",";\
     print "            href               = routes.ChargeTypeController.onPageLoad(CheckMode).url,";\
     print "            visuallyHiddenText = Some(msg\"site.edit.hidden\".withArgs(msg\"chargeType.checkYourAnswersLabel\"))";\
     print "          )";\
     print "        )";\
     print "      )";\
     print "  }";\
     next }1' ../app/utils/CheckYourAnswersHelper.scala > tmp && mv tmp ../app/utils/CheckYourAnswersHelper.scala

echo "Migration ChargeType completed"
