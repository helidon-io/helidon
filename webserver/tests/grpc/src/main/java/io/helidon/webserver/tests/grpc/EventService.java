/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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

package io.helidon.webserver.tests.grpc;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import io.helidon.webserver.grpc.events.Events;
import io.helidon.webserver.grpc.GrpcService;

import com.google.protobuf.Descriptors;
import com.google.protobuf.Empty;
import io.grpc.stub.StreamObserver;

import static io.helidon.grpc.core.ResponseHelper.complete;

/**
 * A simple service to send events to subscriber listeners.
 * <p/>
 * The main purpose of this service it so test long-running bidirectional
 * gRPC requests alongside concurrent unary requests so that there are
 * multiple in-flight requests at the same time on the same gRPC channel.
 */
public class EventService implements GrpcService {

    /**
     * The registered listeners.
     */
    private final List<Listener> listeners = new ArrayList<>();

    /**
     * A lock to control mutating the listeners list.
     */
    private final Lock lock = new ReentrantLock();

    @Override
    public Descriptors.FileDescriptor proto() {
        return Events.getDescriptor();
    }

    @Override
    public void update(Routing router) {
        router.unary("Send", this::send)
                .bidi("Events", this::events);
    }

    /**
     * The events bidirectional request.
     * <p/>
     * Clients send subscribe or un-subscribe requests up the channel, and the server
     * sends events back down the channel.
     *
     * @param responses  the {@link StreamObserver} to receive event responses
     *
     * @return the {@link StreamObserver} to receive subscription requests
     */
    private StreamObserver<Events.EventRequest> events(StreamObserver<Events.EventResponse> responses) {
        lock.lock();
        try {
            Listener listener = new Listener(responses);
            listeners.add(listener);
            return listener;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Send an event to all subscribed listeners.
     *
     * @param message   the message to send
     * @param observer  the observer that is completed when events have been sent
     */
    private void send(Events.Message message, StreamObserver<Empty> observer) {
        String text = message.getText();
        Iterator<Listener> it = listeners.iterator();
        while (it.hasNext()) {
            Listener listener = it.next();
            listener.send(text);
        }
        complete(observer, Empty.getDefaultInstance());
    }

    /**
     * An implementation of a {@link StreamObserver} used to
     * subscribe or unsubscribe listeners for a specific
     * bidirectional channel.
     */
    private class Listener
        implements StreamObserver<Events.EventRequest> {

        /**
         * The {@link StreamObserver} to send events to.
         */
        private final StreamObserver<Events.EventResponse> responses;

        /**
         * The set of subscriber identifiers.
         */
        private final Set<Long> subscribers = new HashSet<>();

        /**
         * Create a Listener.
         *
         * @param responses {@link StreamObserver} to send events to
         */
        public Listener(StreamObserver<Events.EventResponse> responses) {
            this.responses = responses;
        }

        /**
         * Send an event to all subscribers.
         *
         * @param text the message to send
         */
        public void send(String text) {
            Iterator<Long> it = subscribers.iterator();
            while(it.hasNext()) {
                long id = it.next();
                responses.onNext(Events.EventResponse.newBuilder()
                                         .setEvent(Events.Event.newBuilder()
                                                           .setId(id)
                                                           .setText(text).build())
                                         .build());
            }
        }

        /**
         * Handle messages from the client to subscribe or
         * unsubscribe. Listeners are just registered by
         * keeping an ID and events are sent to for each
         * subscribed ID.
         *
         * @param request a request to subscribe or unsubscribe
         */
        @Override
        public void onNext(Events.EventRequest request) {
            long id = request.getId();
            if (request.getAction() == Events.EventRequest.Action.SUBSCRIBE) {
                subscribers.add(id);
                responses.onNext(Events.EventResponse.newBuilder()
                                         .setSubscribed(Events.Subscribed.newBuilder().setId(id).build())
                                         .build());
            } else {
                subscribers.remove(id);
            }
        }

        @Override
        public void onError(Throwable throwable) {
            close();
        }

        @Override
        public void onCompleted() {
            close();
        }

        private void close() {
            lock.lock();
            try {
                subscribers.clear();
                listeners.remove(this);
            } finally {
                lock.unlock();
            }
        }
    }
}
