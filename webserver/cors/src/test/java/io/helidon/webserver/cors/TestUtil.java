/*
 * Copyright (c) 2020, 2023 Oracle and/or its affiliates.
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
package io.helidon.webserver.cors;

import java.util.Date;

import io.helidon.http.Status;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http.HttpService;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

class TestUtil {

    static final String GREETING_PATH = "/greet";
    static final String OTHER_GREETING_PATH = "/othergreet";

    static String path(CorsTestServices.CORSTestService testService) {
        return GREETING_PATH + testService.path();
    }

    static String path(String prefix, CorsTestServices.CORSTestService testService) {
        return prefix + testService.path();
    }

    static class GreetService implements HttpService {

        private final String greeting;

        GreetService() {
            this("Hello");
        }

        GreetService(String initialGreeting) {
            greeting = initialGreeting;
        }

        @Override
        public void routing(HttpRules rules) {
            rules.get("/", this::getDefaultMessageHandler);
            // Add the cors paths so requests to them have a place to land.
            for (int i = 1; i <= 3; i++) {
                rules.any("/cors" + i, this::getDefaultMessageHandler);
            }
        }

        void getDefaultMessageHandler(ServerRequest request, ServerResponse response) {
            String msg = String.format("%s %s!", greeting, new Date().toString());
            response.status(Status.OK_200);
            response.send(msg);
        }

    }
}
