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

package io.helidon.webclient.http3;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.StandardProtocolFamily;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Comparator;
import java.util.PriorityQueue;
import java.util.SplittableRandom;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Standalone, deterministic, bounded UDP impairment proxy used by HTTP/3 evidence runners.
 */
public final class UdpImpairmentProxyMain {
    private static final int MAX_DATAGRAM_BYTES = 65_535;
    private static final int MAX_RECEIVE_BATCH = 256;
    private static final int MAX_QUEUED_DATAGRAMS = 4096;
    private static final long MAX_QUEUED_BYTES = 64L * 1024 * 1024;
    static final long MAX_DURATION_MILLIS = 60_000;
    private static final long MAX_STALL_AFTER_DATAGRAMS = 10_000_000;
    private static final long RETRY_SEND_NANOS = TimeUnit.MICROSECONDS.toNanos(100);
    private static final long CLIENT_RANDOM_SALT = 0x43A9_89B1_01D4_F391L;
    private static final long SERVER_RANDOM_SALT = 0xB7E1_5162_8AED_2A6BL;

    private UdpImpairmentProxyMain() {
    }

    /**
     * Starts the proxy. Arguments are bind host, server host, server port, seed, one-way delay ms, jitter ms,
     * loss permille, reorder permille, reorder delay ms, stall-after datagrams, stall ms, and maximum queued datagrams.
     *
     * @param args proxy configuration
     * @throws Exception if setup or forwarding fails
     */
    public static void main(String[] args) throws Exception {
        Config config = Config.parse(args);
        StandardProtocolFamily family = config.bindAddress instanceof Inet6Address
                ? StandardProtocolFamily.INET6
                : StandardProtocolFamily.INET;
        AtomicBoolean running = new AtomicBoolean(true);
        try (DatagramChannel channel = DatagramChannel.open(family);
             Selector selector = Selector.open()) {
            channel.bind(new InetSocketAddress(config.bindAddress, 0));
            channel.configureBlocking(false);
            channel.register(selector, SelectionKey.OP_READ);
            ProxyLoop loop = new ProxyLoop(config, channel, selector);
            ConcurrentLinkedQueue<String> commands = new ConcurrentLinkedQueue<>();
            Thread commandThread = Thread.ofPlatform()
                    .name("udp-impairment-proxy-control")
                    .daemon()
                    .start(() -> {
                        try (var reader = new BufferedReader(new InputStreamReader(System.in))) {
                            while (running.get()) {
                                String command = reader.readLine();
                                commands.add(command == null ? "STOP" : command);
                                selector.wakeup();
                                if (command == null || "STOP".equals(command)) {
                                    return;
                                }
                            }
                        } catch (Exception e) {
                            commands.add("STOP");
                            selector.wakeup();
                        }
                    });

            InetSocketAddress localAddress = (InetSocketAddress) channel.getLocalAddress();
            System.out.println("READY " + localAddress.getPort());
            System.out.flush();

            try {
                loop.run(running, commands);
            } finally {
                running.set(false);
                selector.wakeup();
                commandThread.interrupt();
            }
            System.out.println(loop.metrics());
            System.out.flush();
        }
    }

