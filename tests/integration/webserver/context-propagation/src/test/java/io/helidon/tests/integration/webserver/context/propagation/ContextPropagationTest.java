/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.tests.integration.webserver.context.propagation;

import io.helidon.common.context.Context;
import io.helidon.webclient.WebClient;
import io.helidon.webserver.WebServer;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.helidon.tests.integration.webserver.context.propagation.ContextPropagationMain.TIMEOUT;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.arrayContaining;

class ContextPropagationTest {
    private static WebServer server;
    private static WebClient client;

    @BeforeAll
    static void init() {
        ContextPropagationMain.main(new String[0]);
        server = ContextPropagationMain.server();
        client = ContextPropagationMain.client();
    }

    @AfterAll
    static void shutdown() {
        if (server != null) {
            server.shutdown()
                    .await(TIMEOUT);
        }
    }

    @Test
    void testJustDefaults() {
        DataDto dto = client.get()
                .request(DataDto.class)
                .await(TIMEOUT);

        assertThat(dto.getValue(), nullValue());
        assertThat(dto.getValues(), nullValue());
        assertThat(dto.getDefaultValue(), is("default-value"));
        assertThat(dto.getDefaultValues(), is(arrayContaining("default-value1",
                                                              "default-value2",
                                                              "default-value3")));
    }

    @Test
    void testAllCustom() {
        Context context = Context.create();
        context.register(ContextPropagationMain.CLASSIFIER_VALUE, "first");
        context.register(ContextPropagationMain.CLASSIFIER_VALUES, new String[] {"second", "third"});
        context.register(ContextPropagationMain.CLASSIFIER_DEFAULT, "fourth");
        context.register(ContextPropagationMain.CLASSIFIER_DEFAULTS, new String[] {"fifth", "sixth"});
        context.register(ContextPropagationMain.NOT_PROPAGATED, "value");

        DataDto dto = client.get()
                .context(context)
                .request(DataDto.class)
                .await(TIMEOUT);

        assertThat(dto.getValue(), is("first"));
        assertThat(dto.getValues(), is(arrayContaining("second",
                                                       "third")));
        assertThat(dto.getDefaultValue(), is("fourth"));
        assertThat(dto.getDefaultValues(), is(arrayContaining("fifth",
                                                              "sixth")));
        assertThat(dto.getNotPropagated(), is(nullValue()));
    }
}
