/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.nima.tests.integration.udp.server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static io.helidon.nima.tests.integration.udp.server.UdpServer.ACK_FREQUENCY;

/**
 * UDP load generator for performance testing.
 */
public class UdpGenerator implements Runnable {

    private int threads = 4;
    private int packetSize = 4 * 1024;
    private Duration run = Duration.ofMinutes(1);
    private Duration warmup = Duration.ofSeconds(20);

    private volatile long startTime;
    private volatile long endTime;
    private final ExecutorService executor;
    private final InetSocketAddress address;

    private long bytesSent = 0L;
    private long messagesSent = 0L;

    /**
     * CLI main method.
     *
     * @param args argument list
     */
    public static void main(String[] args) {
        new UdpGenerator(args).run();
    }

    public UdpGenerator(String[] args) {
        int port = 8888;
        String host = "localhost";
        try {
            for (int i = 0; i < args.length; i++) {
                switch (args[i]) {
                    case "--host" -> host = args[++i];
                    case "--port" -> port = Integer.parseInt(args[++i]);
                    case "--threads" -> threads = Integer.parseInt(args[++i]);
                    case "--warmup" -> warmup = Duration.parse(args[++i]);
                    case "--run" -> run = Duration.parse(args[++i]);
                    case "--packet-size" -> {
                        String value = args[++i].trim().toLowerCase();
                        int multiplier = 1;
                        if (value.endsWith("k")) {
                            multiplier = 1024;
                            value = value.substring(0, value.length() - 1);
                        } else if (value.endsWith("m")) {
                            multiplier = 1024 * 1024;
                            value = value.substring(0, value.length() - 1);
                        }
                        packetSize = Integer.parseInt(value) * multiplier;
                    }
                    default -> usage();
                }
            }
        } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
            usage();
        }
        this.address = new InetSocketAddress(host, port);
        this.executor = Executors.newThreadPerTaskExecutor(Thread.ofVirtual()
                .allowSetThreadLocals(false)
                .inheritInheritableThreadLocals(false)
                .factory());
        display();
    }

    private void usage() {
        System.err.println("udpgen [--packet-size size (default: 4k)] " +
                "[--host host (default: localhost)] " +
                "[--port port (default: 8888)] " +
                "[--threads threads (default: 4)] " +
                "[--warmup duration (default: PT30S)] " +
                "[--run duration (default: PT2M)]");
        System.exit(1);
    }

    private void display() {
        System.out.println("UDP load generator");
        System.out.println("  udp://" + address.getAddress().getHostAddress() + ":" + address.getPort());
        System.out.println("  warmup: " + warmup);
        System.out.println("  run: " + run);
        System.out.println("  threads: " + threads);
        System.out.println("  packet-size: " + (packetSize / 1024) + "k");
    }

    /**
     * Runs the benchmark with all its phases.
     */
    public void run() {
        try {
            System.out.print("Starting warmup phase ... ");
            runPhase(warmup);
            System.out.println("DONE");

            System.out.print("Starting run phase ... ");
            runPhase(run);
            System.out.println("DONE\n");

            double actualDuration = (endTime - startTime) / 1_000_000_000.0;
            System.out.printf("TPS %.2f%n", messagesSent / actualDuration);
            System.out.printf("Mbps %.2f%n", bytesSent / 1024.0 / 1024.0 / actualDuration * 8);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Runs an individual phase such as warmup.
     */
    private void runPhase(Duration duration) throws InterruptedException, ExecutionException, TimeoutException {
        startTime = System.nanoTime();
        endTime = startTime + duration.toNanos();

        UdpThreadClient[] clients = new UdpThreadClient[threads];
        Future<?>[] futures = new Future[threads];
        for (int i = 0; i < threads; i++) {
            clients[i] = new UdpThreadClient();
            futures[i] = executor.submit(clients[i]);
        }
        long await = (long) (duration.toNanos() * 1.2d);
        for (int i = 0; i < threads; i++) {
            futures[i].get(await, TimeUnit.NANOSECONDS);
        }
        for (int i = 0; i < threads; i++) {
            bytesSent += clients[i].bytesSent;
            messagesSent += clients[i].messagesSent;
        }
    }

    class UdpThreadClient implements Runnable {

        private long n = 0;
        private final ByteBuffer ack = ByteBuffer.allocate("ack".length());
        private final ByteBuffer data;

        long bytesSent = 0L;
        long messagesSent = 0L;

        UdpThreadClient() {
            data = ByteBuffer.allocate(packetSize);
            for (int i = 0; i < data.capacity(); i++) {
                data.put((byte) 0xA);
            }
            data.flip();
        }

        @Override
        public void run() {
            try (DatagramChannel channel = DatagramChannel.open()) {
                channel.connect(address);
                while (System.nanoTime() < endTime) {
                    // Send data over
                    data.mark();
                    int sent = channel.send(data, address);
                    if (sent != packetSize) {
                        System.out.println("Sent fewer bytes than packet size " + sent);
                    }
                    data.reset();

                    // Receive ACK as a form of primitive flow control
                    if (n++ % ACK_FREQUENCY == 0) {
                        ack.clear();
                        channel.receive(ack);
                    }

                    // Update stats
                    bytesSent += sent;
                    messagesSent++;
                }
                channel.disconnect();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
