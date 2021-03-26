/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#start two servers participants
java -Dserver.port=8091 -jar lra-rest-participant/target/lra-rest-participant.jar
java -Dserver.port=8092 -jar lra-rest-participant/target/lra-rest-participant.jar
#call the first service with a "calladdress" url indicating it should call the second resource
curl "http://localhost:8091/rest/requiresNew?calladdress=http://localhost:8092/rest/mandatory"
#call the first service with a "calladdress" url indicating it should call the second resource
curl "http://localhost:8091/rest/requiresNew?calladdress=http://localhost:8092/rest/never"
#mark the second resource to throw exception thus inducing an implicit cancel call to the coordinator and compensate calls to participants
curl "http://localhost:8092/rest/setCancel" 
curl "http://localhost:8091/rest/requiresNew?calladdress=http://localhost:8092/rest/mandatory"

java -Dserver.port=8093 -jar lra-rest-participant/target/lra-rest-participant.jar
curl "http://localhost:8091/rest/requiresNew?calladdress='http://localhost:8092/rest/mandatory?calladdress=http://localhost:8093/rest/never'"
curl -d {"calladdress":"http://localhost:8092/rest/mandatory?calladdress=http://localhost:8093/rest/never"} "http://localhost:8091/rest/requiresNew"

http://localhost:8092/rest/mandatory?calladdress=http://localhost:8093/rest/never

Any number of services can be started by just passing different -Dserver.port=8091  values
and then provide the flow in the front end service
Examples...
 1. call REQUIRESNEW on restMS1 (localhost:8091) which should in turn call MANDATORY on restMS2 (localhost:8092)
For Messaging some changes to microprofile-config.properties are required.
 2. call REQUIRESNEW on aqMessagingMS1 (localhost:8091) which should in turn call MANDATORY on restMS2 (localhost:8092)
 1. call REQUIRESNEW on restMS1 (localhost:8091) which should in turn call MANDATORY on restMS2 (localhost:8092)

