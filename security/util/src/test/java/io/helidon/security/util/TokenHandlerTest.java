/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.security.util;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.regex.Pattern;

import io.helidon.common.CollectionsHelper;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static java.util.Collections.singletonList;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Unit test for {@link TokenHandler}.
 */
public class TokenHandlerTest {
    private static final String TOKEN_VALUE = "abdasf5as4df35as4dfas3f4as35d21afd3";

    @Test
    public void testMissingHeader() {
        TokenHandler tp = TokenHandler.builder()
                .tokenHeader("Other")
                .tokenPrefix("bearer ")
                .build();

        Optional<String> optToken = tp.extractToken(bearerRequest());
        assertThat(optToken, notNullValue());
        assertThat(optToken.isPresent(), is(false));
    }

    @Test
    public void testWrongPrefix() {
        TokenHandler tp = TokenHandler.builder()
                .tokenHeader("Authorization")
                .tokenPrefix("hearer ")
                .build();

        try {
            testBearer(tp, TOKEN_VALUE);
        } catch (SecurityException e) {
            assertThat(e.getMessage(), startsWith("Header does not start"));
        }
    }

    @Test
    public void testWrongPattern() {
        TokenHandler tp = TokenHandler.builder()
                .tokenHeader("Authorization")
                .tokenPattern(Pattern.compile("not matching"))
                .build();

        try {
            testBearer(tp, TOKEN_VALUE);
        } catch (SecurityException e) {
            assertThat(e.getMessage(), startsWith("Header does not match expected pattern"));
        }
    }

    @Test
    public void testPrefixConfig() {
        Config config = Config.builder()
                .sources(ConfigSources.classpath("token_provider.conf"))
                .build();
        TokenHandler tp = TokenHandler.fromConfig(config.get("token-1"));
        testBearer(tp, TOKEN_VALUE);
    }

    @Test
    public void testPrefixBuilder() {
        TokenHandler tp = TokenHandler.builder().tokenHeader("Authorization").tokenPrefix("bearer ").build();

        testBearer(tp, TOKEN_VALUE);
        testCreateHeader(tp);
    }

    @Test
    public void testRegexpConfig() {
        Config config = Config.builder()
                .sources(ConfigSources.classpath("token_provider.conf"))
                .build();
        TokenHandler tp = TokenHandler.fromConfig(config.get("token-2"));
        testBearer(tp, TOKEN_VALUE);
        testCreateHeader(tp);
    }

    @Test
    public void testAddHeader() {
        TokenHandler tp = TokenHandler.forHeader("Authorization");

        Map<String, List<String>> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        headers.put("Authorization", singletonList("firstToken"));

        tp.addHeader(headers, "secondToken");

        List<String> authorization = headers.get("Authorization");
        assertThat(authorization.size(), is(2));
        assertThat(authorization, is(CollectionsHelper.listOf("firstToken", "secondToken")));

        assertThat(tp.getTokenHeader(), is("Authorization"));
    }

    @Test
    public void testAddNewHeader() {
        TokenHandler tp = TokenHandler.forHeader("Authorization");

        Map<String, List<String>> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

        tp.addHeader(headers, "secondToken");

        List<String> authorization = headers.get("Authorization");
        assertThat(authorization.size(), is(1));
        assertThat(authorization, is(singletonList("secondToken")));

        assertThat(tp.getTokenHeader(), is("Authorization"));
    }

    @Test
    public void testExtractHeader() {
        String tokenValue = "token_asdfasůdfasdlkjfsadůlflsd";
        TokenHandler tokenHandler = TokenHandler.forHeader("Test");
        String value = tokenHandler.extractToken(tokenValue);

        assertThat(value, is(tokenValue));
    }

    private void testCreateHeader(TokenHandler tp) {
        Map<String, List<String>> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        tp.setHeader(headers, TOKEN_VALUE);

        List<String> authList = headers.get("Authorization");
        assertThat(authList, notNullValue());
        assertThat(authList.size(), is(1));

        String header = authList.get(0);
        assertThat(header, is("bearer " + TOKEN_VALUE));
    }

    @Test
    public void testRegexpBuilder() {
        TokenHandler tp = TokenHandler.builder().tokenHeader("Authorization")
                .tokenPattern(Pattern.compile("bearer (.*)", Pattern.CASE_INSENSITIVE)).build();

        testBearer(tp, TOKEN_VALUE);
    }

    @Test
    public void testConfig() {
        Config config = Config.builder()
                .sources(ConfigSources.classpath("token_provider.conf"))
                .build();
        TokenHandler tp = TokenHandler.fromConfig(config.get("token-4"));
        testBearer(tp, "bearer " + TOKEN_VALUE);
    }

    @Test
    public void testBuilder() {
        TokenHandler tp = TokenHandler.builder().tokenHeader("Authorization").build();
        testBearer(tp, "bearer " + TOKEN_VALUE);
    }

    @Test
    public void testWrongConfig() {
        Config config = Config.builder()
                .sources(ConfigSources.classpath("token_provider.conf"))
                .build();
        Assertions.assertThrows(NullPointerException.class, () -> TokenHandler.fromConfig(config.get("token-3")));
    }

    @Test
    public void testWrongBuilder() {
        Assertions.assertThrows(NullPointerException.class, () -> TokenHandler.builder()
                .tokenPattern(Pattern.compile("bearer (.*)", Pattern.CASE_INSENSITIVE)).build());
    }

    private void testBearer(TokenHandler tp, String tokenValue) {
        Optional<String> optToken = tp.extractToken(bearerRequest());
        assertThat(optToken, notNullValue());
        assertThat(optToken.isPresent(), is(true));
        assertThat(optToken.get(), is(tokenValue));
    }

    private Map<String, List<String>> bearerRequest() {
        Map<String, List<String>> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        headers.put("Authorization", singletonList("bearer " + TOKEN_VALUE));
        return headers;
    }
}
