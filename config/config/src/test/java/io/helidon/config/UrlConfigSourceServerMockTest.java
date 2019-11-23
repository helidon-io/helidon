/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.config;

import java.io.IOException;
import java.net.URL;
import java.time.Instant;
import java.util.Optional;

import io.helidon.config.spi.ConfigContext;
import io.helidon.config.spi.ConfigParser;

import com.xebialabs.restito.server.StubServer;
import org.glassfish.grizzly.http.Method;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.xebialabs.restito.builder.stub.StubHttp.whenHttp;
import static com.xebialabs.restito.builder.verify.VerifyHttp.verifyHttp;
import static com.xebialabs.restito.semantics.Action.contentType;
import static com.xebialabs.restito.semantics.Action.header;
import static com.xebialabs.restito.semantics.Action.status;
import static com.xebialabs.restito.semantics.Action.stringContent;
import static com.xebialabs.restito.semantics.Condition.get;
import static com.xebialabs.restito.semantics.Condition.method;
import static com.xebialabs.restito.semantics.Condition.uri;
import static io.helidon.config.internal.PropertiesConfigParser.MEDIA_TYPE_TEXT_JAVA_PROPERTIES;
import static org.glassfish.grizzly.http.Method.HEAD;
import static org.glassfish.grizzly.http.util.HttpStatus.OK_200;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.Is.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests {@link io.helidon.config.UrlConfigSource} with running {@link StubServer Restito Server}.
 */
public class UrlConfigSourceServerMockTest {

    private static final String TEST_MEDIA_TYPE = "my/media/type";
    private static final String TEST_CONFIG = "test-key = test-value";

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
    public void testDataTimestamp() throws IOException {
        whenHttp(server).
                match(method(HEAD), uri("/application.properties")).
                then(
                        status(OK_200),
                        contentType(MEDIA_TYPE_TEXT_JAVA_PROPERTIES),
                        header("Last-Modified", "Sat, 10 Jun 2017 10:14:02 GMT"),
                        stringContent(TEST_CONFIG)
                );

        UrlConfigSource configSource = (UrlConfigSource) ConfigSources
                .url(new URL(String.format("http://127.0.0.1:%d/application.properties", server.getPort())))
                .build();

        Optional<Instant> dataStamp = configSource.dataStamp();

        assertThat(dataStamp.get(), is(Instant.parse("2017-06-10T10:14:02.00Z")));

        verifyHttp(server).once(
                method(HEAD),
                uri("/application.properties")
        );
    }

    @Test
    public void testDoNotReloadSameContent() throws IOException {
        //HEAD
        whenHttp(server).
                match(method(HEAD), uri("/application.properties")).
                then(
                        status(OK_200),
                        contentType(MEDIA_TYPE_TEXT_JAVA_PROPERTIES),
                        header("Last-Modified", "Sat, 10 Jun 2017 10:14:02 GMT"),
                        stringContent(TEST_CONFIG)
                );
        //GET
        whenHttp(server).
                match(get("/application.properties")).
                then(
                        status(OK_200),
                        contentType(MEDIA_TYPE_TEXT_JAVA_PROPERTIES),
                        header("Last-Modified", "Sat, 10 Jun 2017 10:14:02 GMT"),
                        stringContent(TEST_CONFIG)
                );

        ConfigContext context = mock(ConfigContext.class);
        when(context.findParser("text/x-java-properties"))
                .thenReturn(Optional.of(ConfigParsers.properties()));

        UrlConfigSource configSource = (UrlConfigSource) ConfigSources
                .url(new URL(String.format("http://127.0.0.1:%d/application.properties", server.getPort())))
                .build();

        //INIT
        configSource.init(context);
        //HEAD & GET
        configSource.load();
        //just HEAD - same "Last-Modified"
        configSource.load();

        verifyHttp(server).times(
                2,
                method(HEAD),
                uri("/application.properties")
        );
        verifyHttp(server).once(
                method(Method.GET),
                uri("/application.properties")
        );
    }

