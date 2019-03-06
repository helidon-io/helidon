package io.helidon.security.integration.grpc;


import io.grpc.stub.StreamObserver;
import io.helidon.grpc.server.CollectingObserver;
import io.helidon.grpc.server.GrpcService;
import io.helidon.security.integration.grpc.test.Strings;

import java.util.stream.Collectors;
import java.util.stream.Stream;


/**
 * @author Aleksandar Seovic  2019.02.11
 */
public class StringService
        implements GrpcService
    {
    @Override
    public void update(Methods methods)
        {
        methods
            .descriptor(Strings.getDescriptor())
            .unary("Upper", this::upper)
            .unary("Lower", this::lower)
            .serverStreaming("Split", this::split)
            .clientStreaming("Join", this::join)
            .bidirectional("Echo", this::echo);
        }

    // ---- service methods -------------------------------------------------

    private void upper(Strings.StringMessage request, StreamObserver<Strings.StringMessage> observer)
        {
        complete(observer, response(request.getText().toUpperCase()));
        }

    private void lower(Strings.StringMessage request, StreamObserver<Strings.StringMessage> observer)
        {
        complete(observer, response(request.getText().toLowerCase()));
        }

    private void split(Strings.StringMessage request, StreamObserver<Strings.StringMessage> observer)
        {
        String[] parts = request.getText().split(" ");
        stream(observer, Stream.of(parts).map(this::response));
        }

    private StreamObserver<Strings.StringMessage> join(StreamObserver<Strings.StringMessage> observer)
        {
        return new CollectingObserver<>(
                Collectors.joining(" "),
                observer,
                Strings.StringMessage::getText,
                this::response);
        }

    private StreamObserver<Strings.StringMessage> echo(StreamObserver<Strings.StringMessage> observer)
        {
        return new StreamObserver<Strings.StringMessage>()
            {
            public void onNext(Strings.StringMessage value)
                {
                observer.onNext(value);
                }

            public void onError(Throwable t)
                {
                t.printStackTrace();
                }

            public void onCompleted()
                {
                observer.onCompleted();
                }
            };
        }

    // ---- helper methods --------------------------------------------------

    private Strings.StringMessage response(String text)
        {
        return Strings.StringMessage.newBuilder().setText(text).build();
        }

    }
