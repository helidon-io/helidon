/*
 * Copyright (c) 2023, 2026 Oracle and/or its affiliates.
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

package io.helidon.webserver.tests.sse;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.http.HeaderNames;
import io.helidon.http.Status;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http1.Http1ClientResponse;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.http.AltSvc;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http1.Http1Config;
import io.helidon.webserver.http1.Http1ConnectionSelector;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;
import io.helidon.webserver.testing.junit5.SetUpServer;

import org.junit.jupiter.api.Test;

import static io.helidon.http.HeaderValues.ACCEPT_EVENT_STREAM;
import static io.helidon.http.HeaderValues.ACCEPT_JSON;
import static io.helidon.http.HeaderValues.CONTENT_TYPE_JSON;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@ServerTest
class SseServerTest extends SseBaseTest {
    private static final String CUSTOM_ALT_SVC = "h2=\":443\"";
    private static final String BEFORE_SEND_CALLS = "Before-Send-Calls";
    private static final String BEFORE_SEND_CALLS_AFTER_FAILED_SINK_CREATION =
            "Before-Send-Calls-After-Failed-Sink-Creation";
    private static final String ALT_SVC_PRESENT_AFTER_FAILED_SINK_CREATION =
            "Alt-Svc-Present-After-Failed-Sink-Creation";

    SseServerTest(WebServer webServer) {
        super(webServer);
    }

    @SetUpRoute
    static void routing(HttpRules rules) {
        rules.get("/sseString1", SseServerTest::sseString1);
        rules.get("/sseBeforeSendStatus", (req, res) -> {
            res.beforeSend(() -> res.status(Status.BAD_REQUEST_400));
            sseString1(req, res);
        });
        rules.get("/sseCustomAltSvc", (req, res) -> {
            AtomicInteger beforeSendCalls = new AtomicInteger();
            res.beforeSend(() -> {
                res.header(HeaderNames.ALT_SVC, CUSTOM_ALT_SVC);
                res.header(BEFORE_SEND_CALLS, Integer.toString(beforeSendCalls.incrementAndGet()));
            });
            sseString1(req, res);
        });
        rules.get("/sseRejected", (req, res) -> {
            AtomicBoolean beforeSendCalled = new AtomicBoolean();
            res.beforeSend(() -> beforeSendCalled.set(true));
            try {
                sseString1(req, res);
            } catch (RuntimeException e) {
                res.header("Before-Send-Called-During-Sink-Lookup", Boolean.toString(beforeSendCalled.get()));
                throw e;
            }
        });
        rules.get("/sseProviderCreationFailure", (req, res) -> {
            AtomicInteger beforeSendCalls = new AtomicInteger();
            res.beforeSend(() -> res.header(BEFORE_SEND_CALLS,
                                            Integer.toString(beforeSendCalls.incrementAndGet())));
            res.header(CONTENT_TYPE_JSON);
            try {
                sseString1(req, res);
            } catch (IllegalStateException _) {
                res.header(BEFORE_SEND_CALLS_AFTER_FAILED_SINK_CREATION,
                           Integer.toString(beforeSendCalls.get()));
                res.header(ALT_SVC_PRESENT_AFTER_FAILED_SINK_CREATION,
                           Boolean.toString(res.headers().contains(HeaderNames.ALT_SVC)));
                res.headers().remove(HeaderNames.CONTENT_TYPE);
                res.send("ok");
            }
        });
        rules.get("/sseString2", SseServerTest::sseString2);
        rules.get("/sseDelayed", SseServerTest::sseDelayed);
        rules.get("/sseFlush", SseServerTest::sseFlush);
        rules.get("/sseJson1", SseServerTest::sseJson1);
        rules.get("/sseJson2", SseServerTest::sseJson2);
        rules.get("/sseMixed", SseServerTest::sseMixed);
        rules.get("/sseIdComment", SseServerTest::sseIdComment);
        rules.get("/sseCommentOnly", SseServerTest::sseCommentOnly);
        rules.get("/sseFieldLineBreaks", SseServerTest::sseFieldLineBreaks);
        rules.get("/sseDataCarriageReturn", SseServerTest::sseDataCarriageReturn);
    }

    @SetUpServer
    static void server(WebServerConfig.Builder server) {
        server.writeQueueLength(2);
        server.smartAsyncWrites(true);
        server.addConnectionSelector(Http1ConnectionSelector.builder()
                                             .config(Http1Config.builder()
                                                             .altSvc(AltSvc.builder().build())
                                                             .build())
                                             .build());
    }

    @Test
    void testSseString1() throws Exception {
        testSse("/sseString1", "data:hello", "data:world");
    }

    @Test
    void testSseString2() throws Exception {
        testSse("/sseString2", "data:1", "data:2", "data:3");
    }

    @Test
    void testSseHeadersAreSentBeforeFirstEvent() throws Exception {
        CountDownLatch delayedLatch = new CountDownLatch(1);
        SseBaseTest.delayedLatch(delayedLatch);
        try (SimpleSseClient sseClient = SimpleSseClient.create("localhost",
                                                                webServer().port(),
                                                                "/sseDelayed",
                                                                Duration.ofSeconds(10))) {
            sseClient.awaitHeaders();

            delayedLatch.countDown();
            assertThat(sseClient.nextEvent(), is("data:delayed"));
        } finally {
            delayedLatch.countDown();
            SseBaseTest.delayedLatch(new CountDownLatch(0));
        }
    }

    @Test
    void testSseAdvertisesAltSvc() {
        Http1Client client = Http1Client.builder()
                .baseUri("http://localhost:" + webServer().port())
                .build();
        try (Http1ClientResponse response = client.get("/sseString1").request()) {
            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.headers().first(HeaderNames.ALT_SVC).orElse(null),
                       is("h3=\":" + webServer().port() + "\""));
        } finally {
            client.closeResource();
        }
    }

    @Test
    void testBeforeSendStatusIsAppliedToSseWithAltSvcConfigured() {
        Http1Client client = Http1Client.builder()
                .baseUri("http://localhost:" + webServer().port())
                .build();
        try (Http1ClientResponse response = client.get("/sseBeforeSendStatus").request()) {
            assertThat(response.status(), is(Status.BAD_REQUEST_400));
            assertThat(response.headers().contains(HeaderNames.ALT_SVC), is(false));
        } finally {
            client.closeResource();
        }
    }

    @Test
    void testSsePreservesBeforeSendAltSvc() {
        Http1Client client = Http1Client.builder()
                .baseUri("http://localhost:" + webServer().port())
                .build();
        try (Http1ClientResponse response = client.get("/sseCustomAltSvc").request()) {
            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.headers().first(HeaderNames.ALT_SVC).orElse(null), is(CUSTOM_ALT_SVC));
            assertThat(response.headers().first(HeaderNames.create(BEFORE_SEND_CALLS)).orElse(null), is("1"));
        } finally {
            client.closeResource();
        }
    }

    @Test
    void testRejectedSseDoesNotAdvertiseAltSvcOrRunBeforeSendDuringSinkLookup() {
        Http1Client client = Http1Client.builder()
                .baseUri("http://localhost:" + webServer().port())
                .build();
        try (Http1ClientResponse response = client.get("/sseRejected").header(ACCEPT_JSON).request()) {
            assertThat(response.status(), is(Status.NOT_ACCEPTABLE_406));
            assertThat(response.headers().contains(HeaderNames.ALT_SVC), is(false));
            assertThat(response.headers()
                               .first(HeaderNames.create("Before-Send-Called-During-Sink-Lookup"))
                               .orElse(null),
                       is("false"));
        } finally {
            client.closeResource();
        }
    }

    @Test
    void testFailedSseProviderCreationRollsBackAndReadvertisesAltSvc() {
        Http1Client client = Http1Client.builder()
                .baseUri("http://localhost:" + webServer().port())
                .build();
        try (Http1ClientResponse response = client.get("/sseProviderCreationFailure")
                .header(ACCEPT_EVENT_STREAM)
                .request()) {
            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.headers()
                               .first(HeaderNames.create(BEFORE_SEND_CALLS_AFTER_FAILED_SINK_CREATION))
                               .orElse(null),
                       is("0"));
            assertThat(response.headers()
                               .first(HeaderNames.create(ALT_SVC_PRESENT_AFTER_FAILED_SINK_CREATION))
                               .orElse(null),
                       is("false"));
            assertThat(response.headers().first(HeaderNames.create(BEFORE_SEND_CALLS)).orElse(null), is("1"));
            assertThat(response.headers().first(HeaderNames.ALT_SVC).orElse(null),
                       is("h3=\":" + webServer().port() + "\""));
            assertThat(response.entity().as(String.class), is("ok"));
        } finally {
            client.closeResource();
        }
    }

    @Test
    void testSseFlushesEachEvent() throws Exception {
        CountDownLatch flushLatch = new CountDownLatch(1);
        SseBaseTest.flushLatch(flushLatch);
        try (SimpleSseClient sseClient = SimpleSseClient.create("localhost",
                                                                webServer().port(),
                                                                "/sseFlush",
                                                                Duration.ofSeconds(10))) {
            assertThat(sseClient.nextEvent(), is("data:first"));

            flushLatch.countDown();
            assertThat(sseClient.nextEvent(), is("data:second"));
        } finally {
            flushLatch.countDown();
            SseBaseTest.flushLatch(new CountDownLatch(0));
        }
    }

    @Test
    void testSseJson1() throws Exception {
        testSse("/sseJson1", "data:{\"hello\":\"world\"}");
    }

    @Test
    void testSseJson2() throws Exception {
        testSse("/sseJson2", "data:{\"hello\":\"world\"}");
    }

    @Test
    void testSseMixed() throws Exception {
        testSse("/sseMixed", "data:hello", "data:world",
                "data:{\"hello\":\"world\"}", "data:{\"hello\":\"world\"}");
    }

    @Test
    void testIdComment() throws Exception {
        testSse("/sseIdComment", ":This is a comment\nid:1\ndata:hello");
    }

    @Test
    void testCommentOnly() throws Exception {
        testSse("/sseCommentOnly", ":This is a comment");
    }

    @Test
    void testFieldLineBreaksDoNotCreateNewFields() throws Exception {
        testSse("/sseFieldLineBreaks",
                ":comment\n:id:injected-comment\n:data:injected-comment\n:event:injected-comment\n"
                        + "id:id id:injected-id id:injected-id id:injected-id\n"
                        + "event:name event:injected-event event:injected-event event:injected-event\n"
                        + "data:payload");
    }

    @Test
    void testDataCarriageReturnDoesNotCreateNewField() throws Exception {
        testSse("/sseDataCarriageReturn", "data:line\ndata:event:injected-data");
    }

    @Test
    void testWrongAcceptType() {
        Http1Client client = Http1Client.builder()
                .baseUri("http://localhost:" + webServer().port())
                .build();
        try (Http1ClientResponse response = client.get("/sseString1").header(ACCEPT_JSON).request()) {
            assertThat(response.status(), is(Status.NOT_ACCEPTABLE_406));
        }
    }
}
