# Pension Scheme Accounting For Tax Frontend 

## Info

This service allows a pensions administrator, to file or amend an accounting for tax return for a registered pension scheme.

This service has a corresponding back-end service, namely pension-scheme-accounting-for-tax which integrates with HOD i.e DES/ETMP.

### Dependencies

|Service                |Link                                                             |
|-----------------------|-----------------------------------------------------------------|
|Accounting For Tax     |https://github.com/hmrc/pension-scheme-accounting-for-tax        |
|Pensions Scheme        |https://github.com/hmrc/pensions-scheme                          |
|Pension Administrator  |https://github.com/hmrc/pension-administrator                    |
|Address Lookup         |https://github.com/hmrc/address-lookup                           |
|Email                  |https://github.com/hmrc/email                                    |
|Auth                   |https://github.com/hmrc/auth                                     |

### Endpoints used   

|Service                | HTTP Method | Route | Purpose
|-----------------------|-------------|-----------------------------------------------------------|----------------------------------------------------------------------|
|Accounting For Tax     | POST        | /pension-scheme-accounting-for-tax/aft-file-return        | Submits/Updates an AFT Return                                        |
|Accounting For Tax     | GET         | /pension-scheme-accounting-for-tax/get-aft-details        | Returns AFT details                                                  |
|Accounting For Tax     | GET         | /pension-scheme-accounting-for-tax/get-aft-versions       | Returns AFT reporting versions for a given period                                   |
|Accounting For Tax     | GET         | /pension-scheme-accounting-for-tax/get-aft-overview       | Returns the overview of the AFT versions in a given period|
|Accounting For Tax     | GET         | /pension-scheme-accounting-for-tax/journey-cache/aft      | Returns the data from AFT Cache based on session id, quarter start date and PSTR                                  |
|Accounting For Tax     | POST        | /pension-scheme-accounting-for-tax/journey-cache/aft      | Saves the data to AFT Cache with key as the combination of session id, quarter start date and PSTR                                      |
|Accounting For Tax     | DELETE      | /pension-scheme-accounting-for-tax/journey-cache/aft      | Removes the data from AFT Cache based on session id, quarter start date and PSTR                                  |
|Accounting For Tax     | GET         | /pension-scheme-accounting-for-tax/journey-cache/aft/lock | Returns the locked by user name if the data for a given session id, quarter start date and PSTR is locked in AFT Cache                                         |
|Accounting For Tax     | POST        | /pension-scheme-accounting-for-tax/journey-cache/aft/lock | Sets the lock with the name of the user for a given session id, quarter start date and PSTR in AFT Cache                                            |
|Pensions Scheme        | GET         | /pensions-scheme/scheme                                   | Returns details of a scheme                                          |
|Pensions Scheme        | GET         | /pensions-scheme/is-psa-associated                        | Returns true if Psa is associated with the selected scheme           |
|Pension Administrator  | GET         | /pension-administrator/get-minimal-psa                    | Returns minimal PSA details                                          | 
|Address Lookup         | GET         | /v2/uk/addresses                                          | Returns a list of addresses that match a given postcode              | 
|Email                  | POST        | /hmrc/email                                               | Sends an email to an email address                                   | 

## Running the service

Service Manager: PODS_ALL

Port: 8206

Link: http://localhost:8206/manage-pension-scheme-accounting-for-tax

Enrolment key: HMRC-PODS-ORG

Identifier name: PsaID

Example PSA ID: A2100005 (Local and Staging Environment only)

## Tests and prototype

[View the prototype here](https://pods-prototype.herokuapp.com)
|Repositories     |Link                                                                   |
|-----------------|-----------------------------------------------------------------------|
|Journey tests    |https://github.com/hmrc/pods-journey-tests                             |
|Prototype        |https://pods-prototype.herokuapp.com                                   |
