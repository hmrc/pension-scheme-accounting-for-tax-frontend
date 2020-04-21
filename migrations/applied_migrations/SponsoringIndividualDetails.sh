#!/bin/bash

echo ""
echo "Applying migration SponsoringIndividualDetails"

echo "Adding routes to conf/app.routes"

echo "" >> ../conf/app.routes
echo "GET        /:srn/new-return/sponsoringIndividualDetails                        controllers.SponsoringIndividualDetailsController.onPageLoad(mode: Mode = NormalMode, srn: String)" >> ../conf/app.routes
echo "POST       /:srn/new-return/sponsoringIndividualDetails                        controllers.SponsoringIndividualDetailsController.onSubmit(mode: Mode = NormalMode, srn: String)" >> ../conf/app.routes

echo "GET        /:srn/new-return/changeSponsoringIndividualDetails                  controllers.SponsoringIndividualDetailsController.onPageLoad(mode: Mode = CheckMode, srn: String)" >> ../conf/app.routes
echo "POST       /:srn/new-return/changeSponsoringIndividualDetails                  controllers.SponsoringIndividualDetailsController.onSubmit(mode: Mode = CheckMode, srn: String)" >> ../conf/app.routes

echo "Adding messages to conf.messages"
echo "" >> ../conf/messages.en
echo "sponsoringIndividualDetails.title = sponsoringIndividualDetails" >> ../conf/messages.en
echo "sponsoringIndividualDetails.heading = sponsoringIndividualDetails" >> ../conf/messages.en
echo "sponsoringIndividualDetails.checkYourAnswersLabel = sponsoringIndividualDetails" >> ../conf/messages.en
echo "sponsoringIndividualDetails.error.required = Enter sponsoringIndividualDetails" >> ../conf/messages.en
echo "sponsoringIndividualDetails.error.length = SponsoringIndividualDetails must be 35 characters or less" >> ../conf/messages.en

echo "Adding to UserAnswersEntryGenerators"
awk '/trait UserAnswersEntryGenerators/ {\
    print;\
    print "";\
    print "  implicit lazy val arbitrarySponsoringIndividualDetailsUserAnswersEntry: Arbitrary[(SponsoringIndividualDetailsPage.type, JsValue)] =";\
    print "    Arbitrary {";\
    print "      for {";\
    print "        page  <- arbitrary[SponsoringIndividualDetailsPage.type]";\
    print "        value <- arbitrary[String].suchThat(_.nonEmpty).map(Json.toJson(_))";\
    print "      } yield (page, value)";\
    print "    }";\
    next }1' ../test/generators/UserAnswersEntryGenerators.scala > tmp && mv tmp ../test/generators/UserAnswersEntryGenerators.scala

echo "Adding to PageGenerators"
awk '/trait PageGenerators/ {\
    print;\
    print "";\
    print "  implicit lazy val arbitrarySponsoringIndividualDetailsPage: Arbitrary[SponsoringIndividualDetailsPage.type] =";\
    print "    Arbitrary(SponsoringIndividualDetailsPage)";\
    next }1' ../test/generators/PageGenerators.scala > tmp && mv tmp ../test/generators/PageGenerators.scala

echo "Adding to UserAnswersGenerator"
awk '/val generators/ {\
    print;\
    print "    arbitrary[(SponsoringIndividualDetailsPage.type, JsValue)] ::";\
    next }1' ../test/generators/UserAnswersGenerator.scala > tmp && mv tmp ../test/generators/UserAnswersGenerator.scala

echo "Adding helper method to CheckYourAnswersHelper"
awk '/class CheckYourAnswersHelper/ {\
     print;\
     print "";\
     print "  def sponsoringIndividualDetails: Option[Row] = addRequiredDetailsToUserAnswers.get(SponsoringIndividualDetailsPage) map {";\
     print "    answer =>";\
     print "      Row(";\
     print "        key     = Key(msg\"sponsoringIndividualDetails.checkYourAnswersLabel\", classes = Seq(\"govuk-!-width-one-half\")),";\
     print "        value   = Value(lit\"$answer\"),";\
     print "        actions = List(";\
     print "          Action(";\
     print "            content            = msg\"site.edit\",";\
     print "            href               = controllers.routes.SponsoringIndividualDetailsController.onPageLoad(CheckMode, srn).url,";\
     print "            visuallyHiddenText = Some(msg\"site.edit.hidden\".withArgs(msg\"sponsoringIndividualDetails.checkYourAnswersLabel\"))";\
     print "          )";\
     print "        )";\
     print "      )";\
     print "  }";\
     next }1' ../app/utils/CYAHelper.scala > tmp && mv tmp ../app/utils/CYAHelper.scala

echo "Migration SponsoringIndividualDetails completed"
