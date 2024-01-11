/*
 * Copyright (c) 2020, 2024 Oracle and/or its affiliates.
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
package io.helidon.cors;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import io.helidon.common.testing.junit5.OptionalMatcher;
import io.helidon.common.uri.UriInfo;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.config.MissingValueException;
import io.helidon.http.HeaderNames;
import io.helidon.http.Status;
import io.helidon.http.WritableHeaders;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.arrayContaining;
import static org.hamcrest.Matchers.emptyArray;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;

public class CrossOriginConfigTest {

    private final static String YAML_PATH = "/configMapperTest.yaml";

    private static Config testConfig;

    @BeforeAll
    public static void loadTestConfig() {
        testConfig = Config.builder()
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .addSource(ConfigSources.classpath(YAML_PATH))
                .build();
    }

    @Test
    public void testNarrow() {
        Config node = testConfig.get("narrow");
        assertThat(node, is(notNullValue()));
        assertThat(node.exists(), is(true));
        CrossOriginConfig c = node.as(CrossOriginConfig::create).get();

        assertThat(c.isEnabled(), is(true));
        assertThat(c.allowOrigins(), arrayContaining("http://foo.bar", "http://bar.foo"));
        assertThat(c.allowMethods(), arrayContaining("DELETE", "PUT"));
        assertThat(c.allowHeaders(), arrayContaining("X-bar", "X-foo"));
        assertThat(c.exposeHeaders(), is(emptyArray()));
        assertThat(c.allowCredentials(), is(true));
        assertThat(c.maxAgeSeconds(), is(-1L));
    }

    @Test
    public void testMissing() {
        Assertions.assertThrows(MissingValueException.class, () -> {
            CrossOriginConfig basic = testConfig.get("notThere").as(CrossOriginConfig::create).get();
        });
    }

    @Test
    public void testWide() {
        Config node = testConfig.get("wide");
        assertThat(node, is(notNullValue()));
        assertThat(node.exists(), is(true));
        CrossOriginConfig b = node.as(CrossOriginConfig::create).get();

        assertThat(b.isEnabled(), is(false));
        assertThat(b.allowOrigins(), arrayContaining(CrossOriginConfig.Builder.ALLOW_ALL));
        assertThat(b.allowMethods(), arrayContaining(CrossOriginConfig.Builder.ALLOW_ALL));
        assertThat(b.allowHeaders(), arrayContaining(CrossOriginConfig.Builder.ALLOW_ALL));
        assertThat(b.exposeHeaders(), is(emptyArray()));
        assertThat(b.allowCredentials(), is(false));
        assertThat(b.maxAgeSeconds(), is(CrossOriginConfig.DEFAULT_AGE));
    }

    @Test
    public void testJustDisabled() {
        Config node = testConfig.get("just-disabled");
        assertThat(node, is(notNullValue()));
        assertThat(node.exists(), is(true));
        CrossOriginConfig b = node.as(CrossOriginConfig::create).get();

        assertThat(b.isEnabled(), is(false));
    }

    @Test
    public void testPaths() {
        Config node = testConfig.get("cors-setup");
        assertThat(node, is(notNullValue()));
        assertThat(node.exists(), is(true));
        MappedCrossOriginConfig m = node.as(MappedCrossOriginConfig::create).get();

        assertThat(m.isEnabled(), is(true));

        CrossOriginConfig b = m.get("/cors1");
        assertThat(b, notNullValue());
        assertThat(b.isEnabled(), is(true));
        assertThat(b.allowOrigins(), arrayContaining("*"));
        assertThat(b.allowMethods(), arrayContaining("*"));
        assertThat(b.allowHeaders(), arrayContaining("*"));
        assertThat(b.allowCredentials(), is(false));
        assertThat(b.maxAgeSeconds(), is(CrossOriginConfig.DEFAULT_AGE));

        b = m.get("/cors2");
        assertThat(b, notNullValue());
        assertThat(b.isEnabled(), is(true));
        assertThat(b.allowOrigins(), arrayContaining("http://foo.bar", "http://bar.foo"));
        assertThat(b.allowMethods(), arrayContaining("DELETE", "PUT"));
        assertThat(b.allowHeaders(), arrayContaining("X-bar", "X-foo"));
        assertThat(b.allowCredentials(), is(true));
        assertThat(b.maxAgeSeconds(), is(-1L));

        assertThat(m.get("/cors3"), nullValue());
    }

    @Test
    void testOrdering() {
        Config node = testConfig.get("order-check");
        assertThat(node, is(notNullValue()));
        assertThat(node.exists(), is(true));
        MappedCrossOriginConfig m = node.as(MappedCrossOriginConfig::create).get();

        assertThat(m.isEnabled(), is(true));

        //Make sure path elements are in the right order.
        List<String> pathsInOrder = new ArrayList<>();
        List<CrossOriginConfig> crossOriginConfigs = new ArrayList<>();
        m.forEach((path, crossOrginConfig) -> {
            pathsInOrder.add(path);
            crossOriginConfigs.add(crossOrginConfig);
        });

        // Make sure ordering from config is what we expect.
        assertThat("Paths configured", pathsInOrder.size(), is(3));
        assertThat("First path", pathsInOrder.get(0), startsWith("/authorize"));
        assertThat("Second path", pathsInOrder.get(1), startsWith("/callback"));
        assertThat("Third path", pathsInOrder.get(2), is("{^(?!((authorize)|(callback))).*$}"));

        // Make sure the aggregator retains the correct order.
        Aggregator agg = Aggregator.builder().mappedConfig(node).build();

        Optional<CrossOriginConfig> crossOriginConfigOpt = agg.lookupCrossOrigin("/authorize", "GET", Optional::empty);
        assertThat("Match found for /authorize", crossOriginConfigOpt.isPresent(), is(true));
        assertThat("Match for /authorize", crossOriginConfigOpt.get().pathPattern(), is(crossOriginConfigs.get(0).pathPattern()));

        crossOriginConfigOpt = agg.lookupCrossOrigin("/authorize/else", "GET", Optional::empty);
        assertThat("Match found for /authorize/else", crossOriginConfigOpt.isPresent(), is(true));
        assertThat("Match for /authorize/else",
                   crossOriginConfigOpt.get().pathPattern(),
                   is(crossOriginConfigs.get(0).pathPattern()));

        crossOriginConfigOpt = agg.lookupCrossOrigin("/callback", "PUT", Optional::empty);
        assertThat("Match found for /callback", crossOriginConfigOpt.isPresent(), is(true));
        assertThat("Match for /callback", crossOriginConfigOpt.get().pathPattern(), is(crossOriginConfigs.get(1).pathPattern()));

        crossOriginConfigOpt = agg.lookupCrossOrigin("/callback/other", "PUT", Optional::empty);
        assertThat("Match found for /callback/other", crossOriginConfigOpt.isPresent(), is(true));
        assertThat("Match for /callback/other",
                   crossOriginConfigOpt.get().pathPattern(),
                   is(crossOriginConfigs.get(1).pathPattern()));
    }

    @Test
    void testBuiltInServiceConfig() {
        Config node = testConfig.get("built-in-service");
        assertThat(node, is(notNullValue()));
        assertThat(node.exists(), is(true));
        CorsSupportHelper<TestCorsServerRequestAdapter, TestCorsServerResponseAdapter> helper =
                CorsSupportHelper.<TestCorsServerRequestAdapter, TestCorsServerResponseAdapter>builder()
                .config(node)
                .build();

        // In the first test, use an unacceptable origin and make sure we get a 403 response.
        WritableHeaders<?> headers = WritableHeaders.create();
        headers.add(HeaderNames.ORIGIN, "http://bad.com")
                .add(HeaderNames.HOST, "someProxy.com")
                .add(HeaderNames.ACCESS_CONTROL_REQUEST_METHOD, "GET");
        UriInfo uriInfo = UriInfo.builder()
                .scheme("http")
                .host("localhost")
                .path("/")
                .port(80)
                .build();
        CorsRequestAdapter<TestCorsServerRequestAdapter> req = new TestCorsServerRequestAdapter("/observe/health",
                                                                                                uriInfo,
                                                                                                "GET",
                                                                                                headers);
        CorsResponseAdapter<TestCorsServerResponseAdapter> resp = new TestCorsServerResponseAdapter();
        Optional<TestCorsServerResponseAdapter> resultOpt = helper.processRequest(req, resp);

        assertThat("Response from GET to built-in service path",
                   resultOpt,
                   OptionalMatcher.optionalPresent());
        assertThat("Response status from secure PUT to unsecured path",
                   resultOpt.get().status(),
                   is(Status.FORBIDDEN_403.code()));

        // In the next test, use an acceptable origin in the request. There should be no response because that's how
        // the CORS support helper lets the caller know that it's an acceptable CORS request.
        headers = WritableHeaders.create();
        headers.add(HeaderNames.ORIGIN, "http://roothere.com")
                .add(HeaderNames.HOST, "someProxy.com")
                .add(HeaderNames.ACCESS_CONTROL_REQUEST_METHOD, "GET");
        req = new TestCorsServerRequestAdapter("/observe/health",
                                               uriInfo,
                                               "GET",
                                               headers);
        resp = new TestCorsServerResponseAdapter();

        resultOpt = helper.processRequest(req, resp);

        assertThat("Response from GET to built-in service path",
                   resultOpt,
                   OptionalMatcher.optionalEmpty());
    }
}
