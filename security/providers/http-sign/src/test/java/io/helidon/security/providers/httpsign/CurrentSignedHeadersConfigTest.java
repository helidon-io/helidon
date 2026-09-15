/*
 * Copyright (c) 2018, 2026 Oracle and/or its affiliates.
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

package io.helidon.security.providers.httpsign;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import io.helidon.common.media.type.MediaTypes;
import io.helidon.config.Config;

import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.helidon.security.providers.httpsign.SignedHeadersConfig.REQUEST_TARGET;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit test for {@link SignedHeadersConfig}.
 */
class CurrentSignedHeadersConfigTest {
    private static Config config;

    @BeforeAll
    static void initClass() {
        config = Config.create().get("current");
    }

    @Test
    void testFromConfig() {
        SignedHeadersConfig shc = config.get("http-signatures.sign-headers").as(SignedHeadersConfig::create).get();

        testThem(shc);
    }

    @Test
    void testExactMethodFromConfig() {
        SignedHeadersConfig shc = SignedHeadersConfig.create(Config.just("""
                sign-headers:
                  - always: [date]
                  - method: PATCH
                    always: [date, x-exact]
                """, MediaTypes.APPLICATION_YAML).get("sign-headers"));

        assertThat(shc.headers("patch"), CoreMatchers.not(CoreMatchers.hasItem("x-exact")));
        assertThat(shc.headers("PATCH"), CoreMatchers.hasItem("x-exact"));
    }

    @Test
    void testFromBuilder() {
        SignedHeadersConfig shc = SignedHeadersConfig.builder()
                .defaultConfig(SignedHeadersConfig.HeadersConfig.create(List.of("date")))
                .config("GET",
                        SignedHeadersConfig.HeadersConfig
                                .create(List.of("date", REQUEST_TARGET, "host"),
                                        List.of("authorization")))
                .build();

        testThem(shc);
    }

    @Test
    void testBuilderRejectsNullMethodConfig() {
        var headers = SignedHeadersConfig.HeadersConfig.create();

        assertAll(
                () -> assertThrows(NullPointerException.class,
                                   () -> SignedHeadersConfig.builder().config(null, headers)),
                () -> assertThrows(NullPointerException.class,
                                   () -> SignedHeadersConfig.builder().config("GET", null))
        );
    }

    private void testThem(SignedHeadersConfig shc) {
        Map<String, List<String>> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

        List<String> requiredHeaders = shc.headers("GET", headers);
        // first check we get the mandatory ones even if they are not present in request
        assertThat(requiredHeaders, CoreMatchers.hasItems("date", REQUEST_TARGET, "host"));
        assertThat(requiredHeaders, CoreMatchers.not(CoreMatchers.hasItems("authorization")));

        requiredHeaders = shc.headers("get", headers);
        assertThat(requiredHeaders, CoreMatchers.hasItems("date"));
        assertThat(requiredHeaders, CoreMatchers.not(CoreMatchers.hasItems("authorization", REQUEST_TARGET, "host")));

        requiredHeaders = shc.headers("POST", headers);
        assertThat(requiredHeaders, CoreMatchers.hasItems("date"));
        assertThat(requiredHeaders, CoreMatchers.not(CoreMatchers.hasItems("authorization", REQUEST_TARGET, "host")));

        //now let's add authorization to the request headers
        headers.put("Authorization", List.of("basic dXNlcm5hbWU6cGFzc3dvcmQ="));
        requiredHeaders = shc.headers("GET", headers);
        // first check we get the mandatory ones even if they are not present in request
        assertThat(requiredHeaders, CoreMatchers.hasItems("date", REQUEST_TARGET, "host", "authorization"));

        requiredHeaders = shc.headers("POST", headers);
        assertThat(requiredHeaders, CoreMatchers.hasItems("date"));
        assertThat(requiredHeaders, CoreMatchers.not(CoreMatchers.hasItems("authorization", REQUEST_TARGET, "host")));
    }
}
