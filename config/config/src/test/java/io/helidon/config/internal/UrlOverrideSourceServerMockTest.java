/*
 * Copyright (c) 2017, 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.config.internal;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.Duration;
import java.util.AbstractMap;
import java.util.Optional;
import java.util.stream.Collectors;

import io.helidon.common.CollectionsHelper;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.config.PollingStrategies;
import io.helidon.config.TestingConfigChangeSubscriber;
import io.helidon.config.spi.OverrideSource;

import com.xebialabs.restito.server.StubServer;

import static io.helidon.config.ConfigSources.from;
import static io.helidon.config.ConfigTest.waitForAssert;
import static io.helidon.config.OverrideSources.url;
import static io.helidon.config.internal.PropertiesConfigParser.MEDIA_TYPE_TEXT_JAVA_PROPERTIES;

import static com.xebialabs.restito.builder.stub.StubHttp.whenHttp;
import static com.xebialabs.restito.semantics.Action.contentType;
import static com.xebialabs.restito.semantics.Action.header;
import static com.xebialabs.restito.semantics.Action.status;
import static com.xebialabs.restito.semantics.Action.stringContent;
import static com.xebialabs.restito.semantics.Condition.method;
import static com.xebialabs.restito.semantics.Condition.uri;
import static org.glassfish.grizzly.http.Method.GET;
import static org.glassfish.grizzly.http.Method.HEAD;
import static org.glassfish.grizzly.http.util.HttpStatus.OK_200;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link UrlOverrideSource} with mocked source.
 */
public class UrlOverrideSourceServerMockTest {

    private static final String NO_WILDCARDS = "";
    private static final String WILDCARDS = "*.*.url = URL1";
    private static final String MULTIPLE_WILDCARDS = ""
            + "xxx.bbb.url = URLX\n"
            + "*.*.url = URL1\n"
            + "*.bbb.url = URL2";
    private static final String MULTIPLE_WILDCARDS_ANOTHER_ORDERING = ""
            + "xxx.bbb.url = URLX\n"
            + "*.bbb.url = URL2\n"
            + "*.*.url = URL1";
    private static final String NEW_WILDCARDS = "*.*.url = URL2";

    private StubServer server;

    @BeforeEach
    public void before() throws IOException {
        server = new StubServer().run();
    }

    @AfterEach
    public void after() {
        server.stop();
    }

    @Test
    public void testWildcards() throws MalformedURLException, InterruptedException {

        whenHttp(server).
                match(method(HEAD), uri("/override")).
                then(
                        status(OK_200),
                        contentType(MEDIA_TYPE_TEXT_JAVA_PROPERTIES),
                        header("Last-Modified", "Sat, 10 Jun 2017 10:14:02 GMT")
                );
        whenHttp(server).
                match(method(GET), uri("/override")).
                then(
                        status(OK_200),
                        contentType(MEDIA_TYPE_TEXT_JAVA_PROPERTIES),
                        header("Last-Modified", "Sat, 10 Jun 2017 10:14:02 GMT"),
                        stringContent(WILDCARDS)
                );

        Config config = Config.builder()
                .sources(from(
                        CollectionsHelper.mapOf(
                                "aaa.bbb.url", "URL0"
                        )))
                .overrides(url(getUrl("/override", server.getPort())))
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .build();

        waitForAssert(() -> config.get("aaa.bbb.url").asString(), is("URL1"));
    }

    @Test
    public void testMultipleMatchingWildcards() throws MalformedURLException, InterruptedException {

        whenHttp(server).
                match(method(HEAD), uri("/override")).
                then(
                        status(OK_200),
                        contentType(MEDIA_TYPE_TEXT_JAVA_PROPERTIES),
                        header("Last-Modified", "Sat, 10 Jun 2017 10:14:02 GMT")
                );
        whenHttp(server).
                match(method(GET), uri("/override")).
                then(
                        status(OK_200),
                        contentType(MEDIA_TYPE_TEXT_JAVA_PROPERTIES),
                        header("Last-Modified", "Sat, 10 Jun 2017 10:14:02 GMT"),
                        stringContent(MULTIPLE_WILDCARDS)
                );

        Config config = Config.builder()
                .sources(from(
                        CollectionsHelper.mapOf(
                                "aaa.bbb.url", "URL0"
                        )))
                .overrides(url(getUrl("/override", server.getPort())))
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .build();

        waitForAssert(() -> config.get("aaa.bbb.url").asString(), is("URL1"));
    }

