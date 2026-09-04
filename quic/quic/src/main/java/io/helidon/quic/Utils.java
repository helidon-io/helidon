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

package io.helidon.quic;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketOption;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.NetworkChannel;
import java.nio.channels.SelectionKey;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

import javax.net.ssl.SSLParameters;

import io.helidon.common.Api;

/**
 * Internal utility methods shared by the QUIC runtime.
 */
@Api.Internal
public final class Utils {
    /**
     * Threshold used to decide whether to slice a buffer or copy it.
     */
    public static final int SLICE_THRESHOLD = 32;
    private Utils() {
    }

    /**
     * Describes the relation between the current time and a deadline.
     *
     * @param now      current deadline snapshot
     * @param deadline deadline to describe
     * @return human-readable deadline description
     */
    public static String debugDeadline(Deadline now, Deadline deadline) {
        boolean isDue = deadline.compareTo(now) <= 0;
        try {
            if (isDue) {
                if (deadline.equals(Deadline.MIN)) {
                    return "due (Deadline.MIN)";
                }
                return "due since " + deadline.until(now, ChronoUnit.MILLIS) + "ms";
            }
            if (deadline.equals(Deadline.MAX)) {
                return "not scheduled (Deadline.MAX)";
            }
            return "due in " + now.until(deadline, ChronoUnit.MILLIS) + "ms";
        } catch (ArithmeticException e) {
            return isDue ? "due since too long" : "due in the far future";
        }
    }

    /**
     * Returns the number of milliseconds until the deadline.
     *
     * @param now      current deadline snapshot
     * @param deadline deadline to measure
     * @return human-readable duration
     */
    public static String millis(Deadline now, Deadline deadline) {
        if (Deadline.MAX.equals(deadline)) {
            return "not scheduled";
        }
        try {
            return now.until(deadline, ChronoUnit.MILLIS) + " ms";
        } catch (ArithmeticException e) {
            return "too far away";
        }
    }

    /**
     * Returns a slice or copy of the requested range using the default threshold.
     *
     * @param src   source buffer
     * @param start slice start index
     * @param len   slice length
     * @return sliced or copied buffer
     */
    public static ByteBuffer sliceOrCopy(ByteBuffer src, int start, int len) {
        return sliceOrCopy(src, start, len, SLICE_THRESHOLD);
    }

    /**
     * Returns a slice or copy of the requested range using the supplied threshold.
     *
     * @param src       source buffer
     * @param start     slice start index
     * @param len       slice length
     * @param threshold copy threshold
     * @return sliced or copied buffer
     */
    public static ByteBuffer sliceOrCopy(ByteBuffer src, int start, int len, int threshold) {
        ByteBuffer duplicate = src.duplicate();
        duplicate.position(start);
        duplicate.limit(start + len);
        ByteBuffer slice = duplicate.slice();
        if (src.capacity() - len < threshold) {
            return slice;
        }
        ByteBuffer copy = ByteBuffer.allocate(len);
        copy.put(slice);
        copy.flip();
        return copy;
    }

    /**
     * Describes the interest operations for a selection key.
     *
     * @param key selection key
     * @return human-readable operations description
     */
    public static String interestOps(SelectionKey key) {
        Objects.requireNonNull(key, "key");
        try {
            return describeOps(key.interestOps());
        } catch (CancelledKeyException e) {
            return "cancelled-key";
        }
    }

    /**
     * Describes the ready operations for a selection key.
     *
     * @param key selection key
     * @return human-readable operations description
     */
    public static String readyOps(SelectionKey key) {
        Objects.requireNonNull(key, "key");
        try {
            return describeOps(key.readyOps());
        } catch (CancelledKeyException e) {
            return "cancelled-key";
        }
    }

