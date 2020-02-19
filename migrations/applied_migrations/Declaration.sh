#!/bin/bash

echo ""
echo "Applying migration Declaration"

echo "Adding routes to conf/app.routes"
echo "" >> ../conf/app.routes
echo "GET        /declaration                       controllers.DeclarationController.onPageLoad()" >> ../conf/app.routes

echo "Adding messages to conf.messages"
echo "" >> ../conf/messages.en
echo "declaration.title = declaration" >> ../conf/messages.en
echo "declaration.heading = declaration" >> ../conf/messages.en

echo "Migration Declaration completed"
