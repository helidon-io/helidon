/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.webserver.benchmark.jmh;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.zip.Deflater;

import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;

/**
 * Explicit, probabilistic qualification probe, never selected by Surefire.
 * Requires JDK 28 build 19 or newer (JDK-8386622); qualify the actual runtime rather than assuming sampling support.
 */
public final class HttpRequestJfrSamplesProbe {
    private static final List<String> SAMPLE_TYPES = List.of("jdk.ExecutionSample", "jdk.NativeMethodSample");
    private static volatile long result;

    private HttpRequestJfrSamplesProbe() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("Supply an existing output directory for recordings and jfr print output");
        }
        Path directory = Path.of(args[0]);
        if (!Files.isDirectory(directory)) {
            throw new IllegalArgumentException("Output directory does not exist: " + directory);
        }
        System.out.println("Sample qualification runtime: " + Runtime.version());
        for (String protocol : List.of("HTTP1", "HTTP2")) {
            qualify(protocol, directory);
        }
    }

    private static void qualify(String protocol, Path directory) throws Exception {
        var state = new HttpRequestJfrJmhBenchmark();
        state.protocol = protocol;
        state.recordingMode = "ENABLED";
        Path recording = directory.resolve(protocol + "-samples.jfr");
        Path printed = directory.resolve(protocol + "-samples.txt");
        try {
            state.setup(HttpRequestJfrSamplesProbe::work);
            SAMPLE_TYPES.forEach(name -> state.recording().enable(name).withPeriod(Duration.ofMillis(1)));
            // Fixed bounded workload; absence of either sample type is an explicit qualification failure.
            for (int i = 0; i < 24; i++) {
                state.keepAliveExchange();
            }
            state.dump(recording);
        } finally {
            state.tearDown();
        }
        var events = RecordingFile.readAllEvents(recording);
        var requests = events.stream()
                .filter(event -> event.getEventType().getName().equals(HttpRequestJfrJmhBenchmark.REQUEST_EVENT))
                .filter(event -> "/benchmark".equals(event.getString("routeTemplate")))
                .toList();
        require(!requests.isEmpty(), "No recorded request intervals for " + protocol);
        for (String field : List.of("serverSocketId", "socketId", "requestId", "requestName")) {
            require(requests.getFirst().getEventType().getField(field).getAnnotationElements().stream()
                            .anyMatch(annotation -> annotation.getTypeName().equals("jdk.jfr.Contextual")),
                    "Missing @Contextual metadata: " + field);
        }
        for (String type : SAMPLE_TYPES) {
            var matches = events.stream().filter(event -> type.equals(event.getEventType().getName()))
                    .filter(sample -> requests.stream().anyMatch(request -> overlaps(request, sample)))
                    .toList();
            require(!matches.isEmpty(),
                    "Host/JDK failed to capture same-thread overlapping " + type + " for " + protocol);
            System.out.println(protocol + " " + type + " overlapping samples=" + matches.size());
        }
        print(recording, printed);
        String output = Files.readString(printed);
        for (String type : SAMPLE_TYPES) {
            var blocks = Pattern.compile("(?ms)^" + Pattern.quote(type) + " \\{\\R(.*?)^\\}").matcher(output);
            boolean correlated = false;
            while (blocks.find()) {
                String block = blocks.group(1);
                for (var request : requests) {
                    if (block.contains("Context: HttpServerRequest.requestName = \"GET /benchmark\"")
                            && block.contains("Context: HttpServerRequest.socketId = \""
                                                      + request.getString("socketId") + "\"")
                            && block.contains("Context: HttpServerRequest.serverSocketId = \""
                                                      + request.getString("serverSocketId") + "\"")
                            && Pattern.compile("(?m)^  Context: HttpServerRequest.requestId = "
                                                       + request.getInt("requestId") + "$").matcher(block).find()) {
                        correlated = true;
                        break;
                    }
                }
            }
            require(correlated, "jfr print did not contextualize " + type + " for " + protocol);
        }
        System.out.println(protocol + " contextual correlation qualified for both sample types: " + recording);
    }

    private static boolean overlaps(RecordedEvent request, RecordedEvent sample) {
        // EA21 PrettyWriter uses getThread(), not a manual sampledThread substitution, for contextual association.
        return request.getThread() != null && sample.getThread() != null
                && request.getThread().getId() == sample.getThread().getId()
                && !sample.getEndTime().isBefore(request.getStartTime())
                && !sample.getEndTime().isAfter(request.getEndTime());
    }

    private static void print(Path recording, Path output) throws IOException, InterruptedException {
        Path tool = Path.of(System.getProperty("java.home"), "bin", "jfr");
        Process process = new ProcessBuilder(tool.toString(), "print", "--events",
                                             String.join(",", SAMPLE_TYPES), recording.toString())
                .redirectErrorStream(true).redirectOutput(output.toFile()).start();
        try {
            require(process.waitFor(30, TimeUnit.SECONDS), "jfr print timed out");
            require(process.exitValue() == 0, "jfr print failed; inspect " + output);
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
                process.waitFor(10, TimeUnit.SECONDS);
            }
        }
    }

    private static void work() {
        long deadline = System.nanoTime() + Duration.ofMillis(100).toNanos();
        long value = result;
        while (System.nanoTime() < deadline) {
            for (int i = 0; i < 100_000; i++) {
                value = Long.rotateLeft(value * 6364136223846793005L + i, 17);
            }
        }
        result = value;
        // Incompressible input and BEST_COMPRESSION keep actual native deflate work on the request thread.
        byte[] input = new byte[4 * 1024 * 1024];
        for (int i = 0; i < input.length; i++) {
            value ^= value << 13;
            value ^= value >>> 7;
            value ^= value << 17;
            input[i] = (byte) value;
        }
        try (var deflater = new Deflater(Deflater.BEST_COMPRESSION)) {
            deflater.setInput(input);
            deflater.finish();
            byte[] output = new byte[64 * 1024];
            while (!deflater.finished()) {
                result += deflater.deflate(output);
            }
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
