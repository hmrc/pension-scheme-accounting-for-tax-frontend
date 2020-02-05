#!/bin/bash

echo ""
echo "Applying migration SponsoringEmployerAddress"

echo "Adding routes to conf/app.routes"

echo "" >> ../conf/app.routes
echo "GET        /:srn/new-return/sponsoringEmployerAddress                        controllers.chargeC.SponsoringEmployerAddressController.onPageLoad(mode: Mode = NormalMode, srn: String)" >> ../conf/app.routes
echo "POST       /:srn/new-return/sponsoringEmployerAddress                        controllers.chargeC.SponsoringEmployerAddressController.onSubmit(mode: Mode = NormalMode, srn: String)" >> ../conf/app.routes

echo "GET        /:srn/new-return/changeSponsoringEmployerAddress                  controllers.chargeC.SponsoringEmployerAddressController.onPageLoad(mode: Mode = CheckMode, srn: String)" >> ../conf/app.routes
echo "POST       /:srn/new-return/changeSponsoringEmployerAddress                  controllers.chargeC.SponsoringEmployerAddressController.onSubmit(mode: Mode = CheckMode, srn: String)" >> ../conf/app.routes

echo "Adding messages to conf.messages"
echo "" >> ../conf/messages.en
echo "sponsoringEmployerAddress.title = sponsoringEmployerAddress" >> ../conf/messages.en
echo "sponsoringEmployerAddress.heading = sponsoringEmployerAddress" >> ../conf/messages.en
echo "sponsoringEmployerAddress.checkYourAnswersLabel = sponsoringEmployerAddress" >> ../conf/messages.en
echo "sponsoringEmployerAddress.error.required = Enter sponsoringEmployerAddress" >> ../conf/messages.en
echo "sponsoringEmployerAddress.error.length = SponsoringEmployerAddress must be 35 characters or less" >> ../conf/messages.en

echo "Adding to UserAnswersEntryGenerators"
awk '/trait UserAnswersEntryGenerators/ {\
    print;\
    print "";\
    print "  implicit lazy val arbitrarySponsoringEmployerAddressUserAnswersEntry: Arbitrary[(SponsoringEmployerAddressPage.type, JsValue)] =";\
    print "    Arbitrary {";\
    print "      for {";\
    print "        page  <- arbitrary[SponsoringEmployerAddressPage.type]";\
    print "        value <- arbitrary[String].suchThat(_.nonEmpty).map(Json.toJson(_))";\
    print "      } yield (page, value)";\
    print "    }";\
    next }1' ../test/generators/UserAnswersEntryGenerators.scala > tmp && mv tmp ../test/generators/UserAnswersEntryGenerators.scala

echo "Adding to PageGenerators"
awk '/trait PageGenerators/ {\
    print;\
    print "";\
    print "  implicit lazy val arbitrarySponsoringEmployerAddressPage: Arbitrary[SponsoringEmployerAddressPage.type] =";\
    print "    Arbitrary(SponsoringEmployerAddressPage)";\
    next }1' ../test/generators/PageGenerators.scala > tmp && mv tmp ../test/generators/PageGenerators.scala

echo "Adding to UserAnswersGenerator"
awk '/val generators/ {\
    print;\
    print "    arbitrary[(SponsoringEmployerAddressPage.type, JsValue)] ::";\
    next }1' ../test/generators/UserAnswersGenerator.scala > tmp && mv tmp ../test/generators/UserAnswersGenerator.scala

echo "Adding helper method to CheckYourAnswersHelper"
awk '/class CheckYourAnswersHelper/ {\
     print;\
     print "";\
     print "  def sponsoringEmployerAddress: Option[Row] = addRequiredDetailsToUserAnswers.get(SponsoringEmployerAddressPage) map {";\
     print "    answer =>";\
     print "      Row(";\
     print "        key     = Key(msg\"sponsoringEmployerAddress.checkYourAnswersLabel\", classes = Seq(\"govuk-!-width-one-half\")),";\
     print "        value   = Value(lit\"$answer\"),";\
     print "        actions = List(";\
     print "          Action(";\
     print "            content            = msg\"site.edit\",";\
     print "            href               = controllers.routes.SponsoringEmployerAddressController.onPageLoad(CheckMode, srn).url,";\
     print "            visuallyHiddenText = Some(msg\"site.edit.hidden\".withArgs(msg\"sponsoringEmployerAddress.checkYourAnswersLabel\"))";\
     print "          )";\
     print "        )";\
     print "      )";\
     print "  }";\
     next }1' ../app/utils/CheckYourAnswersHelper.scala > tmp && mv tmp ../app/utils/CheckYourAnswersHelper.scala

echo "Migration SponsoringEmployerAddress completed"