    private record Config(InetAddress bindAddress,
                          InetSocketAddress serverAddress,
                          long seed,
                          long delayNanos,
                          long jitterNanos,
                          int lossPermille,
                          int reorderPermille,
                          long reorderDelayNanos,
                          long stallAfterDatagrams,
                          long stallNanos,
                          int maxQueuedDatagrams) {
        private static Config parse(String[] args) throws Exception {
            if (args.length != 12) {
                throw new IllegalArgumentException("Expected 12 UDP impairment proxy arguments, received " + args.length);
            }
            InetAddress bindAddress = InetAddress.getByName(args[0]);
            InetAddress serverAddress = InetAddress.getByName(args[1]);
            if ((bindAddress instanceof Inet6Address) != (serverAddress instanceof Inet6Address)) {
                throw new IllegalArgumentException("UDP impairment proxy bind and server addresses use different families");
            }
            int serverPort = Integer.parseInt(args[2]);
            long seed = Long.parseLong(args[3]);
            long delayMillis = Long.parseLong(args[4]);
            long jitterMillis = Long.parseLong(args[5]);
            int lossPermille = Integer.parseInt(args[6]);
            int reorderPermille = Integer.parseInt(args[7]);
            long reorderDelayMillis = Long.parseLong(args[8]);
            long stallAfterDatagrams = Long.parseLong(args[9]);
            long stallMillis = Long.parseLong(args[10]);
            int maxQueuedDatagrams = Integer.parseInt(args[11]);
            if (serverPort < 1 || serverPort > 65_535
                    || delayMillis < 0
                    || jitterMillis < 0
                    || reorderDelayMillis < 0
                    || stallAfterDatagrams < 0
                    || stallMillis < 0
                    || maxQueuedDatagrams < 1
                    || delayMillis > MAX_DURATION_MILLIS
                    || jitterMillis > MAX_DURATION_MILLIS
                    || reorderDelayMillis > MAX_DURATION_MILLIS
                    || stallMillis > MAX_DURATION_MILLIS
                    || stallAfterDatagrams > MAX_STALL_AFTER_DATAGRAMS
                    || maxQueuedDatagrams > MAX_QUEUED_DATAGRAMS) {
                throw new IllegalArgumentException("Invalid UDP impairment proxy bounds");
            }
            validatePermille(lossPermille, "loss");
            validatePermille(reorderPermille, "reorder");
            return new Config(bindAddress,
                              new InetSocketAddress(serverAddress, serverPort),
                              seed,
                              Math.multiplyExact(delayMillis, TimeUnit.MILLISECONDS.toNanos(1)),
                              Math.multiplyExact(jitterMillis, TimeUnit.MILLISECONDS.toNanos(1)),
                              lossPermille,
                              reorderPermille,
                              Math.multiplyExact(reorderDelayMillis, TimeUnit.MILLISECONDS.toNanos(1)),
                              stallAfterDatagrams,
                              Math.multiplyExact(stallMillis, TimeUnit.MILLISECONDS.toNanos(1)),
                              maxQueuedDatagrams);
        }

        private static void validatePermille(int value, String name) {
            if (value < 0 || value > 1000) {
                throw new IllegalArgumentException("UDP impairment " + name + " permille is out of range: " + value);
            }
        }
    }

    private static final class ProxyLoop {
        private final Config config;
        private final DatagramChannel channel;
        private final Selector selector;
        private final SplittableRandom clientRandom;
        private final SplittableRandom serverRandom;
        private final ByteBuffer receiveBuffer = ByteBuffer.allocateDirect(MAX_DATAGRAM_BYTES);
        private final PriorityQueue<PendingDatagram> pending = new PriorityQueue<>(
                Comparator.comparingLong(PendingDatagram::dueNanos)
                        .thenComparingLong(PendingDatagram::sequence));

        private InetSocketAddress clientAddress;
        private long sequence;
        private long clientArrival;
        private long serverArrival;
        private long lastClientForwarded;
        private long lastServerForwarded;
        private long receivedClient;
        private long receivedServer;
        private long forwardedClient;
        private long forwardedServer;
        private long droppedLoss;
        private long droppedOverflow;
        private long droppedNoClient;
        private long reorderDelayed;
        private long actuallyReordered;
        private long stalledDatagrams;
        private long queuedBytes;
        private long maxQueueBytes;
        private int maxQueueDepth;
        private boolean stallTriggered;
        private long stallTriggerReceived;
        private long stallTriggerClientReceived = Long.MAX_VALUE;
        private long configuredStallNanos;
        private long stallUntilNanos;

        private ProxyLoop(Config config, DatagramChannel channel, Selector selector) {
            this.config = config;
            this.channel = channel;
            this.selector = selector;
            this.clientRandom = new SplittableRandom(config.seed ^ CLIENT_RANDOM_SALT);
            this.serverRandom = new SplittableRandom(config.seed ^ SERVER_RANDOM_SALT);
            this.stallTriggerReceived = config.stallAfterDatagrams == 0
                    ? Long.MAX_VALUE
                    : config.stallAfterDatagrams;
            this.configuredStallNanos = config.stallNanos;
        }

