package io.helidon.grpc.server;


import io.grpc.stub.StreamObserver;
import io.helidon.config.Config;
import io.helidon.grpc.server.test.Greet;
import io.helidon.grpc.server.test.Greet.GreetRequest;
import io.helidon.grpc.server.test.Greet.GreetResponse;
import io.helidon.grpc.server.test.Greet.SetGreetingRequest;
import io.helidon.grpc.server.test.Greet.SetGreetingResponse;
import io.helidon.grpc.server.test.Strings;
import io.helidon.grpc.server.test.Strings.StringMessage;
import java.util.Optional;
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

    private void upper(StringMessage request, StreamObserver<StringMessage> observer)
        {
        complete(observer, response(request.getText().toUpperCase()));
        }

    private void lower(StringMessage request, StreamObserver<StringMessage> observer)
        {
        complete(observer, response(request.getText().toLowerCase()));
        }

    private void split(StringMessage request, StreamObserver<StringMessage> observer)
        {
        String[] parts = request.getText().split(" ");
        stream(observer, Stream.of(parts).map(this::response));
        }

    private StreamObserver<StringMessage> join(StreamObserver<StringMessage> observer)
        {
        return new CollectingObserver<>(
                Collectors.joining(" "),
                observer,
                StringMessage::getText,
                this::response);
        }

    private StreamObserver<StringMessage> echo(StreamObserver<StringMessage> observer)
        {
        return new StreamObserver<StringMessage>()
            {
            public void onNext(StringMessage value)
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

    private StringMessage response(String text)
        {
        return StringMessage.newBuilder().setText(text).build();
        }

    }
