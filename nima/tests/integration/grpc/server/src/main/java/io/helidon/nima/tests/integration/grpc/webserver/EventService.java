package io.helidon.nima.tests.integration.grpc.webserver;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import io.helidon.nima.grpc.events.Events;
import io.helidon.nima.grpc.webserver.GrpcService;

import com.google.protobuf.Descriptors;
import com.google.protobuf.Empty;
import io.grpc.stub.StreamObserver;

import static io.helidon.nima.grpc.webserver.ResponseHelper.complete;

public class EventService implements GrpcService {

    private final List<Listener> listeners = new ArrayList<>();

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

    private void send(Events.Message message, StreamObserver<Empty> observer) {
        String text = message.getText();
        for (Listener listener : listeners) {
            listener.send(text);
        }
        complete(observer, Empty.getDefaultInstance());
    }

    private class Listener
        implements StreamObserver<Events.EventRequest> {

        private final StreamObserver<Events.EventResponse> responses;

        private final Set<Long> subscribers = new HashSet<>();

        public Listener(StreamObserver<Events.EventResponse> responses) {
            this.responses = responses;
        }

        public void send(String text) {
            for (long id : subscribers) {
                responses.onNext(Events.EventResponse.newBuilder()
                                         .setEvent(Events.Event.newBuilder()
                                                           .setId(id)
                                                           .setText(text).build())
                                         .build());
            }
        }

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
