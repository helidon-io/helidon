package io.helidon.grpc.server;


import io.grpc.Channel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;

import io.helidon.grpc.server.test.StringServiceGrpc;
import io.helidon.grpc.server.test.Strings.StringMessage;


/**
 * @author Aleksandar Seovic  2019.02.12
 */
public class StringClient
    {
    public static void main(String[] args) throws Exception
        {
        Channel channel = ManagedChannelBuilder.forAddress("localhost", 8080).usePlaintext().build();

        StringServiceGrpc.StringServiceStub stub = StringServiceGrpc.newStub(channel);
        stub.lower(stringMessage("Convert To Lowercase"), new PrintObserver<>());
        Thread.sleep(500L);
        stub.upper(stringMessage("Convert to Uppercase"), new PrintObserver<>());
        Thread.sleep(500L);
        stub.split(stringMessage("Let's split some text"), new PrintObserver<>());
        Thread.sleep(500L);

        StreamObserver<StringMessage> sender = stub.join(new PrintObserver<>());
        sender.onNext(stringMessage("Let's"));
        sender.onNext(stringMessage("join"));
        sender.onNext(stringMessage("some"));
        sender.onNext(stringMessage("text"));
        sender.onCompleted();
        Thread.sleep(500L);

        sender = stub.echo(new PrintObserver<>());
        sender.onNext(stringMessage("Let's"));
        sender.onNext(stringMessage("echo"));
        sender.onNext(stringMessage("some"));
        sender.onNext(stringMessage("text"));
        sender.onCompleted();
        Thread.sleep(500L);
        }

    private static StringMessage stringMessage(String text)
        {
        return StringMessage.newBuilder().setText(text).build();
        }

    static class PrintObserver<T> implements StreamObserver<T>
        {
        public void onNext(T value)
            {
            System.out.println(value);
            }

        public void onError(Throwable t)
            {
            t.printStackTrace();
            }

        public void onCompleted()
            {
            System.out.println("<completed>");
            }
        }
    }
