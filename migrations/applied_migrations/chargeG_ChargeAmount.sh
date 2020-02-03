#!/bin/bash

echo ""
echo "Applying migration chargeG_ChargeAmount"

echo "Adding routes to conf/app.routes"

echo "" >> ../conf/app.routes
echo "GET        /:srn/new-return/chargeG.ChargeAmount                        controllers.chargeG.ChargeAmountController.onPageLoad(mode: Mode = NormalMode, srn: String)" >> ../conf/app.routes
echo "POST       /:srn/new-return/chargeG.ChargeAmount                        controllers.chargeG.ChargeAmountController.onSubmit(mode: Mode = NormalMode, srn: String)" >> ../conf/app.routes

echo "GET        /:srn/new-return/changechargeG.ChargeAmount                  controllers.chargeG.ChargeAmountController.onPageLoad(mode: Mode = CheckMode, srn: String)" >> ../conf/app.routes
echo "POST       /:srn/new-return/changechargeG.ChargeAmount                  controllers.chargeG.ChargeAmountController.onSubmit(mode: Mode = CheckMode, srn: String)" >> ../conf/app.routes

echo "Adding messages to conf.messages"
echo "" >> ../conf/messages.en
echo "chargeG.ChargeAmount.title = chargeG.ChargeAmount" >> ../conf/messages.en
echo "chargeG.ChargeAmount.heading = chargeG.ChargeAmount" >> ../conf/messages.en
echo "chargeG.ChargeAmount.checkYourAnswersLabel = chargeG.ChargeAmount" >> ../conf/messages.en
echo "chargeG.ChargeAmount.error.required = Enter chargeG.ChargeAmount" >> ../conf/messages.en
echo "chargeG.ChargeAmount.error.length = chargeG.ChargeAmount must be 100 characters or less" >> ../conf/messages.en

echo "Adding to UserAnswersEntryGenerators"
awk '/trait UserAnswersEntryGenerators/ {\
    print;\
    print "";\
    print "  implicit lazy val arbitrarychargeG.ChargeAmountUserAnswersEntry: Arbitrary[(chargeG.ChargeAmountPage.type, JsValue)] =";\
    print "    Arbitrary {";\
    print "      for {";\
    print "        page  <- arbitrary[chargeG.ChargeAmountPage.type]";\
    print "        value <- arbitrary[String].suchThat(_.nonEmpty).map(Json.toJson(_))";\
    print "      } yield (page, value)";\
    print "    }";\
    next }1' ../test/generators/UserAnswersEntryGenerators.scala > tmp && mv tmp ../test/generators/UserAnswersEntryGenerators.scala

echo "Adding to PageGenerators"
awk '/trait PageGenerators/ {\
    print;\
    print "";\
    print "  implicit lazy val arbitrarychargeG.ChargeAmountPage: Arbitrary[chargeG.ChargeAmountPage.type] =";\
    print "    Arbitrary(chargeG.ChargeAmountPage)";\
    next }1' ../test/generators/PageGenerators.scala > tmp && mv tmp ../test/generators/PageGenerators.scala

echo "Adding to UserAnswersGenerator"
awk '/val generators/ {\
    print;\
    print "    arbitrary[(chargeG.ChargeAmountPage.type, JsValue)] ::";\
    next }1' ../test/generators/UserAnswersGenerator.scala > tmp && mv tmp ../test/generators/UserAnswersGenerator.scala

echo "Adding helper method to CheckYourAnswersHelper"
awk '/class CheckYourAnswersHelper/ {\
     print;\
     print "";\
     print "  def chargeG.ChargeAmount: Option[Row] = addRequiredDetailsToUserAnswers.get(chargeG.ChargeAmountPage) map {";\
     print "    answer =>";\
     print "      Row(";\
     print "        key     = Key(msg\"chargeG.ChargeAmount.checkYourAnswersLabel\", classes = Seq(\"govuk-!-width-one-half\")),";\
     print "        value   = Value(lit\"$answer\"),";\
     print "        actions = List(";\
     print "          Action(";\
     print "            content            = msg\"site.edit\",";\
     print "            href               = controllers.routes.chargeG.ChargeAmountController.onPageLoad(CheckMode, srn).url,";\
     print "            visuallyHiddenText = Some(msg\"site.edit.hidden\".withArgs(msg\"chargeG.ChargeAmount.checkYourAnswersLabel\"))";\
     print "          )";\
     print "        )";\
     print "      )";\
     print "  }";\
     next }1' ../app/utils/CheckYourAnswersHelper.scala > tmp && mv tmp ../app/utils/CheckYourAnswersHelper.scala

echo "Migration chargeG_ChargeAmount completed"
