/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.nima.tests.integration.grpc.webserver;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.helidon.nima.grpc.events.EventServiceGrpc;
import io.helidon.nima.grpc.events.Events;
import io.helidon.nima.grpc.webserver.GrpcRouting;
import io.helidon.nima.testing.junit5.webserver.ServerTest;
import io.helidon.nima.testing.junit5.webserver.SetUpRoute;
import io.helidon.nima.webserver.Router;
import io.helidon.nima.webserver.WebServer;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@ServerTest
public class EventsTest {
    private final int port;

    protected ManagedChannel channel;

    protected EventServiceGrpc.EventServiceBlockingStub blockingStub;

    protected EventServiceGrpc.EventServiceStub stub;

    EventsTest(WebServer server) {
        this.port = server.port();
    }

    @SetUpRoute
    static void routing(Router.RouterBuilder<?> router) {
        router.addRouting(GrpcRouting.builder().service(new EventService()));
    }

    @BeforeEach
    void beforeEach() {
        channel = ManagedChannelBuilder.forAddress("localhost", port)
                .usePlaintext()
                .build();
        blockingStub = EventServiceGrpc.newBlockingStub(channel);
        stub = EventServiceGrpc.newStub(channel);
    }

    @AfterEach
    void afterEach() throws InterruptedException {
        blockingStub = null;
        stub = null;
        channel.shutdown();
        if (!channel.awaitTermination(10, TimeUnit.SECONDS)) {
            System.err.println("Failed to terminate channel");
        }
        if (!channel.isTerminated()) {
            System.err.println("Channel is not terminated!!!");
        }
    }

    @Test
    public void shouldReceiveEvents() throws Exception {
        TestObserver observer = new TestObserver();

        StreamObserver<Events.EventRequest> requests = stub.events(observer);

        CountDownLatch latch = observer.setLatch(2);
        requests.onNext(subscribe(19L));
        requests.onNext(subscribe(20L));

        assertThat(latch.await(1, TimeUnit.MINUTES), is(true));

        List<Events.EventResponse> responses = observer.getResponses();
        assertThat(responses.size(), is(2));

        Events.EventResponse response = responses.get(0);
        assertThat(response.getResponseTypeCase(), is(Events.EventResponse.ResponseTypeCase.SUBSCRIBED));
        Events.Subscribed subscribed = response.getSubscribed();
        assertThat(subscribed.getId(), is(19L));
        response = responses.get(1);
        assertThat(response.getResponseTypeCase(), is(Events.EventResponse.ResponseTypeCase.SUBSCRIBED));
        subscribed = response.getSubscribed();
        assertThat(subscribed.getId(), is(20L));

        observer.clear();
        latch = observer.setLatch(2);
        blockingStub.send(message("foo"));

        assertThat(latch.await(1, TimeUnit.MINUTES), is(true));

        responses = observer.getResponses();
        assertThat(responses.size(), is(2));

        response = responses.get(0);
        assertThat(response.getResponseTypeCase(), is(Events.EventResponse.ResponseTypeCase.EVENT));
        Events.Event event = response.getEvent();
        assertThat(event.getId(), is(19L));
        assertThat(event.getText(), is("foo"));

        response = responses.get(1);
        assertThat(response.getResponseTypeCase(), is(Events.EventResponse.ResponseTypeCase.EVENT));
        event = response.getEvent();
        assertThat(event.getId(), is(20L));
        assertThat(event.getText(), is("foo"));
    }

    private Events.EventRequest subscribe(long id) {
        return Events.EventRequest.newBuilder()
                .setAction(Events.EventRequest.Action.SUBSCRIBE)
                .setId(id)
                .build();
    }

    private Events.EventRequest unsubscribe(long id) {
        return Events.EventRequest.newBuilder()
                .setAction(Events.EventRequest.Action.UNSUBSCRIBE)
                .setId(id)
                .build();
    }

    private Events.Message message(String text) {
        return Events.Message.newBuilder()
                .setText(text)
                .build();
    }

    private static class TestObserver
            implements StreamObserver<Events.EventResponse> {

        private final List<Events.EventResponse> responses = new ArrayList<>();

        private CountDownLatch latch;

        public CountDownLatch setLatch(int count) {
            latch = new CountDownLatch(count);
            return latch;
        }

        public List<Events.EventResponse> getResponses() {
            return responses;
        }

        public void clear() {
            responses.clear();
        }

        @Override
        public void onNext(Events.EventResponse response) {
            responses.add(response);
            if (latch != null) {
                latch.countDown();
            }
        }

        @Override
        public void onError(Throwable throwable) {
        }

        @Override
        public void onCompleted() {
        }
    }
}