        private void run(AtomicBoolean running, ConcurrentLinkedQueue<String> commands) throws Exception {
            while (running.get()) {
                forwardDueDatagrams();
                processCommands(running, commands);
                if (!running.get()) {
                    break;
                }
                long waitMillis = selectWaitMillis();
                if (waitMillis == 0) {
                    selector.selectNow();
                } else {
                    selector.select(waitMillis);
                }
                boolean readable = selector.selectedKeys().contains(channel.keyFor(selector));
                selector.selectedKeys().clear();
                if (readable) {
                    receiveDatagrams(running);
                }
            }
            forwardDueDatagrams();
        }

        private void processCommands(AtomicBoolean running, ConcurrentLinkedQueue<String> commands) {
            String command;
            while ((command = commands.poll()) != null) {
                if ("STOP".equals(command)) {
                    running.set(false);
                    return;
                }
                if ("SNAPSHOT".equals(command)) {
                    System.out.println(metrics());
                    System.out.flush();
                    continue;
                }
                String[] fields = command.split(" ");
                if (fields.length == 3 && "ARM_CLIENT_STALL".equals(fields[0])) {
                    long afterDatagrams = Long.parseLong(fields[1]);
                    long stallMillis = Long.parseLong(fields[2]);
                    if (afterDatagrams < 0
                            || afterDatagrams > MAX_STALL_AFTER_DATAGRAMS
                            || stallMillis < 1
                            || stallMillis > MAX_DURATION_MILLIS
                            || stallTriggered
                            || stallTriggerReceived != Long.MAX_VALUE
                            || stallTriggerClientReceived != Long.MAX_VALUE) {
                        throw new IllegalArgumentException("Invalid dynamic UDP impairment stall");
                    }
                    stallTriggerClientReceived = Math.addExact(receivedClient, afterDatagrams);
                    configuredStallNanos = Math.multiplyExact(stallMillis, TimeUnit.MILLISECONDS.toNanos(1));
                    System.out.println("ARMED clientReceived=" + receivedClient
                                               + " serverReceived=" + receivedServer);
                    System.out.flush();
                    continue;
                }
                throw new IllegalArgumentException("Unknown UDP impairment proxy command: " + command);
            }
        }

        private void receiveDatagrams(AtomicBoolean running) throws Exception {
            for (int received = 0; received < MAX_RECEIVE_BATCH && running.get(); received++) {
                receiveBuffer.clear();
                SocketAddress source = channel.receive(receiveBuffer);
                if (source == null) {
                    return;
                }
                receiveBuffer.flip();
                byte[] datagram = new byte[receiveBuffer.remaining()];
                receiveBuffer.get(datagram);
                InetSocketAddress sourceAddress = (InetSocketAddress) source;
                boolean fromServer = sameEndpoint(sourceAddress, config.serverAddress);
                InetSocketAddress target;
                long arrival;
                if (fromServer) {
                    receivedServer++;
                    arrival = ++serverArrival;
                    target = clientAddress;
                    if (target == null) {
                        droppedNoClient++;
                        continue;
                    }
                } else {
                    receivedClient++;
                    arrival = ++clientArrival;
                    clientAddress = sourceAddress;
                    target = config.serverAddress;
                }
                schedule(datagram, target, fromServer, arrival);
            }
        }

        private void schedule(byte[] datagram,
                              InetSocketAddress target,
                              boolean fromServer,
                              long arrival) {
            long received = receivedClient + receivedServer;
            long now = System.nanoTime();
            if (!stallTriggered
                    && (received > stallTriggerReceived
                    || (!fromServer && receivedClient > stallTriggerClientReceived))) {
                stallTriggered = true;
                stallUntilNanos = Math.addExact(now, configuredStallNanos);
                System.out.println("STALL direction=" + (fromServer ? "server" : "client")
                                           + " clientReceived=" + receivedClient
                                           + " serverReceived=" + receivedServer
                                           + " received=" + received);
                System.out.flush();
                for (PendingDatagram pendingDatagram : pending) {
                    if (pendingDatagram.dueNanos < stallUntilNanos) {
                        stalledDatagrams++;
                    }
                }
            }
            SplittableRandom random = fromServer ? serverRandom : clientRandom;
            if (config.lossPermille > 0 && random.nextInt(1000) < config.lossPermille) {
                droppedLoss++;
                return;
            }
            long jitter = config.jitterNanos == 0
                    ? 0
                    : random.nextLong(-config.jitterNanos, config.jitterNanos + 1);
            long delay = Math.max(0, Math.addExact(config.delayNanos, jitter));
            boolean reorderSelected = config.reorderPermille > 0
                    && random.nextInt(1000) < config.reorderPermille;
            if (reorderSelected) {
                delay = Math.addExact(delay, config.reorderDelayNanos);
            }
            long due = Math.addExact(now, delay);
            boolean stalled = false;
            if (now < stallUntilNanos) {
                if (due < stallUntilNanos) {
                    due = stallUntilNanos;
                    stalled = true;
                }
            }
            if (pending.size() >= config.maxQueuedDatagrams
                    || queuedBytes > MAX_QUEUED_BYTES - datagram.length) {
                droppedOverflow++;
                return;
            }
            pending.add(new PendingDatagram(datagram, target, due, sequence++, fromServer, arrival));
            queuedBytes += datagram.length;
            maxQueueBytes = Math.max(maxQueueBytes, queuedBytes);
            maxQueueDepth = Math.max(maxQueueDepth, pending.size());
            if (reorderSelected) {
                reorderDelayed++;
            }
            if (stalled) {
                stalledDatagrams++;
            }
        }

