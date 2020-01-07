#!/bin/bash

echo ""
echo "Applying migration SponsoringOrganisationDetails"

echo "Adding routes to conf/app.routes"

echo "" >> ../conf/app.routes
echo "GET        /:srn/new-return/sponsoringOrganisationDetails                        controllers.chargeC.SponsoringOrganisationDetailsController.onPageLoad(mode: Mode = NormalMode, srn: String)" >> ../conf/app.routes
echo "POST       /:srn/new-return/sponsoringOrganisationDetails                        controllers.chargeC.SponsoringOrganisationDetailsController.onSubmit(mode: Mode = NormalMode, srn: String)" >> ../conf/app.routes

echo "GET        /:srn/new-return/changeSponsoringOrganisationDetails                  controllers.chargeC.SponsoringOrganisationDetailsController.onPageLoad(mode: Mode = CheckMode, srn: String)" >> ../conf/app.routes
echo "POST       /:srn/new-return/changeSponsoringOrganisationDetails                  controllers.chargeC.SponsoringOrganisationDetailsController.onSubmit(mode: Mode = CheckMode, srn: String)" >> ../conf/app.routes

echo "Adding messages to conf.messages"
echo "" >> ../conf/messages.en
echo "sponsoringOrganisationDetails.title = sponsoringOrganisationDetails" >> ../conf/messages.en
echo "sponsoringOrganisationDetails.heading = sponsoringOrganisationDetails" >> ../conf/messages.en
echo "sponsoringOrganisationDetails.checkYourAnswersLabel = sponsoringOrganisationDetails" >> ../conf/messages.en
echo "sponsoringOrganisationDetails.error.required = Enter sponsoringOrganisationDetails" >> ../conf/messages.en
echo "sponsoringOrganisationDetails.error.length = SponsoringOrganisationDetails must be 155 characters or less" >> ../conf/messages.en

echo "Adding to UserAnswersEntryGenerators"
awk '/trait UserAnswersEntryGenerators/ {\
    print;\
    print "";\
    print "  implicit lazy val arbitrarySponsoringOrganisationDetailsUserAnswersEntry: Arbitrary[(SponsoringOrganisationDetailsPage.type, JsValue)] =";\
    print "    Arbitrary {";\
    print "      for {";\
    print "        page  <- arbitrary[SponsoringOrganisationDetailsPage.type]";\
    print "        value <- arbitrary[String].suchThat(_.nonEmpty).map(Json.toJson(_))";\
    print "      } yield (page, value)";\
    print "    }";\
    next }1' ../test/generators/UserAnswersEntryGenerators.scala > tmp && mv tmp ../test/generators/UserAnswersEntryGenerators.scala

echo "Adding to PageGenerators"
awk '/trait PageGenerators/ {\
    print;\
    print "";\
    print "  implicit lazy val arbitrarySponsoringOrganisationDetailsPage: Arbitrary[SponsoringOrganisationDetailsPage.type] =";\
    print "    Arbitrary(SponsoringOrganisationDetailsPage)";\
    next }1' ../test/generators/PageGenerators.scala > tmp && mv tmp ../test/generators/PageGenerators.scala

echo "Adding to UserAnswersGenerator"
awk '/val generators/ {\
    print;\
    print "    arbitrary[(SponsoringOrganisationDetailsPage.type, JsValue)] ::";\
    next }1' ../test/generators/UserAnswersGenerator.scala > tmp && mv tmp ../test/generators/UserAnswersGenerator.scala

echo "Adding helper method to CheckYourAnswersHelper"
awk '/class CheckYourAnswersHelper/ {\
     print;\
     print "";\
     print "  def sponsoringOrganisationDetails: Option[Row] = userAnswers.get(SponsoringOrganisationDetailsPage) map {";\
     print "    answer =>";\
     print "      Row(";\
     print "        key     = Key(msg\"sponsoringOrganisationDetails.checkYourAnswersLabel\", classes = Seq(\"govuk-!-width-one-half\")),";\
     print "        value   = Value(lit\"$answer\"),";\
     print "        actions = List(";\
     print "          Action(";\
     print "            content            = msg\"site.edit\",";\
     print "            href               = controllers.routes.SponsoringOrganisationDetailsController.onPageLoad(CheckMode, srn).url,";\
     print "            visuallyHiddenText = Some(msg\"site.edit.hidden\".withArgs(msg\"sponsoringOrganisationDetails.checkYourAnswersLabel\"))";\
     print "          )";\
     print "        )";\
     print "      )";\
     print "  }";\
     next }1' ../app/utils/CheckYourAnswersHelper.scala > tmp && mv tmp ../app/utils/CheckYourAnswersHelper.scala

echo "Migration SponsoringOrganisationDetails completed"