    @Test
    public void testMultipleMatchingWildcardsAnotherOrdering() throws MalformedURLException, InterruptedException {

        whenHttp(server).
                match(method(HEAD), uri("/override")).
                then(
                        status(OK_200),
                        contentType(MEDIA_TYPE_TEXT_JAVA_PROPERTIES),
                        header("Last-Modified", "Sat, 10 Jun 2017 10:14:02 GMT")
                );
        whenHttp(server).
                match(method(GET), uri("/override")).
                then(
                        status(OK_200),
                        contentType(MEDIA_TYPE_TEXT_JAVA_PROPERTIES),
                        header("Last-Modified", "Sat, 10 Jun 2017 10:14:02 GMT"),
                        stringContent(MULTIPLE_WILDCARDS_ANOTHER_ORDERING)
                );

        Config config = Config.builder()
                .sources(from(
                        CollectionsHelper.mapOf(
                                "aaa.bbb.url", "URL0"
                        )))
                .overrides(url(getUrl("/override", server.getPort())))
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .build();

        waitForAssert(() -> config.get("aaa.bbb.url").asString(), is("URL2"));
    }

    @Test
    public void testWildcardsChanges() throws MalformedURLException, InterruptedException {

        whenHttp(server).
                match(method(GET), uri("/override")).
                then(
                        status(OK_200),
                        contentType(MEDIA_TYPE_TEXT_JAVA_PROPERTIES),
                        stringContent(WILDCARDS)
                );

        whenHttp(server).
                match(method(GET), uri("/config")).
                then(
                        status(OK_200),
                        contentType(MEDIA_TYPE_TEXT_JAVA_PROPERTIES),
                        stringContent(CONFIG)
                );

        Config config = Config.builder()
                .sources(ConfigSources.url(getUrl("/config", server.getPort())))
                .overrides(url(getUrl("/override", server.getPort()))
                                   .pollingStrategy(PollingStrategies.regular(Duration.ofMillis(50))))
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .build();

        assertThat(config.get("aaa.bbb.url").asString(), is("URL1"));

        // register subscriber
        TestingConfigChangeSubscriber subscriber = new TestingConfigChangeSubscriber();
        config.get("aaa.bbb.url").changes().subscribe(subscriber);
        subscriber.request1();

        whenHttp(server).
                match(method(GET), uri("/override")).
                then(
                        status(OK_200),
                        contentType(MEDIA_TYPE_TEXT_JAVA_PROPERTIES),
                        stringContent(NEW_WILDCARDS)
                );

        // wait for event
        Config newConfig = subscriber.getLastOnNext(1000, true);

        // new: key exists
        assertThat(newConfig.asString(), is("URL2"));

    }

    @Test
    public void testWildcardsSupplier() throws MalformedURLException, InterruptedException {

        whenHttp(server).
                match(method(GET), uri("/override")).
                then(
                        status(OK_200),
                        contentType(MEDIA_TYPE_TEXT_JAVA_PROPERTIES),
                        stringContent(WILDCARDS)
                );

        whenHttp(server).
                match(method(GET), uri("/config")).
                then(
                        status(OK_200),
                        contentType(MEDIA_TYPE_TEXT_JAVA_PROPERTIES),
                        stringContent(CONFIG)
                );

        Config config = Config.builder()
                .sources(ConfigSources.url(getUrl("/config", server.getPort())))
                .overrides(url(getUrl("/override", server.getPort()))
                                   .pollingStrategy(PollingStrategies.regular(Duration.ofMillis(10))))
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .build();

        assertThat(config.get("aaa.bbb.url").asString(), is("URL1"));

        whenHttp(server).
                match(method(GET), uri("/override")).
                then(
                        status(OK_200),
                        contentType(MEDIA_TYPE_TEXT_JAVA_PROPERTIES),
                        stringContent(NO_WILDCARDS)
                );

        waitForAssert(() -> config.get("aaa.bbb.url").asStringSupplier().get(), is("URL0"));

    }

