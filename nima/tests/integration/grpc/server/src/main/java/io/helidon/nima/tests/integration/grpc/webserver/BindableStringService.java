package io.helidon.nima.tests.integration.grpc.webserver;

import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.helidon.nima.grpc.strings.StringServiceGrpc;
import io.helidon.nima.grpc.strings.Strings;
import io.helidon.nima.grpc.webserver.CollectingObserver;

import io.grpc.stub.StreamObserver;

import static io.helidon.nima.grpc.webserver.ResponseHelper.complete;
import static io.helidon.nima.grpc.webserver.ResponseHelper.stream;

public class BindableStringService
        extends StringServiceGrpc.StringServiceImplBase {

    @Override
    public void upper(Strings.StringMessage request, StreamObserver<Strings.StringMessage> observer) {
        String requestText = request.getText();
        complete(observer, Strings.StringMessage.newBuilder()
                .setText(requestText.toUpperCase(Locale.ROOT))
                .build());
    }

    @Override
    public void lower(Strings.StringMessage request, StreamObserver<Strings.StringMessage> observer) {
        String requestText = request.getText();
        complete(observer, Strings.StringMessage.newBuilder()
                .setText(requestText.toLowerCase(Locale.ROOT))
                .build());
    }

    @Override
    public void split(Strings.StringMessage request, StreamObserver<Strings.StringMessage> observer) {
        String[] parts = request.getText().split(" ");
        stream(observer, Stream.of(parts).map(this::response));
    }

    @Override
    public StreamObserver<Strings.StringMessage> join(StreamObserver<Strings.StringMessage> observer) {
        return new CollectingObserver<>(
                Collectors.joining(" "),
                observer,
                Strings.StringMessage::getText,
                this::response);
    }

    @Override
    public StreamObserver<Strings.StringMessage> echo(StreamObserver<Strings.StringMessage> observer) {
        return new StreamObserver<>() {
            public void onNext(Strings.StringMessage value) {
                observer.onNext(value);
            }

            public void onError(Throwable t) {
                t.printStackTrace();
            }

            public void onCompleted() {
                observer.onCompleted();
            }
        };
    }

    private Strings.StringMessage response(String text) {
        return Strings.StringMessage.newBuilder().setText(text).build();
    }
}