    @Test
    public void testDoReloadChangedContent() throws IOException {
        //HEAD
        whenHttp(server).
                match(method(HEAD), uri("/application.properties")).
                then(
                        status(OK_200),
                        contentType(MEDIA_TYPE_TEXT_JAVA_PROPERTIES),
                        header("Last-Modified", "Sat, 10 Jun 2017 10:14:02 GMT"),
                        stringContent(TEST_CONFIG)
                );
        //GET
        whenHttp(server).
                match(get("/application.properties")).
                then(
                        status(OK_200),
                        contentType(MEDIA_TYPE_TEXT_JAVA_PROPERTIES),
                        header("Last-Modified", "Sat, 10 Jun 2017 10:14:02 GMT"),
                        stringContent(TEST_CONFIG)
                );

        ConfigContext context = mock(ConfigContext.class);
        when(context.findParser("text/x-java-properties"))
                .thenReturn(Optional.of(ConfigParsers.properties()));

        UrlConfigSource configSource = (UrlConfigSource) ConfigSources
                .url(new URL(String.format("http://127.0.0.1:%d/application.properties", server.getPort())))
                .build();

        //INIT
        configSource.init(context);
        //HEAD & GET
        configSource.load();

        //HEAD
        whenHttp(server).
                match(method(HEAD), uri("/application.properties")).
                then(
                        status(OK_200),
                        contentType(MEDIA_TYPE_TEXT_JAVA_PROPERTIES),
                        header("Last-Modified", "Sat, 10 Jun 2017 10:14:03 GMT"),
                        stringContent(TEST_CONFIG)
                );

        //HEAD & GET - "Last-Modified" changed
        configSource.load();

        verifyHttp(server).times(
                2,
                method(HEAD),
                uri("/application.properties")
        );
        verifyHttp(server).times(
                2,
                method(Method.GET),
                uri("/application.properties")
        );
    }

    @Test
    public void testContentMediaTypeSet() throws IOException {
        whenHttp(server).
                match(get("/application.properties")).
                then(
                        status(OK_200),
                        contentType(MEDIA_TYPE_TEXT_JAVA_PROPERTIES),
                        stringContent(TEST_CONFIG)
                );

        UrlConfigSource configSource = (UrlConfigSource) ConfigSources
                .url(new URL(String.format("http://127.0.0.1:%d/application.properties", server.getPort())))
                .mediaType(TEST_MEDIA_TYPE)
                .build();

        ConfigParser.Content content = configSource.content();

        assertThat(content.mediaType(), is(TEST_MEDIA_TYPE));
        assertThat(ConfigHelperTest.readerToString(content.asReadable()), is(TEST_CONFIG));

        verifyHttp(server).once(
                method(Method.GET),
                uri("/application.properties")
        );
    }

    @Test
    public void testContentMediaTypeFromResponse() throws IOException {
        whenHttp(server).
                match(get("/application.properties")).
                then(
                        status(OK_200),
                        contentType(MEDIA_TYPE_TEXT_JAVA_PROPERTIES),
                        stringContent(TEST_CONFIG)
                );

        UrlConfigSource configSource = (UrlConfigSource) ConfigSources
                .url(new URL(String.format("http://127.0.0.1:%d/application.properties", server.getPort())))
                .build();

        ConfigParser.Content content = configSource.content();

        assertThat(content.mediaType(), is(MEDIA_TYPE_TEXT_JAVA_PROPERTIES));
        assertThat(ConfigHelperTest.readerToString(content.asReadable()), is(TEST_CONFIG));

        verifyHttp(server).once(
                method(Method.GET),
                uri("/application.properties")
        );
    }

    @Test
    public void testContentMediaTypeGuessed() throws IOException {
        whenHttp(server).
                match(get("/application.properties")).
                then(
                        status(OK_200),
                        contentType(TEST_MEDIA_TYPE),
                        stringContent(TEST_CONFIG)
                );

        UrlConfigSource configSource = (UrlConfigSource) ConfigSources
                .url(new URL(String.format("http://127.0.0.1:%d/application.properties", server.getPort())))
                .build();

        ConfigParser.Content content = configSource.content();

        assertThat(content.mediaType(), is(TEST_MEDIA_TYPE));
        assertThat(ConfigHelperTest.readerToString(content.asReadable()), is(TEST_CONFIG));

        verifyHttp(server).once(
                method(Method.GET),
                uri("/application.properties")
        );
    }

    @Test
    public void testContentMediaTypeUnknown() throws IOException {
        whenHttp(server).
                match(get("/application.unknown")).
                then(
                        status(OK_200),
                        stringContent(TEST_CONFIG)
                );

        UrlConfigSource configSource = (UrlConfigSource) ConfigSources
                .url(new URL(String.format("http://127.0.0.1:%d/application.unknown", server.getPort())))
                .build();

        assertThat(configSource.content().mediaType(), is(nullValue()));
    }

}