    @Test
    public void testConfigChangingWithOverrideSource() throws MalformedURLException, InterruptedException {

        whenHttp(server).
                match(method(GET), uri("/override")).
                then(
                        status(OK_200),
                        contentType(MEDIA_TYPE_TEXT_JAVA_PROPERTIES),
                        stringContent(WILDCARDS)
                );

        whenHttp(server).
                match(method(GET), uri("/config")).
                then(
                        status(OK_200),
                        contentType(MEDIA_TYPE_TEXT_JAVA_PROPERTIES),
                        stringContent(CONFIG)
                );

        Config config = Config.builder()
                .sources(ConfigSources.url(getUrl("/config", server.getPort()))
                                 .pollingStrategy(PollingStrategies.regular(Duration.ofMillis(10))))
                .overrides(url(getUrl("/override", server.getPort()))
                                   .pollingStrategy(PollingStrategies.regular(Duration.ofMillis(10))))
                //                .addFilter(new OverrideConfigFilter(CollectionsHelper.mapOf(Pattern.compile("\\w+\\.\\w+\\.url"), "URL1")))
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .build();

        assertThat(config.get("aaa.bbb.url").asString(), is("URL1"));

        whenHttp(server).
                match(method(GET), uri("/config")).
                then(
                        status(OK_200),
                        contentType(MEDIA_TYPE_TEXT_JAVA_PROPERTIES),
                        stringContent(CONFIG2)
                );

        waitForAssert(() -> config.get("aaa.bbb.url").asOptionalStringSupplier().get(), is(Optional.empty()));

    }

    @Test
    public void testConfigChangingWithFilters() throws MalformedURLException, InterruptedException {

        whenHttp(server).
                match(method(GET), uri("/override")).
                then(
                        status(OK_200),
                        contentType(MEDIA_TYPE_TEXT_JAVA_PROPERTIES),
                        stringContent(WILDCARDS)
                );

        whenHttp(server).
                match(method(GET), uri("/config")).
                then(
                        status(OK_200),
                        contentType(MEDIA_TYPE_TEXT_JAVA_PROPERTIES),
                        stringContent(CONFIG)
                );

        Config config = Config.builder()
                .sources(ConfigSources.url(getUrl("/config", server.getPort()))
                                 .pollingStrategy(PollingStrategies.regular(Duration.ofMillis(10))))
                .overrides(url(getUrl("/override", server.getPort())))
                .addFilter(new OverrideConfigFilter(() -> OverrideSource.OverrideData.fromWildcards(
                        CollectionsHelper.mapOf("*.*.url", "URL1")
                                .entrySet()
                                .stream()
                                .map(e -> new AbstractMap.SimpleEntry<>(e.getKey(), e.getValue()))
                                .collect(Collectors.toList())).data()))
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .build();

        assertThat(config.get("aaa.bbb.url").asString(), is("URL1"));

        whenHttp(server).
                match(method(GET), uri("/config")).
                then(
                        status(OK_200),
                        contentType(MEDIA_TYPE_TEXT_JAVA_PROPERTIES),
                        stringContent(CONFIG2)
                );

        waitForAssert(() -> config.get("aaa.bbb.url").asOptionalStringSupplier().get(), is(Optional.empty()));

    }

    private static URL getUrl(String path, int port) throws MalformedURLException {
        return new URL(String.format("http://127.0.0.1:%d%s", port, path));
    }

    private static final String CONFIG = "aaa.bbb.url = URL0\n";

    private static final String CONFIG2 = "bbb = ahoj\n";

}
