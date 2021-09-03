/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates.
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

import io.helidon.config.spi.ConfigParser.Content;

import com.xebialabs.restito.server.StubServer;
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
import static io.helidon.config.PropertiesConfigParser.MEDIA_TYPE_TEXT_JAVA_PROPERTIES;
import static org.glassfish.grizzly.http.Method.GET;
import static org.glassfish.grizzly.http.Method.HEAD;
import static org.glassfish.grizzly.http.util.HttpStatus.OK_200;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

/**
 * Tests {@link io.helidon.config.UrlConfigSource} with running {@link StubServer Restito Server}.
 */
public class UrlConfigSourceServerMockTest {

    private static final String TEST_MEDIA_TYPE = "my/media/type";
    private static final String TEST_CONFIG = "test-key = test-value";

    private StubServer server;

    @BeforeEach
    public void before() {
        server = new StubServer().run();
    }

    @AfterEach
    public void after() {
        server.stop();
    }

    @Test
    public void testDataTimestamp() throws IOException {
        whenHttp(server)
                .match(method(GET), uri("/application.properties"))
                .then(
                        status(OK_200),
                        contentType(MEDIA_TYPE_TEXT_JAVA_PROPERTIES),
                        header("Last-Modified", "Sat, 10 Jun 2017 10:14:02 GMT"),
                        stringContent(TEST_CONFIG)
                );

        UrlConfigSource configSource = ConfigSources
                .url(new URL(String.format("http://127.0.0.1:%d/application.properties", server.getPort())))
                .build();

        Optional<Instant> dataStamp = configSource.load().flatMap(Content::stamp).map(Instant.class::cast);

        assertThat(dataStamp.get(), is(Instant.parse("2017-06-10T10:14:02.00Z")));

        verifyHttp(server).once(
                method(GET),
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

        UrlConfigSource configSource = ConfigSources
                .url(new URL(String.format("http://127.0.0.1:%d/application.properties", server.getPort())))
                .build();

        //GET
        Instant stamp = (Instant) configSource.load().get().stamp().get();
        //just HEAD - same "Last-Modified"
        assertThat(configSource.isModified(stamp), is(false));

        verifyHttp(server).times(
                1,
                method(HEAD),
                uri("/application.properties")
        );
        verifyHttp(server).once(
                method(GET),
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

        UrlConfigSource configSource = ConfigSources
                .url(new URL(String.format("http://127.0.0.1:%d/application.properties", server.getPort())))
                .build();


        //GET
        Instant stamp = (Instant) configSource.load().get().stamp().get();

        //HEAD
        whenHttp(server).
                match(method(HEAD), uri("/application.properties")).
                then(
                        status(OK_200),
                        contentType(MEDIA_TYPE_TEXT_JAVA_PROPERTIES),
                        header("Last-Modified", "Sat, 10 Jun 2017 10:14:03 GMT"),
                        stringContent(TEST_CONFIG)
                );

        //just HEAD - "Last-Modified" changed
        assertThat(configSource.isModified(stamp), is(true));

        verifyHttp(server).times(
                1,
                method(HEAD),
                uri("/application.properties")
        );
        verifyHttp(server).times(
                1,
                method(GET),
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

        UrlConfigSource configSource = ConfigSources
                .url(new URL(String.format("http://127.0.0.1:%d/application.properties", server.getPort())))
                .mediaType(TEST_MEDIA_TYPE)
                .build();

        Content content = configSource.load().get();

        assertThat(content.mediaType(), is(Optional.of(TEST_MEDIA_TYPE)));
        assertThat(TestHelper.inputStreamToString(content.data()), is(TEST_CONFIG));

        verifyHttp(server).once(
                method(GET),
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

        UrlConfigSource configSource = ConfigSources
                .url(new URL(String.format("http://127.0.0.1:%d/application.properties", server.getPort())))
                .build();

        Content content = configSource.load().get();

        assertThat(content.mediaType(), is(Optional.of(MEDIA_TYPE_TEXT_JAVA_PROPERTIES)));
        assertThat(TestHelper.inputStreamToString(content.data()), is(TEST_CONFIG));

        verifyHttp(server).once(
                method(GET),
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

        UrlConfigSource configSource = ConfigSources
                .url(new URL(String.format("http://127.0.0.1:%d/application.properties", server.getPort())))
                .build();

        Content content = configSource.load().get();

        assertThat(content.mediaType(), is(Optional.of(TEST_MEDIA_TYPE)));
        assertThat(TestHelper.inputStreamToString(content.data()), is(TEST_CONFIG));

        verifyHttp(server).once(
                method(GET),
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

        assertThat(configSource.load().get().mediaType(), is(Optional.empty()));
    }

}
