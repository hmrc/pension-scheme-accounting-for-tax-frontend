#!/bin/bash

echo ""
echo "Applying migration AddressList"

echo "Adding routes to conf/app.routes"

echo "" >> ../conf/app.routes
echo "GET        /:srn/new-return/addressList                        controllers.chargeC.AddressListController.onPageLoad(mode: Mode = NormalMode, srn: String)" >> ../conf/app.routes
echo "POST       /:srn/new-return/addressList                        controllers.chargeC.AddressListController.onSubmit(mode: Mode = NormalMode, srn: String)" >> ../conf/app.routes

echo "GET        /:srn/new-return/changeAddressList                  controllers.chargeC.AddressListController.onPageLoad(mode: Mode = CheckMode, srn: String)" >> ../conf/app.routes
echo "POST       /:srn/new-return/changeAddressList                  controllers.chargeC.AddressListController.onSubmit(mode: Mode = CheckMode, srn: String)" >> ../conf/app.routes

echo "Adding messages to conf.messages"
echo "" >> ../conf/messages.en
echo "addressList.title = addressList" >> ../conf/messages.en
echo "addressList.heading = addressList" >> ../conf/messages.en
echo "addressList.option1 = Option 1" >> ../conf/messages.en
echo "addressList.option2 = Option 2" >> ../conf/messages.en
echo "addressList.checkYourAnswersLabel = addressList" >> ../conf/messages.en
echo "addressList.error.required = Select addressList" >> ../conf/messages.en

echo "Adding to UserAnswersEntryGenerators"
awk '/trait UserAnswersEntryGenerators/ {\
    print;\
    print "";\
    print "  implicit lazy val arbitraryAddressListUserAnswersEntry: Arbitrary[(AddressListPage.type, JsValue)] =";\
    print "    Arbitrary {";\
    print "      for {";\
    print "        page  <- arbitrary[AddressListPage.type]";\
    print "        value <- arbitrary[AddressList].map(Json.toJson(_))";\
    print "      } yield (page, value)";\
    print "    }";\
    next }1' ../test/generators/UserAnswersEntryGenerators.scala > tmp && mv tmp ../test/generators/UserAnswersEntryGenerators.scala

echo "Adding to PageGenerators"
awk '/trait PageGenerators/ {\
    print;\
    print "";\
    print "  implicit lazy val arbitraryAddressListPage: Arbitrary[AddressListPage.type] =";\
    print "    Arbitrary(AddressListPage)";\
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
    print "    arbitrary[(AddressListPage.type, JsValue)] ::";\
    next }1' ../test/generators/UserAnswersGenerator.scala > tmp && mv tmp ../test/generators/UserAnswersGenerator.scala

echo "Adding helper method to CheckYourAnswersHelper"
awk '/class CheckYourAnswersHelper/ {\
     print;\
     print "";\
     print "  def addressList: Option[Row] = userAnswers.get(AddressListPage) map {";\
     print "    answer =>";\
     print "      Row(";\
     print "        key     = Key(msg\"addressList.checkYourAnswersLabel\", classes = Seq(\"govuk-!-width-one-half\")),";\
     print "        value   = Value(msg\"addressList.$answer\"),";\
     print "        actions = List(";\
     print "          Action(";\
     print "            content            = msg\"site.edit\",";\
     print "            href               = controllers.routes.AddressListController.onPageLoad(CheckMode, srn).url,";\
     print "            visuallyHiddenText = Some(msg\"site.edit.hidden\".withArgs(msg\"addressList.checkYourAnswersLabel\"))";\
     print "          )";\
     print "        )";\
     print "      )";\
     print "  }";\
     next }1' ../app/utils/CheckYourAnswersHelper.scala > tmp && mv tmp ../app/utils/CheckYourAnswersHelper.scala

echo "Migration AddressList completed"
