/*
 * Copyright (c) 2019, 2023 Oracle and/or its affiliates.
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

package io.helidon.webserver.tests;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.helidon.common.media.type.MediaTypes;
import io.helidon.common.parameters.Parameters;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.Method;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;

@ServerTest
class FormParamsSupportTest {

    private final Http1Client client;

    FormParamsSupportTest(Http1Client client) {
        this.client = client;
    }

    @SetUpRoute
    static void routing(HttpRules rules) {
        rules.put("/params", (req, resp) -> {

            Parameters parameters = req.content().as(Parameters.class);
            Map<String, List<String>> map = new HashMap<>();

            for (String name : parameters.names()) {
                map.put(name, parameters.all(name));
            }

            resp.send(map.toString());
        });
    }

    @Test
    void urlEncodedTest() {
        String result = client.method(Method.PUT)
                .path("/params")
                .header(HeaderNames.CONTENT_TYPE, MediaTypes.APPLICATION_FORM_URLENCODED.text())
                .submit("key1=val+1&key2=val2_1&key2=val2_2")
                .as(String.class);

        assertThat(result, containsString("key1=[val 1]"));
        assertThat(result, containsString("key2=[val2_1, val2_2]"));
    }

    @Test
    void plainTextTest() {
        String result = client.method(Method.PUT)
                .path("/params")
                .header(HeaderValues.CONTENT_TYPE_TEXT_PLAIN)
                .submit("key1=val 1\nkey2=val2_1\nkey2=val2_2")
                .as(String.class);
        assertThat(result, containsString("key1=[val 1]"));
        assertThat(result, containsString("key2=[val2_1, val2_2]"));

    }

}
