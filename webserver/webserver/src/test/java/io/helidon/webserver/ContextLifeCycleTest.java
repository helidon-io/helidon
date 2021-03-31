/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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
package io.helidon.webserver;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow.Subscriber;
import java.util.concurrent.Flow.Subscription;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import io.helidon.common.context.Context;
import io.helidon.common.context.Contexts;
import io.helidon.common.http.DataChunk;
import io.helidon.common.http.Http.ResponseStatus;
import io.helidon.webclient.WebClient;
import io.helidon.webclient.WebClientResponse;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Tests that {@link ServerRequest#context()} is returned by {@link Contexts#context()} throughout the request
 * life-cycle.
 */
class ContextLifeCycleTest {

    private static String contextId() {
        return Contexts.context().map(Context::id).orElse(null);
    }

    @Test
    void testContextMediaSupport() {
        Handler handler = (req, res) -> {
            String cid = req.context().id();
            req.content().as(String.class).thenAccept(payload -> {
                if (cid.equals(contextId())) {
                    res.send();
                } else {
                    res.status(400).send();
                }
            });
        };

        WebServer webServer = WebServer.builder(Routing.builder().post(handler))
                                       .build()
                                       .start()
                                       .await(10, TimeUnit.SECONDS);

        ResponseStatus responseStatus = WebClient.builder()
                                                 .baseUri("http://localhost:" + webServer.port())
                                                 .build()
                                                 .post()
                                                 .submit("some-payload")
                                                 .map(WebClientResponse::status)
                                                 .onTerminate(webServer::shutdown)
                                                 .await(10, TimeUnit.SECONDS);

        assertThat(responseStatus.code(), is(200));
    }

    @Test
    void testContextReactive() {
        Handler handler = (req, res) -> {
            String cid = req.context().id();
            req.content()
               .subscribe(new Subscriber<>() {
                   @Override
                   public void onSubscribe(Subscription subscription) {
                       subscription.request(Long.MAX_VALUE);
                   }

                   @Override
                   public void onNext(DataChunk item) {
                       item.release();
                       if (!cid.equals(contextId())) {
                           throw new IllegalStateException("Context invalid");
                       }
                   }

                   @Override
                   public void onError(Throwable throwable) {
                       res.send(throwable);
                   }

                   @Override
                   public void onComplete() {
                       if (!cid.equals(contextId())) {
                           res.send(new IllegalStateException("Context invalid"));
                       } else {
                           res.send();
                       }
                   }
               });
        };

        WebServer webServer = WebServer.builder(Routing.builder().post(handler))
                                       .build()
                                       .start()
                                       .await(10, TimeUnit.SECONDS);

        ResponseStatus responseStatus = WebClient.builder()
                                                 .baseUri("http://localhost:" + webServer.port())
                                                 .build()
                                                 .post()
                                                 .submit("some-payload")
                                                 .map(WebClientResponse::status)
                                                 .onTerminate(webServer::shutdown)
                                                 .await(10, TimeUnit.SECONDS);

        assertThat(responseStatus.code(), is(200));
    }

    @Test
    void testContextWhenSent() throws InterruptedException, ExecutionException, TimeoutException {
        CompletableFuture<String> requestCid = new CompletableFuture<>();
        CompletableFuture<String> whenSentCid = new CompletableFuture<>();
        Handler handler = (req, res) -> {
            requestCid.complete(req.context().id());
            req.content().as(String.class).thenAccept(payload -> res.send());
            res.whenSent().thenRun(() -> whenSentCid.complete(contextId()));
        };

        WebServer webServer = WebServer.builder(Routing.builder().post(handler))
                                       .build()
                                       .start()
                                       .await(10, TimeUnit.SECONDS);

        WebClient.builder()
                 .baseUri("http://localhost:" + webServer.port())
                 .build()
                 .post()
                 .submit("some-payload")
                 .onTerminate(webServer::shutdown)
                 .await(10, TimeUnit.SECONDS);

        assertThat(whenSentCid.get(10, TimeUnit.SECONDS), is(requestCid.get(10, TimeUnit.SECONDS)));
    }
}
