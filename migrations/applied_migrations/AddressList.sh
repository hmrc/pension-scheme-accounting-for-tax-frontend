#!/bin/bash

echo ""
echo "Applying migration AddressList"

echo "Adding routes to conf/app.routes"

echo "" >> ../conf/app.routes
echo "GET        /:srn/new-return/employerAddressResults                        controllers.chargeC.SponsoringEmployerAddressResultsController.onPageLoad(mode: Mode = NormalMode, srn: String)" >> ../conf/app.routes
echo "POST       /:srn/new-return/employerAddressResults                        controllers.chargeC.SponsoringEmployerAddressResultsController.onSubmit(mode: Mode = NormalMode, srn: String)" >> ../conf/app.routes

echo "GET        /:srn/new-return/changeAddressList                  controllers.chargeC.SponsoringEmployerAddressResultsController.onPageLoad(mode: Mode = CheckMode, srn: String)" >> ../conf/app.routes
echo "POST       /:srn/new-return/changeAddressList                  controllers.chargeC.SponsoringEmployerAddressResultsController.onSubmit(mode: Mode = CheckMode, srn: String)" >> ../conf/app.routes

echo "Adding messages to conf.messages"
echo "" >> ../conf/messages.en
echo "employerAddressResults.title = employerAddressResults" >> ../conf/messages.en
echo "employerAddressResults.heading = employerAddressResults" >> ../conf/messages.en
echo "employerAddressResults.option1 = Option 1" >> ../conf/messages.en
echo "employerAddressResults.option2 = Option 2" >> ../conf/messages.en
echo "employerAddressResults.checkYourAnswersLabel = employerAddressResults" >> ../conf/messages.en
echo "employerAddressResults.error.required = Select employerAddressResults" >> ../conf/messages.en

echo "Adding to UserAnswersEntryGenerators"
awk '/trait UserAnswersEntryGenerators/ {\
    print;\
    print "";\
    print "  implicit lazy val arbitraryAddressListUserAnswersEntry: Arbitrary[(SponsoringEmployerAddressResultsPage.type, JsValue)] =";\
    print "    Arbitrary {";\
    print "      for {";\
    print "        page  <- arbitrary[SponsoringEmployerAddressResultsPage.type]";\
    print "        value <- arbitrary[AddressList].map(Json.toJson(_))";\
    print "      } yield (page, value)";\
    print "    }";\
    next }1' ../test/generators/UserAnswersEntryGenerators.scala > tmp && mv tmp ../test/generators/UserAnswersEntryGenerators.scala

echo "Adding to PageGenerators"
awk '/trait PageGenerators/ {\
    print;\
    print "";\
    print "  implicit lazy val arbitraryAddressListPage: Arbitrary[SponsoringEmployerAddressResultsPage.type] =";\
    print "    Arbitrary(SponsoringEmployerAddressResultsPage)";\
    next }1' ../test/generators/PageGenerators.scala > tmp && mv tmp ../test/generators/PageGenerators.scala

echo "Adding to ModelGenerators"
awk '/trait ModelGenerators/ {\
    print;\
    print "";\
    print "  implicit lazy val arbitraryAddressList: Arbitrary[AddressList] =";\
    print "    Arbitrary {";\
    print "      Gen.oneOf(AddressList.values.toSeq)";\
    print "    }";\
    next }1' ../test/generators/ModelGenerators.scala > tmp && mv tmp ../test/generators/ModelGenerators.scala

echo "Adding to UserAnswersGenerator"
awk '/val generators/ {\
    print;\
    print "    arbitrary[(SponsoringEmployerAddressResultsPage.type, JsValue)] ::";\
    next }1' ../test/generators/UserAnswersGenerator.scala > tmp && mv tmp ../test/generators/UserAnswersGenerator.scala

echo "Adding helper method to CheckYourAnswersHelper"
awk '/class CheckYourAnswersHelper/ {\
     print;\
     print "";\
     print "  def employerAddressResults: Option[Row] = userAnswers.get(SponsoringEmployerAddressResultsPage) map {";\
     print "    answer =>";\
     print "      Row(";\
     print "        key     = Key(msg\"employerAddressResults.checkYourAnswersLabel\", classes = Seq(\"govuk-!-width-one-half\")),";\
     print "        value   = Value(msg\"employerAddressResults.$answer\"),";\
     print "        actions = List(";\
     print "          Action(";\
     print "            content            = msg\"site.edit\",";\
     print "            href               = controllers.routes.SponsoringEmployerAddressResultsController.onPageLoad(CheckMode, srn).url,";\
     print "            visuallyHiddenText = Some(msg\"site.edit.hidden\".withArgs(msg\"employerAddressResults.checkYourAnswersLabel\"))";\
     print "          )";\
     print "        )";\
     print "      )";\
     print "  }";\
     next }1' ../app/utils/CheckYourAnswersHelper.scala > tmp && mv tmp ../app/utils/CheckYourAnswersHelper.scala

echo "Migration AddressList completed"