        private void forwardDueDatagrams() throws Exception {
            long now = System.nanoTime();
            if (now < stallUntilNanos) {
                return;
            }
            while (!pending.isEmpty() && pending.peek().dueNanos <= now) {
                PendingDatagram datagram = pending.remove();
                int sent = channel.send(ByteBuffer.wrap(datagram.bytes), datagram.target);
                if (sent == 0) {
                    pending.add(new PendingDatagram(datagram.bytes,
                                                    datagram.target,
                                                    Math.addExact(now, RETRY_SEND_NANOS),
                                                    datagram.sequence,
                                                    datagram.fromServer,
                                                    datagram.arrival));
                    return;
                }
                if (sent != datagram.bytes.length) {
                    throw new IllegalStateException("UDP impairment proxy sent a partial datagram");
                }
                queuedBytes -= datagram.bytes.length;
                if (datagram.fromServer) {
                    forwardedServer++;
                    if (datagram.arrival < lastServerForwarded) {
                        actuallyReordered++;
                    } else {
                        lastServerForwarded = datagram.arrival;
                    }
                } else {
                    forwardedClient++;
                    if (datagram.arrival < lastClientForwarded) {
                        actuallyReordered++;
                    } else {
                        lastClientForwarded = datagram.arrival;
                    }
                }
                now = System.nanoTime();
            }
        }

        private long selectWaitMillis() {
            if (pending.isEmpty()) {
                return 1000;
            }
            long now = System.nanoTime();
            long nextDeadline = Math.max(pending.peek().dueNanos, stallUntilNanos);
            long remaining = nextDeadline - now;
            if (remaining <= 0) {
                return 0;
            }
            return Math.min(1000, Math.max(1, TimeUnit.NANOSECONDS.toMillis(remaining) + 1));
        }

        private String metrics() {
            return "METRICS"
                    + " seed=" + config.seed
                    + " receivedClient=" + receivedClient
                    + " receivedServer=" + receivedServer
                    + " forwardedClient=" + forwardedClient
                    + " forwardedServer=" + forwardedServer
                    + " droppedLoss=" + droppedLoss
                    + " droppedOverflow=" + droppedOverflow
                    + " droppedNoClient=" + droppedNoClient
                    + " reorderDelayed=" + reorderDelayed
                    + " actuallyReordered=" + actuallyReordered
                    + " stallTriggered=" + (stallTriggered ? 1 : 0)
                    + " stalledDatagrams=" + stalledDatagrams
                    + " maxQueueDepth=" + maxQueueDepth
                    + " maxQueueBytes=" + maxQueueBytes
                    + " queuedDatagrams=" + pending.size()
                    + " queuedBytes=" + queuedBytes;
        }

        private static boolean sameEndpoint(InetSocketAddress first, InetSocketAddress second) {
            return first.getPort() == second.getPort() && first.getAddress().equals(second.getAddress());
        }
    }

    private record PendingDatagram(byte[] bytes,
                                   InetSocketAddress target,
                                   long dueNanos,
                                   long sequence,
                                   boolean fromServer,
                                   long arrival) {
    }
}
