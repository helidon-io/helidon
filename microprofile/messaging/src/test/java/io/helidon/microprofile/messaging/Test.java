package io.helidon.microprofile.messaging;

import org.eclipse.microprofile.reactive.streams.operators.ReactiveStreams;

public class Test {
    public static void main(String[] args) {
        // Create a stream of words
        ReactiveStreams.of("hello", "from", "smallrye", "reactive", "stream", "operators")
                .map(String::toUpperCase) // Transform the words
                .filter(s -> s.length() > 4) // Filter items
                .forEach(word -> System.out.println(">> " + word)) // Terminal operation
                .run(); // Run it (create the streams, subscribe to it...)
    }
}