    /**
     * Describes the supplied selection-key operation flags.
     *
     * @param ops selection-key operations bitmask
     * @return human-readable operations description
     */
    public static String describeOps(int ops) {
        if (ops == 0) {
            return "None";
        }
        StringBuilder builder = new StringBuilder();
        appendOp(builder, ops, SelectionKey.OP_READ, "READ");
        appendOp(builder, ops, SelectionKey.OP_WRITE, "WRITE");
        appendOp(builder, ops, SelectionKey.OP_CONNECT, "CONNECT");
        appendOp(builder, ops, SelectionKey.OP_ACCEPT, "ACCEPT");
        return builder.toString();
    }

    /**
     * Returns the remaining contents of a buffer as hexadecimal text.
     *
     * @param buffer buffer to encode
     * @return hexadecimal representation
     */
    public static String asHexString(ByteBuffer buffer) {
        if (!buffer.hasRemaining()) {
            return "";
        }
        ByteBuffer duplicate = buffer.duplicate();
        byte[] bytes = new byte[duplicate.remaining()];
        duplicate.get(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    /**
     * Applies configured socket buffer sizes to a channel.
     *
     * @param channel        channel to configure
     * @param receiveBufSize requested receive buffer size
     * @param sendBufSize    requested send buffer size
     * @param <T>            channel type
     * @return configured channel
     */
    public static <T extends NetworkChannel> T configureChannelBuffers(T channel,
                                                                       int receiveBufSize,
                                                                       int sendBufSize) {
        return configureChannelBuffers(Optional.empty(), channel, receiveBufSize, sendBufSize);
    }

    /**
     * Applies configured socket buffer sizes to a channel and reports the outcome to a log sink.
     *
     * @param logSink        log sink
     * @param channel        channel to configure
     * @param receiveBufSize requested receive buffer size
     * @param sendBufSize    requested send buffer size
     * @param <T>            channel type
     * @return configured channel
     */
    public static <T extends NetworkChannel> T configureChannelBuffers(Consumer<String> logSink,
                                                                       T channel,
                                                                       int receiveBufSize,
                                                                       int sendBufSize) {
        return configureChannelBuffers(Optional.of(Objects.requireNonNull(logSink, "logSink")),
                                       channel,
                                       receiveBufSize,
                                       sendBufSize);
    }

    private static <T extends NetworkChannel> T configureChannelBuffers(Optional<Consumer<String>> logSink,
                                                                        T channel,
                                                                        int receiveBufSize,
                                                                        int sendBufSize) {
        Objects.requireNonNull(channel, "channel");
        if (receiveBufSize > 0) {
            trySetOption(logSink, channel, StandardSocketOptions.SO_RCVBUF, receiveBufSize, "receive");
        }
        if (sendBufSize > 0) {
            trySetOption(logSink, channel, StandardSocketOptions.SO_SNDBUF, sendBufSize, "send");
        }
        return channel;
    }

    /**
     * Creates a defensive copy of SSL parameters needed by QUIC channels.
     *
     * @param parameters parameters to copy
     * @return copied parameters
     */
    public static SSLParameters copySSLParameters(SSLParameters parameters) {
        SSLParameters copy = new SSLParameters();
        copy.setAlgorithmConstraints(parameters.getAlgorithmConstraints());
        copy.setCipherSuites(parameters.getCipherSuites());
        copy.setEnableRetransmissions(parameters.getEnableRetransmissions());
        copy.setEndpointIdentificationAlgorithm(parameters.getEndpointIdentificationAlgorithm());
        copy.setMaximumPacketSize(parameters.getMaximumPacketSize());
        if (parameters.getNeedClientAuth()) {
            copy.setNeedClientAuth(true);
        }
        if (parameters.getWantClientAuth()) {
            copy.setWantClientAuth(true);
        }
        if (parameters.getProtocols() != null) {
            copy.setProtocols(parameters.getProtocols().clone());
        }
        copy.setSNIMatchers(parameters.getSNIMatchers());
        copy.setServerNames(parameters.getServerNames());
        copy.setUseCipherSuitesOrder(parameters.getUseCipherSuitesOrder());
        copy.setApplicationProtocols(parameters.getApplicationProtocols());
        copy.setSignatureSchemes(parameters.getSignatureSchemes());
        copy.setNamedGroups(parameters.getNamedGroups());
        return copy;
    }

    /**
     * Returns a canonical text representation of a throwable without using {@code toString()}.
     *
     * @param throwable throwable to describe
     * @return throwable text
     */
    public static String throwableText(Throwable throwable) {
        Objects.requireNonNull(throwable, "throwable");
        String type = throwable.getClass().getSimpleName();
        if (type.isEmpty()) {
            type = throwable.getClass().getName();
        }
        String message = throwable.getMessage();
        if (message == null || message.isBlank()) {
            return type;
        }
        return type + ": " + message;
    }

    /**
     * Returns a canonical text representation of a socket address without using {@code toString()}.
     *
     * @param address address to describe
     * @return socket-address text
     */
    public static String socketAddressText(SocketAddress address) {
        Objects.requireNonNull(address, "address");
        if (address instanceof InetSocketAddress inetSocketAddress) {
            InetAddress inetAddress = inetSocketAddress.getAddress();
            String host = inetAddress == null ? inetSocketAddress.getHostString() : inetAddress.getHostAddress();
            if (host.indexOf(':') >= 0 && !host.startsWith("[")) {
                host = "[" + host + "]";
            }
            return host + ":" + inetSocketAddress.getPort();
        }
        return address.getClass().getName();
    }

    /**
     * Detects whether two socket addresses would conflict for loopback or wildcard binding.
     *
     * @param local local socket address
     * @param peer  peer socket address
     * @return conflict description when a conflict was detected
     */
    public static Optional<String> addressConflict(SocketAddress local, SocketAddress peer) {
        Objects.requireNonNull(local, "local");
        Objects.requireNonNull(peer, "peer");
        if (local.equals(peer)) {
            return Optional.of("local endpoint and remote endpoint are bound to the same IP address and port");
        }
        if (!(local instanceof InetSocketAddress localAddress) || !(peer instanceof InetSocketAddress peerAddress)) {
            return Optional.empty();
        }
        var localInet = localAddress.getAddress();
        var peerInet = peerAddress.getAddress();
        if (localInet != null && peerInet != null
                && !localInet.isAnyLocalAddress()
                && !peerInet.isAnyLocalAddress()
                && localInet.getClass() != peerInet.getClass()) {
            if ((localInet instanceof Inet6Address local6 && !local6.isIPv4CompatibleAddress())
                    || (peerInet instanceof Inet6Address peer6 && !peer6.isIPv4CompatibleAddress())) {
                return Optional.of("local endpoint IP (%s) and remote endpoint IP (%s) don't match"
                                           .formatted(localInet.getClass().getSimpleName(),
                                                      peerInet.getClass().getSimpleName()));
            }
        }
        if (localAddress.getPort() != peerAddress.getPort() || localInet == null || peerInet == null) {
            return Optional.empty();
        }
        if (localInet.isAnyLocalAddress() && peerInet.isLoopbackAddress()) {
            return Optional.of("local endpoint (wildcard) and remote endpoint (loopback) ports conflict");
        }
        if (peerInet.isAnyLocalAddress() && localInet.isLoopbackAddress()) {
            return Optional.of("local endpoint (loopback) and remote endpoint (wildcard) ports conflict");
        }
        return Optional.empty();
    }

    private static void appendOp(StringBuilder builder, int ops, int mask, String label) {
        if ((ops & mask) == 0) {
            return;
        }
        if (!builder.isEmpty()) {
            builder.append('|');
        }
        builder.append(label);
    }

    private static <T> void trySetOption(Optional<Consumer<String>> logSink,
                                         NetworkChannel channel,
                                         SocketOption<T> option,
                                         T value,
                                         String label) {
        try {
            channel.setOption(option, value);
            if (logSink.isPresent()) {
                logSink.get().accept("Configured " + label + " buffer size: " + value);
            }
        } catch (Exception e) {
            if (logSink.isPresent()) {
                logSink.get().accept("Failed to configure " + label + " buffer size: " + e);
            }
        }
    }

}
