/*
 * Copyright (c) 2020, 2026 Oracle and/or its affiliates.
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

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.VarHandle;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.NetworkChannel;
import java.nio.channels.UnresolvedAddressException;
import java.security.Principal;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Queue;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.LongFunction;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;

import io.helidon.common.Api;
import io.helidon.common.buffers.BufferData;
import io.helidon.common.socket.PeerInfo;
import io.helidon.quic.OrderedFlow.CryptoDataFlow;
import io.helidon.quic.QuicEndpoint.QuicDatagram;
import io.helidon.quic.QuicTLSEngine.HandshakeState;
import io.helidon.quic.QuicTLSEngine.KeySpace;
import io.helidon.quic.QuicTransportParameters.ParameterId;
import io.helidon.quic.QuicTransportParameters.VersionInformation;
import io.helidon.quic.frame.AckFrame;
import io.helidon.quic.frame.ConnectionCloseFrame;
import io.helidon.quic.frame.CryptoFrame;
import io.helidon.quic.frame.DataBlockedFrame;
import io.helidon.quic.frame.HandshakeDoneFrame;
import io.helidon.quic.frame.MaxDataFrame;
import io.helidon.quic.frame.MaxStreamDataFrame;
import io.helidon.quic.frame.MaxStreamsFrame;
import io.helidon.quic.frame.NewConnectionIDFrame;
import io.helidon.quic.frame.NewTokenFrame;
import io.helidon.quic.frame.PaddingFrame;
import io.helidon.quic.frame.PathChallengeFrame;
import io.helidon.quic.frame.PathResponseFrame;
import io.helidon.quic.frame.PingFrame;
import io.helidon.quic.frame.QuicFrame;
import io.helidon.quic.frame.ResetStreamFrame;
import io.helidon.quic.frame.RetireConnectionIDFrame;
import io.helidon.quic.frame.StopSendingFrame;
import io.helidon.quic.frame.StreamDataBlockedFrame;
import io.helidon.quic.frame.StreamFrame;
import io.helidon.quic.frame.StreamsBlockedFrame;
import io.helidon.quic.packet.HandshakePacket;
import io.helidon.quic.packet.InitialPacket;
import io.helidon.quic.packet.LongHeader;
import io.helidon.quic.packet.OneRttPacket;
import io.helidon.quic.packet.PacketSpace;
import io.helidon.quic.packet.QuicPacket;
import io.helidon.quic.packet.QuicPacket.PacketNumberSpace;
import io.helidon.quic.packet.QuicPacket.PacketType;
import io.helidon.quic.packet.QuicPacketDecodeException;
import io.helidon.quic.packet.QuicPacketDecoder;
import io.helidon.quic.packet.QuicPacketEncoder;
import io.helidon.quic.packet.QuicPacketEncoder.OutgoingQuicPacket;
import io.helidon.quic.packet.RetryPacket;
import io.helidon.quic.packet.VersionNegotiationPacket;
import io.helidon.quic.spi.QuicPacketTLSEngine;
import io.helidon.quic.stream.CryptoWriterQueue;
import io.helidon.quic.stream.QuicBidiStream;
import io.helidon.quic.stream.QuicBidiStreamImpl;
import io.helidon.quic.stream.QuicBidiStreamReservation;
import io.helidon.quic.stream.QuicConnectionStreams;
import io.helidon.quic.stream.QuicReceiverStream;
import io.helidon.quic.stream.QuicSenderStream;
import io.helidon.quic.stream.QuicSenderStreamImpl;
import io.helidon.quic.stream.QuicStream;
import io.helidon.quic.stream.QuicStream.StreamState;
import io.helidon.quic.stream.QuicStreams;

import static io.helidon.quic.QuicCloseCommand.transport;
import static io.helidon.quic.QuicConnectionId.MAX_CONNECTION_ID_LENGTH;
import static io.helidon.quic.QuicTransportErrors.NO_VIABLE_PATH;
import static io.helidon.quic.QuicTransportErrors.PROTOCOL_VIOLATION;
import static io.helidon.quic.QuicTransportParameters.ParameterId.active_connection_id_limit;
import static io.helidon.quic.QuicTransportParameters.ParameterId.initial_max_data;
import static io.helidon.quic.QuicTransportParameters.ParameterId.initial_max_stream_data_bidi_local;
import static io.helidon.quic.QuicTransportParameters.ParameterId.initial_max_stream_data_bidi_remote;
import static io.helidon.quic.QuicTransportParameters.ParameterId.initial_max_stream_data_uni;
import static io.helidon.quic.QuicTransportParameters.ParameterId.initial_max_streams_bidi;
import static io.helidon.quic.QuicTransportParameters.ParameterId.initial_max_streams_uni;
import static io.helidon.quic.QuicTransportParameters.ParameterId.initial_source_connection_id;
import static io.helidon.quic.QuicTransportParameters.ParameterId.max_idle_timeout;
import static io.helidon.quic.QuicTransportParameters.ParameterId.max_udp_payload_size;
import static io.helidon.quic.QuicTransportParameters.ParameterId.version_information;
import static io.helidon.quic.frame.QuicFrame.MAX_VL_INTEGER;
import static io.helidon.quic.packet.QuicPacketNumbers.computePacketNumberLength;
import static io.helidon.quic.stream.QuicStreams.isUnidirectional;
import static io.helidon.quic.stream.QuicStreams.streamType;

/**
 * This class implements a QUIC connection.
 * A QUIC connection is established between a client and a server over a
 * QuicEndpoint endpoint.
 * A QUIC connection can then multiplex multiple QUIC streams to the same server.
 *
 * <p>Specification: https://www.rfc-editor.org/info/rfc9000
 *        RFC 9000: QUIC: A UDP-Based Multiplexed and Secure Transport
 * <p>Specification: https://www.rfc-editor.org/info/rfc9001
 *        RFC 9001: Using TLS to Secure QUIC
 * <p>Specification: https://www.rfc-editor.org/info/rfc9002
 *        RFC 9002: QUIC Loss Detection and Congestion Control
 */
@Api.Internal
public class QuicConnectionImpl implements QuicConnection, QuicPacketReceiver {
    /**
     * Minimum maximum datagram size permitted by QUIC.
     */
    public static final int SMALLEST_MAXIMUM_DATAGRAM_SIZE = 1200;
    /**
     * Length, in bytes, of a stateless reset token.
     */
    public static final int RESET_TOKEN_LENGTH = 16; // RFC states 16 bytes for stateless token
    /**
     * Largest legal value accepted in MAX_STREAMS-style transport parameters and frames.
     */
    public static final long MAX_STREAMS_VALUE_LIMIT = 1L << 60; // cannot exceed 2^60 as per RFC

    /**
     * Default connection-level flow-control limit advertised in transport parameters.
     */
    public static final long DEFAULT_INITIAL_MAX_DATA;
    // The default value for the initial_max_data transport parameter that a QuicConnectionImpl
    // will send to its peer, if no value is provided by the higher level protocol.
    /**
     * Default per-stream flow-control limit advertised in transport parameters.
     */
    public static final long DEFAULT_INITIAL_STREAM_MAX_DATA;
    // The default value for the initial_max_stream_data_bidi_local, initial_max_stream_data_bidi_remote,
    // and initial_max_stream_data_uni transport parameters that a QuicConnectionImpl
    // will send to its peer, if no value is provided by the higher level protocol.
    /**
     * Default bidirectional stream limit advertised in transport parameters.
     */
    public static final long DEFAULT_MAX_BIDI_STREAMS;
    // The default value for the initial_max_streams_bidi transport parameter that a QuicConnectionImpl
    // will send to its peer, if no value is provided by the higher level protocol.
    // The Http3ClientImpl typically provides a value of 0, so this property has no effect
    // on QuicConnectionImpl instances created on behalf of the HTTP/3 client
    /**
     * Default unidirectional stream limit advertised in transport parameters.
     */
    public static final long DEFAULT_MAX_UNI_STREAMS;
    // The default value for the initial_max_streams_uni transport parameter that a QuicConnectionImpl
    // will send to its peer, if no value is provided by the higher level protocol.

    // Quic assumes a minimum packet size of 1200
    // See https://www.rfc-editor.org/rfc/rfc9000#name-datagram-size
    private static final QuicConfig DEFAULT_CONFIG = QuicConfig.create();
    private static final int MAX_IPV6_MTU = 65527;
    private static final int MAX_IPV4_MTU = 65507;
    private static final int INITIAL_SERVER_CONNECTION_ID_LENGTH = 17;
    // VarHandle provide the same atomic compareAndSet functionality
    // than Atomic* classes, but without the additional cost in
    // footprint.
    private static final VarHandle VERSION_NEGOTIATED;
    private static final VarHandle STATE;
    private static final VarHandle MAX_SND_DATA;
    private static final VarHandle MAX_RCV_DATA;
    private static final int MAX_INCOMING_CRYPTO_CAPACITY = 64 << 10;
    private static final int MAX_REASSEMBLY_NODES_PER_FLOW = 1024;
    private static final int MAX_REASSEMBLY_NODES_PER_CONNECTION = 4096;
    private static final Random RANDOM;
    // Maximum size of the connection's Direct ByteBuffer Pool.
    // For a connection configured to attempt sending datagrams in thread
    // (QuicEndpoint.SEND_DGRAM_ASYNC == false), 2 should be enough, as we
    // shouldn't have more than 2 packet number spaces active at the same time.
    private static final int MAX_DBB_POOL_SIZE = 3;
    private static final System.Logger LOGGER;

    static {
        DEFAULT_INITIAL_MAX_DATA = DEFAULT_CONFIG.initialMaxData();
        DEFAULT_INITIAL_STREAM_MAX_DATA = DEFAULT_CONFIG.initialMaxStreamData();
        DEFAULT_MAX_BIDI_STREAMS = DEFAULT_CONFIG.maxBidiStreams();
        DEFAULT_MAX_UNI_STREAMS = DEFAULT_CONFIG.maxUniStreams();
        RANDOM = new SecureRandom();
        LOGGER = System.getLogger(QuicConnectionImpl.class.getName());
        try {
            Lookup lookup = MethodHandles.lookup();
            VERSION_NEGOTIATED = lookup
                    .findVarHandle(QuicConnectionImpl.class, "versionNegotiated", boolean.class);
            STATE = lookup.findVarHandle(QuicConnectionImpl.class, "state", int.class);
            MAX_SND_DATA = lookup.findVarHandle(OneRttFlowControlledSendingQueue.class, "maxData", long.class);
            MAX_RCV_DATA = lookup.findVarHandle(OneRttFlowControlledReceivingQueue.class, "maxData", long.class);
        } catch (Exception x) {
            throw new ExceptionInInitializerError(x);
        }
    }

    /**
     * Stream manager for this connection.
     */
    private final QuicConnectionStreams streams;
    private final ReentrantLock streamDispatchLock = new ReentrantLock();
    private final ReassemblyBudgetOwner reassemblyBudgetOwner = new ReassemblyBudgetOwner();
    /**
     * Control and transport frames waiting to be emitted in application packet space.
     */
    private final Queue<QuicFrame> outgoing1RTTFrames = new ConcurrentLinkedQueue<>();
    /**
     * Idle timeout tracking for the connection.
     */
    private final IdleTimeoutManager idleTimeoutManager;
    /**
     * Endpoint that owns and drives this connection.
     */
    private final QuicEndpoint endpoint;
    private final QuicEndpointRouteLifecycle routeLifecycle = new QuicEndpointRouteLifecycle(this);
    private final QuicRttEstimator rttEstimator;
    private final QuicCongestionController congestionController;
    private final ConnectionTerminatorImpl terminator;
    private final QuicRuntimeConfig runtimeConfig;
    private final QuicConfig quicConfig;
    private final Duration initialResponseTimeout;
    private final long defaultInitialMaxData;
    private final long defaultInitialStreamMaxData;
    private final long defaultMaxBidiStreams;
    private final long defaultMaxUniStreams;
    private final boolean useDirectBufferPool;
    /**
     * The state of the quic connection.
     * The handshake is confirmed when HANDSHAKE_DONE has been received,
     * or when the first 1-RTT packet has been successfully decrypted.
     * See RFC 9001 section 4.1.2
     * https://www.rfc-editor.org/rfc/rfc9001#name-handshake-confirmed
     */
    private final StateHandle stateHandle = new StateHandle();
    private final AtomicBoolean startHandshakeCalled = new AtomicBoolean();
    private final QuicPathManager pathManager;
    private final PacketSpaceManager.PathRecoveryState pathRecoveryState;
    private final QuicInstance quicInstance;
    private final QuicTLSEngine quicTLSEngine;
    private final CodingContext codingContext;
    private final PacketSpaces packetSpaces;
    private final OneRttFlowControlledSendingQueue oneRttSndQueue =
            new OneRttFlowControlledSendingQueue();
    private final OneRttFlowControlledReceivingQueue oneRttRcvQueue;
    private final ReentrantLock peerCryptoFlowLock = new ReentrantLock();
    // for one-rtt crypto data (session tickets)
    private final ReassemblyBudget peerCryptoBudget = newReassemblyBudget();
    private final CryptoDataFlow peerCryptoFlow = CryptoDataFlow.create(peerCryptoBudget, KeySpace.ONE_RTT);
    private final CryptoWriterQueue localCryptoFlow = CryptoWriterQueue.create();
    private final HandshakeFlow handshakeFlow = new HandshakeFlow();
    private final CompletableFuture<Void> successfulHandshakeCF;
    // the initial (local) connection ID
    private final QuicConnectionId connectionId;
    private final PeerConnIdManager peerConnIdManager;
    private final LocalConnIdManager localConnIdManager;
    // the quic version from the first packet
    private final QuicVersion originalVersion;
    private final ReentrantLock handshakeLock = new ReentrantLock();
    private final String cachedToString;
    private final String socketId;
    private final String childSocketId;
    private final String logTag;
    private final PeerInfo remotePeer;
    private final PeerInfo localPeer;
    private final SequentialScheduler handshakeScheduler =
            SequentialScheduler.lockingScheduler(this::continueHandshake0);
    private final long labelId;
    private final ReentrantLock maxInitialTimerLock = new ReentrantLock();
    private final PathValidationTimer pathValidationTimer = new PathValidationTimer();
    private final LongSupplier pathValidationTimeoutSupplier = this::pathValidationTimeoutNanos;
    private final QuicInboundQueue<IncomingDatagram> incoming;
    // The ByteBuffer pool, which contains available byte buffers
    private final ConcurrentLinkedQueue<ByteBuffer> bbPool = new ConcurrentLinkedQueue<>();
    // The number of Direct Byte Buffers allocated for sending and managed by the pool.
    // This is the number of Direct Byte Buffers currently in flight, plus the number
    // of available byte buffers present in the pool. It will never exceed
    // MAX_DBB_POOL_SIZE.
    private final AtomicInteger bbAllocated = new AtomicInteger();
    private final SequentialScheduler incomingLoopScheduler;

    private volatile LongFunction<String> appErrorCodeToString;
    private volatile QuicConnectionId incomingInitialPacketSourceId;
    private volatile QuicTransportParameters localTransportParameters;
    private volatile QuicTransportParameters peerTransportParameters;
    private volatile byte[] initialToken = BufferData.EMPTY_BYTES;
    // the number of (active) connection ids the peer is willing to accept for a given connection
    private volatile long peerActiveConnIdsLimit = 2; // default is 2 as per RFC
    private volatile int state;
    // the quic version currently in use
    private volatile QuicVersion quicVersion;
    private volatile QuicPacketDecoder decoder;
    private volatile QuicPacketEncoder encoder;
    // (client-only) if true, we no longer accept VERSIONS packets
    private volatile boolean versionCompatible;
    // if true, we no longer accept version changes
    private volatile boolean versionNegotiated;
    // true if we changed version in response to VERSIONS packet
    private volatile boolean processedVersionsPacket;
    // starts at the configured default datagram size before peer transport parameters or path MTU adjustments
    private int maxPeerAdvertisedPayloadSize;
    private volatile MaxInitialTimer maxInitialTimer;

    {
        incomingLoopScheduler = SequentialScheduler.lockingScheduler(this::incoming);
    }

    /**
     * Creates a connection implementation bound to the supplied peer and QUIC instance.
     *
     * @param quicInstance  owning QUIC instance
     * @param runtimeConfig resolved implementation policy for the owning runtime
     * @param peerAddress   remote peer address
     * @param peerName      peer host name used for TLS engine creation
     * @param peerPort      peer port used for TLS engine creation
     * @param sslParameters TLS parameters for the connection
     * @param logTagFormat  format used to build the cached string representation
     * @param labelId       connection identifier used for logging and debugging
     */
    QuicConnectionImpl(QuicInstance quicInstance,
                       QuicRuntimeConfig runtimeConfig,
                       InetSocketAddress peerAddress,
                       String peerName,
                       int peerPort,
                       SSLParameters sslParameters,
                       String logTagFormat,
                       long labelId) {
        this(QuicVersion.firstFlightVersion(Objects.requireNonNull(runtimeConfig, "runtimeConfig")
                                                    .userConfig()
                                                    .availableVersions()),
             quicInstance,
             runtimeConfig,
             peerAddress,
             peerName,
             peerPort,
             sslParameters,
             logTagFormat,
             labelId);
    }

    private QuicConnectionImpl(QuicInstance quicInstance,
                               QuicRuntimeConfig runtimeConfig,
                               InetSocketAddress peerAddress,
                               String peerName,
                               int peerPort,
                               SSLParameters sslParameters,
                               Duration initialResponseTimeout,
                               String logTagFormat,
                               long labelId) {
        this(QuicVersion.firstFlightVersion(Objects.requireNonNull(runtimeConfig, "runtimeConfig")
                                                    .userConfig()
                                                    .availableVersions()),
             quicInstance,
             runtimeConfig,
             peerAddress,
             peerName,
             peerPort,
             sslParameters,
             quicInstance.quicTlsContext(),
             initialResponseTimeout,
             logTagFormat,
             labelId);
    }

    /**
     * Creates a connection implementation bound to the supplied peer and QUIC instance.
     *
     * @param firstFlightVersion version to use for the first flight
     * @param quicInstance       owning QUIC instance
     * @param runtimeConfig      resolved implementation policy for the owning runtime
     * @param peerAddress        remote peer address
     * @param peerName           peer host name used for TLS engine creation
     * @param peerPort           peer port used for TLS engine creation
     * @param sslParameters      TLS parameters for the connection
     * @param logTagFormat       format used to build the cached string representation
     * @param labelId            connection identifier used for logging and debugging
     */
    QuicConnectionImpl(QuicVersion firstFlightVersion,
                       QuicInstance quicInstance,
                       QuicRuntimeConfig runtimeConfig,
                       InetSocketAddress peerAddress,
                       String peerName,
                       int peerPort,
                       SSLParameters sslParameters,
                       String logTagFormat,
                       long labelId) {
        this(firstFlightVersion,
             quicInstance,
             runtimeConfig,
             peerAddress,
             peerName,
             peerPort,
             sslParameters,
             quicInstance.quicTlsContext(),
             logTagFormat,
             labelId);
    }

    /**
     * Creates a connection implementation bound to the supplied peer and QUIC instance.
     *
     * @param firstFlightVersion version to use for the first flight
     * @param quicInstance       owning QUIC instance
     * @param runtimeConfig      resolved implementation policy for the owning runtime
     * @param peerAddress        remote peer address
     * @param peerName           peer host name used for TLS engine creation
     * @param peerPort           peer port used for TLS engine creation
     * @param sslParameters      TLS parameters for the connection
     * @param quicTLSContext     TLS context for the connection
     * @param logTagFormat       format used to build the cached string representation
     * @param labelId            connection identifier used for logging and debugging
     */
    QuicConnectionImpl(QuicVersion firstFlightVersion,
                       QuicInstance quicInstance,
                       QuicRuntimeConfig runtimeConfig,
                       InetSocketAddress peerAddress,
                       String peerName,
                       int peerPort,
                       SSLParameters sslParameters,
                       QuicTLSContext quicTLSContext,
                       String logTagFormat,
                       long labelId) {
        this(firstFlightVersion,
             quicInstance,
             runtimeConfig,
             peerAddress,
             peerName,
             peerPort,
             sslParameters,
             quicTLSContext,
             null,
             logTagFormat,
             labelId);
    }

    private QuicConnectionImpl(QuicVersion firstFlightVersion,
                               QuicInstance quicInstance,
                               QuicRuntimeConfig runtimeConfig,
                               InetSocketAddress peerAddress,
                               String peerName,
                               int peerPort,
                               SSLParameters sslParameters,
                               QuicTLSContext quicTLSContext,
                               Duration initialResponseTimeout,
                               String logTagFormat,
                               long labelId) {
        Objects.requireNonNull(firstFlightVersion, "firstFlightVersion");
        Objects.requireNonNull(quicTLSContext, "quicTLSContext");
        this.labelId = labelId;
        this.quicInstance = Objects.requireNonNull(quicInstance, "quicInstance");
        this.socketId = Objects.requireNonNull(quicInstance.instanceId(), "QUIC instance ID");
        this.childSocketId = Long.toString(labelId);
        this.logTag = socketId + " " + childSocketId;
        this.appErrorCodeToString = quicInstance::appErrorToString;
        this.runtimeConfig = Objects.requireNonNull(runtimeConfig, "runtimeConfig");
        this.quicConfig = runtimeConfig.userConfig();
        boolean isClientConn = isClientConnection();
        if (isClientConn) {
            Duration responseTimeout = initialResponseTimeout == null
                    ? QuicClientRuntime.DEFAULT_INITIAL_RESPONSE_TIMEOUT
                    : initialResponseTimeout;
            if (responseTimeout.isNegative() || responseTimeout.isZero()) {
                throw new IllegalArgumentException("initialResponseTimeout must be positive: " + responseTimeout);
            }
            this.initialResponseTimeout = responseTimeout;
        } else {
            if (initialResponseTimeout != null) {
                throw new IllegalArgumentException("initialResponseTimeout is client-only");
            }
            this.initialResponseTimeout = null;
        }
        this.defaultInitialMaxData = quicConfig.initialMaxData();
        this.defaultInitialStreamMaxData = quicConfig.initialMaxStreamData();
        this.defaultMaxBidiStreams = quicConfig.maxBidiStreams();
        this.defaultMaxUniStreams = quicConfig.maxUniStreams();
        int initialDatagramSize = runtimeConfig.endpoint().defaultDatagramSize();
        this.useDirectBufferPool = runtimeConfig.endpoint().useDirectBufferPool();
        this.rttEstimator = QuicRttEstimator.create(runtimeConfig.recovery());
        this.oneRttRcvQueue = new OneRttFlowControlledReceivingQueue(this::logTag, defaultInitialMaxData);
        this.endpoint = quicInstance.endpoint();
        this.incoming = new QuicInboundQueue<>(datagram -> datagram.buffer().remaining(),
                                               endpoint::buffer,
                                               endpoint::unbuffer);
        SocketAddress boundAddress = endpoint.localAddress();
        if (!(boundAddress instanceof InetSocketAddress localAddress)) {
            throw new IllegalStateException("QUIC endpoint is not bound to an internet socket address: " + boundAddress);
        }
        this.peerConnIdManager = new PeerConnIdManager(this);
        this.pathManager = new QuicPathManager(isClientConn,
                                               localAddress,
                                               peerAddress,
                                               initialDatagramSize,
                                               peerConnIdManager);
        this.pathRecoveryState = new PacketSpaceManager.PathRecoveryState(pathManager.generation());
        this.maxPeerAdvertisedPayloadSize = initialDatagramSize;
        this.cachedToString = String.format(logTagFormat.formatted("quic:%s:%s:%s"), labelId,
                                            Arrays.toString(sslParameters.getApplicationProtocols()), peerAddress);
        this.connectionId = this.endpoint.idFactory().newConnectionId();
        this.congestionController = switch (runtimeConfig.userConfig().congestionAlgorithm()) {
            case RENO -> QuicRenoCongestionController.create(runtimeConfig, logTag, rttEstimator, initialDatagramSize);
            case CUBIC -> QuicCubicCongestionController.create(runtimeConfig, logTag, rttEstimator, initialDatagramSize);
        };
        this.originalVersion = firstFlightVersion;
        this.quicVersion = firstFlightVersion;
        this.localConnIdManager = new LocalConnIdManager(this, connectionId);
        this.decoder = QuicPacketDecoder.of(this.quicVersion);
        this.encoder = QuicPacketEncoder.of(this.quicVersion);
        this.codingContext = new QuicCodingContext();
        QuicTLSEngine engine = quicTLSContext.createEngine(peerName, peerPort);
        engine.clientMode(isClientConn);
        engine.sslParameters(sslParameters);
        this.quicTLSEngine = engine;
        this.remotePeer = new ConnectionPeerInfo(true);
        this.localPeer = new ConnectionPeerInfo(false);
        quicTLSEngine.remoteQuicTransportParametersConsumer(this::consumeQuicParameters);
        packetSpaces = PacketSpaces.forConnection(this);
        quicTLSEngine.oneRttContext(packetSpaces.oneRttContext());
        streams = QuicConnectionStreams.create(this);
        if (quicInstance.isClient()) {
            // use the (INITIAL) token that a server might have sent to this client (through
            // NEW_TOKEN frame) on a previous connection against that server
            this.initialToken = quicInstance.initialTokenFor(peerAddress(), quicVersion).orElse(BufferData.EMPTY_BYTES);
        }
        terminator = new ConnectionTerminatorImpl(this);
        idleTimeoutManager = new IdleTimeoutManager(this);
        successfulHandshakeCF = handshakeFlow.handshakeCF().thenAccept(this::onHandshakeCompletion);
        if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
            logDebug("Quic Connection Created");
        }
    }

    /**
     * Creates a client-side QUIC connection implementation.
     *
     * @param quicInstance  owning QUIC instance
     * @param runtimeConfig resolved implementation policy for the owning runtime
     * @param peerAddress   remote peer address
     * @param tlsPeer       peer host and port used for TLS engine creation
     * @param sslParameters TLS parameters for the connection
     * @param initialResponseTimeout maximum wait for the peer's first Initial response
     * @param labelId       connection identifier used for logging and debugging
     * @return new client QUIC connection and its owning endpoint
     */
    static ClientConnectionCreation create(QuicInstance quicInstance,
                                           QuicRuntimeConfig runtimeConfig,
                                           InetSocketAddress peerAddress,
                                           InetSocketAddress tlsPeer,
                                           SSLParameters sslParameters,
                                           Duration initialResponseTimeout,
                                           long labelId) {
        ClientConnection connection = new ClientConnection(quicInstance,
                                                           runtimeConfig,
                                                           peerAddress,
                                                           tlsPeer,
                                                           sslParameters,
                                                           initialResponseTimeout,
                                                           labelId);
        return new ClientConnectionCreation(connection, connection.endpoint());
    }

    /**
     * Sets a transport-parameter value if it has not been explicitly configured yet.
     *
     * @param params        transport parameters to update
     * @param paramId       parameter identifier
     * @param valueSupplier supplier of the default value
     */
    protected static void putIntParameterIfAbsent(QuicTransportParameters params,
                                              ParameterId paramId,
                                              Supplier<Long> valueSupplier) {
        if (params.isPresent(paramId)) {
            return;
        }
        params.intParameter(paramId, valueSupplier.get());
    }

    static void closeUnclaimedLocalStream(QuicSenderStream stream) {
        try {
            stream.reset(0);
        } finally {
            if (stream instanceof QuicReceiverStream receiver) {
                receiver.requestStopSending(0);
            }
        }
    }

    /**
     * Returns the transport-internal numeric connection identifier.
     *
     * @return internal connection identifier
     */
    public final long uniqueId() {
        return labelId;
    }

    @Override
    public String socketId() {
        return socketId;
    }

    @Override
    public String childSocketId() {
        return childSocketId;
    }

    @Override
    public boolean isSecure() {
        return true;
    }

    @Override
    public PeerInfo remotePeer() {
        return remotePeer;
    }

    @Override
    public PeerInfo localPeer() {
        return localPeer;
    }

    /**
     * Stops the incoming datagram loop and releases any queued buffers.
     */
    public void closeIncoming() {
        incomingLoopScheduler.stop();
        incoming.closeAndDrain();
    }

    /**
     * Returns the endpoint that owns this connection.
     *
     * @return owning endpoint
     */
    QuicEndpoint endpoint() {
        return endpoint;
    }

    QuicEndpointRouteLifecycle routeLifecycle() {
        return routeLifecycle;
    }

    /**
     * Returns the stream manager for this connection.
     *
     * @return connection stream manager
     */
    QuicConnectionStreams streams() {
        return streams;
    }

    /**
     * Creates a flow-local handle backed by this connection's reassembly-node budget.
     *
     * @return reassembly budget handle
     */
    public ReassemblyBudget newReassemblyBudget() {
        return reassemblyBudgetOwner.newBudget();
    }

    void closeReassemblyBudgets() {
        reassemblyBudgetOwner.close();
    }

    void discardPeerCryptoFlow(KeySpace keySpace) {
        peerCryptoFlowLock.lock();
        try {
            switch (keySpace) {
            case INITIAL -> {
                handshakeFlow.peerInitialBudget.close();
                handshakeFlow.peerInitial.clear();
            }
            case HANDSHAKE -> {
                handshakeFlow.peerHandshakeBudget.close();
                handshakeFlow.peerHandshake.clear();
            }
            case ONE_RTT -> {
                peerCryptoBudget.close();
                peerCryptoFlow.clear();
            }
            default -> throw new IllegalArgumentException(
                    "No peer crypto reassembly flow exists for key space " + keySpace);
            }
        } finally {
            peerCryptoFlowLock.unlock();
        }
    }

    /**
     * Terminates all streams owned by this connection.
     *
     * @param termination connection termination
     */
    void terminateStreams(QuicTermination termination) {
        runWithStreamDispatchLock(() -> streams.terminate(termination));
    }

    /**
     * Runs a stream state transition without racing packet dispatch resolution.
     *
     * @param action stream state transition
     */
    public void runWithStreamDispatchLock(Runnable action) {
        Objects.requireNonNull(action, "action");
        streamDispatchLock.lock();
        try {
            action.run();
        } finally {
            streamDispatchLock.unlock();
        }
    }

    boolean withStreamDispatchLock(BooleanSupplier action) {
        Objects.requireNonNull(action, "action");
        streamDispatchLock.lock();
        try {
            return action.getAsBoolean();
        } finally {
            streamDispatchLock.unlock();
        }
    }

    boolean withConnectionIdLock(BooleanSupplier action) {
        return localConnIdManager.withConnectionIdLock(action);
    }

    /**
     * Returns the idle-timeout manager for this connection.
     *
     * @return idle-timeout manager
     */
    IdleTimeoutManager idleTimeoutManager() {
        return idleTimeoutManager;
    }

    /**
     * Returns the round-trip-time estimator for this connection.
     *
     * @return RTT estimator
     */
    QuicRttEstimator rttEstimator() {
        return rttEstimator;
    }

    /**
     * Returns the congestion controller for this connection.
     *
     * @return congestion controller
     */
    QuicCongestionController congestionController() {
        return congestionController;
    }

    /**
     * Returns the connection terminator.
     *
     * @return connection terminator
     */
    ConnectionTerminatorImpl terminator() {
        return terminator;
    }

    /**
     * Returns the largest packet number acknowledged by the peer in the given space.
     *
     * @param packetSpace packet number space to inspect
     * @return largest peer-acknowledged packet number
     */
    public long largestAckedPN(PacketNumberSpace packetSpace) {
        var space = packetSpaces.get(packetSpace);
        return space.largestPeerAcknowledgedPacketNumber();
    }

    /**
     * Returns the largest packet number processed in the given space.
     *
     * @param packetSpace packet number space to inspect
     * @return largest processed packet number
     */
    public long largestProcessedPN(PacketNumberSpace packetSpace) {
        var space = packetSpaces.get(packetSpace);
        return space.largestProcessedPacketNumber();
    }

    /**
     * Returns the length, in bytes, of locally generated connection IDs.
     *
     * @return local connection-id length
     */
    public int connectionIdLength() {
        return localConnectionIdOrThrow().length();
    }

    /**
     * Returns the owning QUIC instance.
     *
     * @return owning QUIC instance
     */
    public QuicInstance quicInstance() {
        return this.quicInstance;
    }

    /**
     * Sets the formatter used for application error codes on this connection.
     *
     * @param errorCodeToString formatter for application error codes that must return a non-null description
     */
    public void applicationErrors(LongFunction<String> errorCodeToString) {
        this.appErrorCodeToString = Objects.requireNonNull(errorCodeToString, "errorCodeToString");
    }

    /**
     * Returns a human-readable description of the supplied application error code.
     *
     * @param errorCode application error code
     * @return non-null formatted error description
     */
    public String appErrorToString(long errorCode) {
        return Objects.requireNonNull(appErrorCodeToString.apply(errorCode), "application error formatter result");
    }

    /**
     * Returns the public QUIC transport configuration for this connection.
     *
     * @return QUIC configuration
     */
    public QuicConfig quicConfig() {
        return quicConfig;
    }

    QuicRuntimeConfig runtimeConfig() {
        return runtimeConfig;
    }

    /**
     * Returns the currently negotiated QUIC version.
     *
     * @return active QUIC version
     */
    @Override
    public QuicVersion quicVersion() {
        return this.quicVersion;
    }

    @Override
    public CompletableFuture<QuicBidiStream> openNewLocalBidiStream(Duration limitIncreaseDuration) {
        Objects.requireNonNull(limitIncreaseDuration, "limitIncreaseDuration");
        return openNewLocalStream(() -> streams.createNewLocalBidiStream(limitIncreaseDuration));
    }

    @Override
    public CompletableFuture<QuicBidiStreamReservation> reserveNewLocalBidiStream() {
        if (!stateHandle.opened()) {
            return MinimalFuture.failedMinimalFuture(new ClosedChannelException());
        }
        var handshake = handshakeFlow.handshakeCF();
        if (handshake.isDone()) {
            if (handshake.isCompletedExceptionally() || handshake.isCancelled()) {
                return handshake.thenCompose(_ -> streams.reserveNewLocalBidiStream());
            }
            try {
                return streams.reserveNewLocalBidiStream();
            } catch (RuntimeException | Error failure) {
                return MinimalFuture.failedMinimalFuture(failure);
            }
        }
        var result = MinimalFuture.<QuicBidiStreamReservation>create();
        var acquisition = new AtomicReference<CompletableFuture<QuicBidiStreamReservation>>();
        result.whenComplete((_, _) -> {
            if (result.isCancelled()) {
                CompletableFuture<QuicBidiStreamReservation> pending = acquisition.get();
                if (pending != null) {
                    pending.cancel(false);
                }
            }
        });
        handshake.whenComplete((_, handshakeFailure) -> {
            if (handshakeFailure != null) {
                result.completeExceptionally(handshakeFailure);
                return;
            }
            if (result.isDone()) {
                return;
            }
            CompletableFuture<QuicBidiStreamReservation> pending;
            try {
                pending = streams.reserveNewLocalBidiStream();
            } catch (RuntimeException | Error failure) {
                result.completeExceptionally(failure);
                return;
            }
            acquisition.set(pending);
            pending.whenComplete((reservation, failure) -> {
                if (failure != null) {
                    result.completeExceptionally(failure);
                } else if (!result.complete(reservation)) {
                    reservation.close();
                }
            });
            if (result.isCancelled()) {
                pending.cancel(false);
            }
        });
        return result;
    }

    @Override
    public CompletableFuture<QuicSenderStream> openNewLocalUniStream(Duration limitIncreaseDuration) {
        Objects.requireNonNull(limitIncreaseDuration, "limitIncreaseDuration");
        return openNewLocalStream(() -> streams.createNewLocalUniStream(limitIncreaseDuration));
    }

    @Override
    public QuicRemoteStreamRegistration addRemoteStreamListener(Predicate<? super QuicReceiverStream> streamConsumer) {
        Objects.requireNonNull(streamConsumer, "streamConsumer");
        AtomicBoolean closed = new AtomicBoolean();
        Predicate<QuicReceiverStream> registration = stream -> !closed.get() && streamConsumer.test(stream);
        if (!streams.addRemoteStreamListener(registration)) {
            closed.set(true);
        }
        return () -> {
            if (closed.compareAndSet(false, true)) {
                streams.removeRemoteStreamListener(registration);
            }
        };
    }

    /**
     * Returns all transport streams currently known to the connection.
     *
     * @return current streams
     */
    public Stream<? extends QuicStream> quicStreams() {
        return streams.quicStreams();
    }

    @Override
    public List<QuicConnectionId> connectionIds() {
        return localConnIdManager.connectionIds();
    }

    @Override
    public List<QuicPacketReceiver.PeerResetToken> activeResetTokens() {
        return peerConnIdManager.activeResetTokens();
    }

    EndpointRoutes freezeEndpointRoutes() {
        localConnIdManager.freezeConnectionIds();
        List<QuicConnectionId> connectionIds = connectionIds();
        List<QuicPacketReceiver.PeerResetToken> resetTokens = peerConnIdManager.freezeResetTokenRoutes();
        return new EndpointRoutes(connectionIds, resetTokens);
    }

    /**
     * Returns the local connection id.
     *
     * @return the local connection id.
     */
    public Optional<QuicConnectionId> localConnectionId() {
        return Optional.of(connectionId);
    }

    /**
     * Returns the peer connection id.
     *
     * @return the peer connection id.
     */
    public QuicConnectionId peerConnectionId() {
        return this.peerConnIdManager.peerConnectionId();
    }

    @Override
    public boolean accepts(SocketAddress source) {
        if (!pathManager.accepts(source)) {
            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                logDebug("unexpected sender %s, skipping packet", source);
            }
            return false;
        }
        return true;
    }

    /**
     * Processes an incoming datagram read by the endpoint.
     *
     * @param source      sender address
     * @param destConnId  destination connection ID bytes
     * @param headersType decoded QUIC header type
     * @param buffer      datagram payload
     */
    public void processIncoming(SocketAddress source, ByteBuffer destConnId,
                                QuicPacket.HeadersType headersType, ByteBuffer buffer) {
        // Processes an incoming datagram that has just been
        // read off the network.
        if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
            logDebug("processIncoming %s(pos=%d, remaining=%d)",
                      headersType, buffer.position(), buffer.remaining());
        }
        if (!stateHandle.opened()) {
            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                logDebug("connection closed, skipping packet");
            }
            return;
        }
        if (!(source instanceof InetSocketAddress inetSource) || inetSource.isUnresolved()) {
            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                logDebug("unsupported sender %s, skipping datagram", source);
            }
            return;
        }
        IncomingDatagram datagram = pathManager.receive(new IncomingDatagram(inetSource,
                                                                             destConnId,
                                                                             headersType,
                                                                             buffer));
        if (datagram.amplificationBudgetIncreased()) {
            runPacketSpaceTransmitters();
        }
        scheduleForDecryption(datagram);
    }

    /**
     * Processes the packets contained in a datagram after it has been queued for decryption.
     *
     * @param source      source address of the datagram
     * @param destConnId  destination connection ID used to route the datagram
     * @param headersType expected packet-header type
     * @param buffer      datagram payload, possibly containing coalesced packets
     */
    public void internalProcessIncoming(SocketAddress source, ByteBuffer destConnId,
                                        QuicPacket.HeadersType headersType, ByteBuffer buffer) {
        if (!(source instanceof InetSocketAddress inetSource) || inetSource.isUnresolved()) {
            return;
        }
        QuicPathManager.ReceiveContext receiveContext = pathManager.receive(inetSource, buffer.remaining());
        if (receiveContext.amplificationBudgetIncreased()) {
            runPacketSpaceTransmitters();
        }
        internalProcessIncoming(receiveContext,
                                destConnId,
                                headersType,
                                buffer);
    }

    /**
     * Called when an incoming packet has been decrypted.
     *
     * @param quicPacket the decrypted quic packet
     */
    public void processDecrypted(QuicPacket quicPacket) {
        processDecrypted(pathManager.receive(peerAddress(), 0), quicPacket);
    }

    /**
     * Returns the next (higher) max streams limit that should be advertised to the remote peer.
     * Returns {@code 0} if the limit should not be increased.
     *
     * @return the next (higher) max streams limit that should be advertised to the remote peer,
     *         or {@code 0} if the limit should not be increased
     *
     * @param bidi true if bidirectional stream, false otherwise
     */
    public long nextMaxStreamsLimit(boolean bidi) {
        return nextMaxStreamsLimit(bidi, false);
    }

    /**
     * Returns the next (higher) max streams limit that should be advertised to the remote peer.
     * Returns {@code 0} if the limit should not be increased.
     *
     * @param bidi        true if bidirectional stream, false otherwise
     * @param peerBlocked whether the peer reported that it is blocked on the current limit
     * @return the next (higher) max streams limit that should be advertised to the remote peer,
     *         or {@code 0} if the limit should not be increased
     */
    public long nextMaxStreamsLimit(boolean bidi, boolean peerBlocked) {
        if (isClientConnection() && bidi) {
            return 0; // server does not open bidi streams
        }
        return streams.nextMaxStreamsLimit(bidi, peerBlocked);
    }

    /**
     * Called when a stateless reset token is received.
     */
    @Override
    public void processStatelessReset() {
        terminator.incomingStatelessReset();
    }

    /**
     * Returns the peer address that should be used when sending datagram
     * to the peer.
     *
     * @return the peer address that should be used when sending datagram
     *         to the peer
     */
    public InetSocketAddress peerAddress() {
        return pathManager.peerAddress();
    }

    /**
     * Returns the local address of the quic endpoint.
     *
     * @return the local address of the quic endpoint.
     */
    public SocketAddress localAddress() {
        return pathManager.localAddress();
    }

    /**
     * Called when a datagram scheduled for writing by this connection
     * could not be written to the network.
     *
     * @param t the error that occurred
     */
    @Override
    public void onWriteError(Throwable t) {
        // log exception if still opened
        if (stateHandle.opened()) {
            log(LOGGER, System.Logger.Level.ERROR, "%s", t, "Failed to write datagram");
        }
    }

    /**
     * Called when a packet couldn't be processed.
     *
     * @param packet packet being processed when the error happened
     * @param t      the error that occurred
     */
    public void onProcessingError(QuicPacket packet, Throwable t) {
        processPacketFailure(packet.packetType(), t, false);
    }

    /**
     * Starts the Quic Handshake.
     *
     * @return A completable future which will be completed when the
     *        handshake is completed.
     * @throws UnsupportedOperationException If this connection isn't a client connection
     */
    public final CompletableFuture<Void> startHandshake() {
        if (!isClientConnection()) {
            throw new UnsupportedOperationException("Not a client connection, cannot start handshake");
        }
        if (!this.startHandshakeCalled.compareAndSet(false, true)) {
            throw new IllegalStateException("handshake has already been started on connection");
        }
        if (peerAddress().isUnresolved()) {
            // fail if address is unresolved
            return MinimalFuture.failedMinimalFuture(
                    new QuicConnectionException("QUIC peer address is unresolved", new UnresolvedAddressException()));
        }
        CompletableFuture<Void> cf;
        try {
            // register the connection with an endpoint
            endpoint.registerNewConnection(this);
            cf = MinimalFuture.completedMinimalFuture(null);
        } catch (Throwable t) {
            cf = MinimalFuture.failedMinimalFuture(t);
        }
        return cf.thenApply(this::sendFirstInitialPacket)
                .exceptionally((t) -> {
                    // complete the handshake CFs with the failure
                    handshakeFlow.failHandshakeCFs(t);
                    return handshakeFlow;
                })
                .thenCompose(_ -> successfulHandshakeCF());
    }

    /**
     * Returns the maximum datagram size that can be used on the
     * connection path.
     *
     * @return the maximum datagram size that can be used on the
     *         connection path
     *
     * @implSpec Initially this is 1200 bytes, but the value will then be decided if the peer sends a specific size
     *        in the transport parameters and the value can further evolve based
     *        on path MTU.
     */
    int maxDatagramSize() {
        // The current transport stays within the peer-advertised payload size
        // and the configured path MTU. Dynamic path MTU discovery is deliberately
        // left outside the initial HTTP/3 scope.
        return Math.min(maxPeerAdvertisedPayloadSize, pathManager.pathMtu());
    }

    /**
     * Returns the most recent transport parameters received from the peer.
     *
     * @return peer transport parameters when received
     */
    public Optional<QuicTransportParameters> peerTransportParameters() {
        return Optional.ofNullable(peerTransportParameters);
    }

    /**
     * Returns the transport parameters currently advertised locally.
     *
     * @return local transport parameters when initialized
     */
    public Optional<QuicTransportParameters> localTransportParameters() {
        return Optional.ofNullable(localTransportParameters);
    }

    /**
     * Requests a transport PING.
     *
     * @return future completed with the measured response duration in milliseconds
     */
    public CompletableFuture<Long> requestSendPing() {
        KeySpace space = quicTLSEngine.currentSendKeySpace();
        PacketSpace spaceManager = packetSpaces.get(PacketNumberSpace.of(space));
        return spaceManager.requestSendPing();
    }

    /**
     * Returns the underlying {@code NetworkChannel} used by this connection,
     * if the endpoint has already been configured.
     *
     * @return the underlying {@code NetworkChannel} used by this connection,
     *         if the endpoint has already been configured
     */
    public Optional<NetworkChannel> channel() {
        QuicEndpoint endpoint = this.endpoint;
        return endpoint == null ? Optional.empty() : Optional.of(endpoint.channel());
    }

    @Override
    public String toString() {
        return cachedToString;
    }

    @Override
    public boolean isOpen() {
        return stateHandle.opened();
    }

    @Override
    public Optional<QuicTermination> termination() {
        return Optional.ofNullable(terminator.termination());
    }

    @Override
    public CompletionStage<QuicTermination> whenTerminated() {
        return terminator.futureTermination().minimalCompletionStage();
    }

    @Override
    public void terminate(QuicCloseCommand command) {
        terminator.terminate(command);
    }

    /**
     * Returns a future that completes when the connection terminates.
     *
     * @return termination future
     */
    public final CompletableFuture<QuicTermination> futureTermination() {
        return terminator.futureTermination();
    }

    CompletionStage<Void> cleanupComplete() {
        return terminator.cleanupComplete().minimalCompletionStage();
    }

    /**
     * Returns true if this connection is a client connection.
     *
     * @return true if this connection is a client connection.
     * Server side connections will return false.
     */
    public boolean isClientConnection() {
        return true;
    }

    /**
     * Signal the connection that some stream data is available for sending on one or more streams.
     *
     * @param streamIds the stream ids
     */
    public void streamDataAvailableForSending(Set<Long> streamIds) {
        for (long id : streamIds) {
            streams.enqueueForSending(id);
        }
        packetSpaces.app.runTransmitter();
    }

    /**
     * Notifies this connection that a stream has data available for sending.
     *
     * @param sender stream with data available
     */
    public void streamDataAvailableForSending(QuicSenderStreamImpl sender) {
        streams.enqueueForSending(sender);
        packetSpaces.app.runTransmitter();
    }

    /**
     * Called when the receiving part or the sending part of a stream
     * reaches a terminal state.
     *
     * @param streamId the id of the stream
     * @param state    the terminal state
     */
    public void notifyTerminalState(long streamId, StreamState state) {
        streams.notifyTerminalState(streamId, state);
    }

    /**
     * Called to request sending of a RESET_STREAM frame.
     *
     * @param streamId  the id of the stream that should be reset
     * @param errorCode the application error code
     * <p>Note: Should only be called for sending streams. For stopping a
     *        receiving stream then {@link #scheduleStopSendingFrame(long, long)} should be called.
     *        This method should only be called from {@code QuicSenderStreamImpl}, after
     *        switching the state of the stream to RESET_SENT.
     */
    public void requestResetStream(long streamId, long errorCode) {
        streams.requestResetStream(streamId, errorCode);
        packetSpaces.app.runTransmitter();
    }

    /**
     * Called to request sending of a STOP_SENDING frame.
     *
     * @param streamId  the stream id to be cancelled
     * @param errorCode the application error code
     * <p>Note: Should only be called for receiving streams. For stopping a
     *        sending stream then {@link #requestResetStream(long, long)}
     *        should be called.
     *        This method should only be called from {@code QuicReceiverStreamImpl}
     */
    public void scheduleStopSendingFrame(long streamId, long errorCode) {
        streams.scheduleStopSendingFrame(StopSendingFrame.create(streamId, errorCode));
        packetSpaces.app.runTransmitter();
    }

    /**
     * Called to request sending of a MAX_STREAM_DATA frame.
     *
     * @param streamId      the stream id to be cancelled
     * @param maxStreamData the new max data we are prepared to receive on
     *                     this stream
     * <p>Note: Should only be called for receiving streams.
     *        This method should only be called from {@code QuicReceiverStreamImpl}
     */
    public void requestSendMaxStreamData(long streamId, long maxStreamData) {
        streams.requestSendMaxStreamData(MaxStreamDataFrame.create(streamId, maxStreamData));
        packetSpaces.app.runTransmitter();
    }

    /**
     * Called when frame data can be safely added to the amount of
     * data received by the connection for MAX_DATA flow control
     * purpose.
     *
     * @param diff      amount of newly received data
     * @param frameType type of frame received
     * @throws QuicTransportException if flow control was exceeded
     */
    public void increaseReceivedData(long diff, long frameType) throws QuicTransportException {
        oneRttRcvQueue.checkAndIncreaseReceivedData(diff, frameType);
    }

    /**
     * Called when frame data is removed from the connection
     * and the amount of data can be added to MAX_DATA window.
     *
     * @param diff amount of data processed
     */
    public void increaseProcessedData(long diff) {
        oneRttRcvQueue.increaseProcessedData(diff);
    }

    /**
     * Returns the transport TLS engine.
     *
     * @return QUIC TLS engine
     */
    public QuicTLSEngine tlsEngine() {
        return quicTLSEngine;
    }

    @Override
    public Optional<String> applicationProtocol() {
        return quicTLSEngine.applicationProtocol();
    }

    /**
     * Returns the computed PTO for the current packet number space,
     * adjusted by our max ack delay.
     *
     * @return the computed PTO for the current packet number space,
     *         adjusted by our max ack delay
     */
    public long peerPtoMs() {
        return rttEstimator.basePtoDuration().toMillis()
                + (
                        quicTLSEngine.currentSendKeySpace() == KeySpace.ONE_RTT
                                ? QuicTransportParametersConfigSupport.DEFAULT_MAX_ACK_DELAY.toMillis() : 0);
    }

    long pathGeneration() {
        return pathManager.generation();
    }

    /**
     * Runs the application packet-space transmitter.
     */
    public void runAppPacketSpaceTransmitter() {
        this.packetSpaces.app.runTransmitter();
    }

    /**
     * Closes all packet spaces for this connection.
     */
    public void shutdown() {
        packetSpaces.close();
    }

    /**
     * Returns the transport logging tag.
     *
     * @return logging tag
     */
    public final String logTag() {
        return logTag;
    }

    @Override
    public void datagramSent(QuicDatagram datagram) {
        datagramReleased(datagram);
    }

    @Override
    public void datagramDiscarded(QuicDatagram datagram) {
        if (LOGGER.isLoggable(System.Logger.Level.TRACE)) {
            log(LOGGER, System.Logger.Level.TRACE, "DIRECTBB: datagram discarded %s", datagram.payload().isDirect());
        }
        datagramReleased(datagram);
    }

    /**
     * Records that an outbound datagram was dropped before it reached the transport.
     *
     * @param datagram dropped datagram
     */
    public void datagramDropped(QuicDatagram datagram) {
        if (LOGGER.isLoggable(System.Logger.Level.TRACE)) {
            log(LOGGER, System.Logger.Level.TRACE, "DIRECTBB: datagram dropped %s", datagram.payload().isDirect());
        }
        datagramReleased(datagram);
    }

    /**
     * Returns a human-readable snapshot of connection state for diagnostics.
     *
     * @return diagnostic state text for HTTP/3 and QUIC debugging
     */
    public String loggableState() {
        // for HTTP3 debugging
        // If the connection was active (open bidi streams), log connection state
        if (streams.quicStreams().noneMatch(QuicStream::isBidirectional)) {
            // no active requests
            return "No active requests";
        }
        Deadline now = TimeSource.now();
        PacketSpaceManager appSpace = (PacketSpaceManager) packetSpaces.app;
        String currentDeadline = Utils.debugDeadline(now, appSpace.deadline());
        String prospectiveDeadline = Utils.debugDeadline(now, appSpace.prospectiveDeadline());
        StringBuilder result = new StringBuilder("sending: {canSend:" + oneRttSndQueue.canSend()
                                                         + ", credit: " + oneRttSndQueue.credit()
                                                         + ", sendersReady: " + streams.hasAvailableData()
                                                         + ", hasControlFrames: " + streams.hasControlFrames()
                                                         + "}, cc: { backoff: " + rttEstimator.ptoBackoff()
                                                         + ", duration: "
                                                         + appSpace.ptoDuration()
                                                         + ", current deadline: " + currentDeadline
                                                         + ", prospective deadline: " + prospectiveDeadline
                                                         + "}, streams: [");
        streams.quicStreams().filter(QuicStream::isBidirectional).forEach(
                s -> {
                    QuicBidiStreamImpl qb = (QuicBidiStreamImpl) s;
                    result.append("{id:" + s.streamId()
                                          + ", available: " + qb.senderPart().available()
                                          + ", blocked: " + qb.senderPart().isBlocked() + "},"
                    );
                }
        );
        result.append("]");
        return result.toString();
    }

    /**
     * Creates a new packet in the selected key space.
     *
     * @param keySpace TLS key space to use
     * @param frames   frames to include
     * @return new QUIC packet
     */
    final QuicPacket newQuicPacket(KeySpace keySpace,
                                   QuicConnectionId destinationConnectionId,
                                   List<QuicFrame> frames) {
        PacketSpace packetSpace = packetSpaces.get(PacketNumberSpace.of(keySpace));
        return encoder.newOutgoingPacket(keySpace, packetSpace,
                                         localConnectionIdOrThrow(), destinationConnectionId, initialToken(),
                                         frames,
                                         codingContext);
    }

    final Optional<QuicConnectionId> destinationConnectionId(KeySpace keySpace,
                                                              QuicPathManager.SendPermit permit) {
        if (keySpace != KeySpace.ONE_RTT) {
            return Optional.of(peerConnectionId());
        }
        return pathManager.cidBinding(permit).map(PeerConnIdManager.PathCidBinding::connectionId);
    }

    /**
     * Encrypt an outgoing quic packet.
     * The ProtectionRecord indicates the position at which the encrypted packet
     * should be written in the datagram, as well as the position of the
     * first packet in the datagram. After encrypting the packet, this method calls
     * {@link #pushEncryptedDatagram(ProtectionRecord)}
     *
     * @param protectionRecord a record containing a quic packet to encrypt,
     *                        a destination byte buffer, and various offset information.
     */
    final void pushDatagram(ProtectionRecord protectionRecord)
            throws QuicKeyUnavailableException, QuicTransportException {
        QuicPacket packet = protectionRecord.packet();
        QuicPathManager.SendPermit permit = protectionRecord.permit();
        if (permit == null) {
            throw new IllegalArgumentException("Outgoing QUIC packet is missing a path send permit");
        }
        if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
            logDebug("encrypting packet into datagram %s(pn:%s, %s)", packet.packetType(),
                      packet.packetNumber(), packet.frames());
        }
        // Processes an outgoing unencrypted packet that needs to be
        // encrypted before being packaged in a datagram.
        ProtectionRecord encrypted;
        try {
            encrypted = protectionRecord.encrypt(codingContext);
        } catch (Throwable e) {
            protectionRecord.permit().release();
            // release the datagram ByteBuffer on failure to encrypt
            datagramDiscarded(QuicDatagram.create(this,
                                                  protectionRecord.destination(),
                                                  protectionRecord.datagram()));
            if (isExpectedKeyDiscardFailure(packet, e)) {
                if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                    logDebug("failed to encrypt %s packet pn=%s after %s keys were discarded: %s",
                              packet.packetType(), packet.packetNumber(), packet.numberSpace(), e.getMessage());
                }
            } else {
                log(LOGGER, System.Logger.Level.ERROR, "%s", e, "Failed to encrypt packet");
            }
            throw e;
        }
        // we currently don't support a ProtectionRecord with more than one QuicPacket
        // encryption of the datagram is complete, now push the encrypted
        // datagram through the endpoint
        logPacket(false, packet);
        pushEncryptedDatagram(encrypted);
    }

    /**
     * Completes the handshake future on the connection executor.
     */
    protected void completeHandshakeCF() {
        // This can be called from the decrypt loop, and can trigger
        // sending of 1-RTT application data from within the same
        // thread: we use an executor here to avoid running the application
        // sending loop from within the Quic decrypt loop.
        var handshakeCF = handshakeFlow.handshakeCF();
        if (handshakeCF.isDone()) {
            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                logDebug("completeHandshakeCF skipped, future already completed (state=%s)",
                          quicTLSEngine.handshakeState());
            }
            return;
        }
        var handshakeState = quicTLSEngine.handshakeState();
        if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
            logDebug("completing handshake future with state=%s", handshakeState);
        }
        try {
            handshakeCF.completeAsync(() -> handshakeState, quicInstance().executor());
        } catch (RejectedExecutionException e) {
            handshakeCF.completeExceptionally(e);
        }
    }

    void sendApplicationPacket(QuicPacket packet,
                               QuicPathManager.SendPermit permit)
            throws QuicKeyUnavailableException, QuicTransportException {
        if (packet.packetType() == PacketType.ONERTT) {
            PeerConnIdManager.PathCidBinding binding = permit.binding();
            if (binding == null) {
                throw new PathSendBlockedException("No destination connection ID available for path");
            }
            if (!binding.connectionId().equals(packet.destinationId())) {
                throw new IllegalArgumentException("1-RTT packet destination connection ID does not match its path permit");
            }
        }
        pushDatagram(ProtectionRecord.single(packet,
                                             permit.destination(),
                                             permit,
                                             QuicConnectionImpl.this::allocateDatagramForEncryption));
        if (packet.packetType() == PacketType.ONERTT
                && packet.frames().stream().anyMatch(HandshakeDoneFrame.class::isInstance)) {
            onHandshakeDoneSent();
        }
    }

    /**
     * Updates handshake state after a HANDSHAKE_DONE frame is sent.
     */
    protected void onHandshakeDoneSent() {
        logDebug("HANDSHAKE_DONE sent in 1-RTT packet");
        packetSpaces.app.confirmHandshake();
        packetSpaces.handshake.close();
        if (stateHandle.markHandshakeComplete()) {
            completeHandshakeCF();
        }
    }

    /**
     * Schedule a frame for sending in a 1-RTT packet.
     * <p>
     * For use with frames that do not change with time
     * (like MAX_* / *_BLOCKED / ACK),
     * or with remaining datagram capacity (like STREAM or CRYPTO),
     * and do not require certain path (PATH_CHALLENGE / RESPONSE).
     * <p>
     * Use with frames like HANDSHAKE_DONE, NEW_TOKEN,
     * NEW_CONNECTION_ID, RETIRE_CONNECTION_ID.
     * <p>
     * Maximum accepted frame size is 1000 bytes to ensure that the frame
     * will fit in a 1-RTT datagram in the foreseeable future.
     *
     * @param frame frame to send
     * @throws IllegalArgumentException if frame is larger than 1000 bytes
     */
    protected void enqueue1RTTFrame(QuicFrame frame) {
        if (frame.size() > 1000) {
            throw new IllegalArgumentException("Frame too big");
        }
        outgoing1RTTFrames.add(frame);
    }

    /**
     * Returns the packet encoder currently associated with the negotiated version.
     *
     * @return active packet encoder
     */
    protected QuicPacketEncoder encoder() {
        return encoder;
    }

    /**
     * Returns the packet decoder currently associated with the negotiated version.
     *
     * @return active packet decoder
     */
    protected QuicPacketDecoder decoder() {
        return decoder;
    }

    /**
     * Returns the connection state handle.
     *
     * @return state handle
     */
    protected final StateHandle stateHandle() {
        return stateHandle;
    }

    /**
     * Returns the coding context used for packet encoding and decoding.
     *
     * @return coding context for this connection
     */
    protected CodingContext codingContext() {
        return codingContext;
    }

    /**
     * Verifies a token presented in an Initial packet.
     *
     * @param destinationID destination connection ID from the packet
     * @param token         token supplied by the peer
     * @return {@code true} if the token is accepted
     */
    protected boolean verifyToken(QuicConnectionId destinationID, byte[] token) {
        // server must send zero-length token
        return Objects.requireNonNull(token, "token").length == 0;
    }

    /**
     * Creates the packet-emitter view used by packet spaces.
     *
     * @return packet emitter bound to this connection
     */
    protected PacketEmitter emitter() {
        return new PacketEmitter() {
            @Override
            public QuicTimerQueue timer() {
                return QuicConnectionImpl.this.endpoint().timer();
            }

            @Override
            public boolean retransmit(PacketSpace packetSpaceManager, QuicPacket packet, int attempts)
                    throws QuicKeyUnavailableException, QuicTransportException {
                try {
                    return QuicConnectionImpl.this.retransmit(packetSpaceManager, packet, attempts);
                } catch (QuicTransportException failure) {
                    terminator.terminate(QuicCloseCommand.transport(failure));
                    return false;
                }
            }

            @Override
            public long emitAckPacket(PacketSpace packetSpaceManager,
                                      AckFrame frame,
                                      boolean sendPing)
                    throws QuicKeyUnavailableException, QuicTransportException {
                try {
                    return QuicConnectionImpl.this.emitAckPacket(packetSpaceManager, frame, sendPing);
                } catch (QuicTransportException failure) {
                    terminator.terminate(QuicCloseCommand.transport(failure));
                    return -1L;
                }
            }

            @Override
            public void acknowledged(QuicPacket packet) {
                QuicConnectionImpl.this.packetAcknowledged(packet);
            }

            @Override
            public boolean sendData(PacketNumberSpace packetNumberSpace)
                    throws QuicKeyUnavailableException, QuicTransportException {
                return QuicConnectionImpl.this.sendData(packetNumberSpace);
            }

            @Override
            public boolean sendPriorityData(PacketNumberSpace packetNumberSpace)
                    throws QuicKeyUnavailableException, QuicTransportException {
                return QuicConnectionImpl.this.sendPriorityData(packetNumberSpace);
            }

            @Override
            public Executor executor() {
                return quicInstance().executor();
            }

            @Override
            public void reschedule(QuicTimedEvent task) {
                var endpoint = QuicConnectionImpl.this.endpoint();
                if (endpoint == null) {
                    return;
                }
                endpoint.timer().reschedule(task);
            }

            @Override
            public void reschedule(QuicTimedEvent task, Deadline deadline) {
                var endpoint = QuicConnectionImpl.this.endpoint();
                if (endpoint == null) {
                    return;
                }
                endpoint.timer().reschedule(task, deadline);
            }

            @Override
            public void checkAbort(PacketNumberSpace packetNumberSpace) {
                QuicConnectionImpl.this.checkAbort(packetNumberSpace);
            }

            @Override
            public void ptoBackoffIncreased(PacketSpaceManager space, long backoff) {
                logDebug("OUT: [%s] increase backoff to %s, duration %s ms: %s",
                           space.packetNumberSpace(), backoff, space.ptoDuration().toMillis(), rttEstimator.state());
            }

            @Override
            public String logTag() {
                return QuicConnectionImpl.this.logTag();
            }

            @Override
            public boolean isOpen() {
                return QuicConnectionImpl.this.stateHandle.opened();
            }
        };
    }

    /**
     * Returns the packet-space collection for this connection.
     *
     * @return packet-space collection
     */
    protected PacketSpaces packetNumberSpaces() {
        return packetSpaces;
    }

    QuicPathManager pathManager() {
        return pathManager;
    }

    PacketSpaceManager.PathRecoveryState pathRecoveryState() {
        return pathRecoveryState;
    }

    /**
     * Returns the packet space for the supplied packet number space.
     *
     * @param packetNumberSpace packet number space to resolve
     * @return associated packet space
     */
    protected PacketSpace packetSpace(PacketNumberSpace packetNumberSpace) {
        return packetSpaces.get(packetNumberSpace);
    }

    LocalConnIdManager localConnectionIdManager() {
        return localConnIdManager;
    }

    /**
     * Returns the original connection id.
     * This is the original destination connection id that
     * the client generated when connecting to the server for
     * the first time.
     *
     * @return the original connection id
     */
    protected QuicConnectionId originalServerConnId() {
        return this.peerConnIdManager.originalServerConnId();
    }

    /**
     * Returns true if this is a stream initiated locally, and false if
     * this is a stream initiated by the peer.
     *
     * @return true if this is a stream initiated locally, and false if
     *         this is a stream initiated by the peer
     *
     * @param streamId a stream ID.
     */
    protected final boolean isLocalStream(long streamId) {
        return isClientConnection() == QuicStreams.isClientInitiated(streamId);
    }

    /**
     * If a stream with this streamId was already created, returns it.
     *
     * @param streamId the stream ID
     * @return the stream identified by the given {@code streamId}, if it exists
     */
    protected Optional<QuicStream> findStream(long streamId) {
        return streams.findStream(streamId);
    }

    /**
     * Tell whether the supplied stream ID refers to an already-created stream.
     *
     * @param streamId the stream id
     * @return {@code true} if the stream was already opened
     */
    protected boolean isExistingStreamId(long streamId) {
        long next = streams.peekNextStreamId(streamType(streamId));
        return streamId < next;
    }

    /**
     * Called to process a {@link OneRttPacket} after it has been successfully decrypted.
     *
     * @param quicPacket the Quic packet
     * @throws IllegalArgumentException if the {@code quicPacket} isn't a 1-RTT packet
     * @throws NullPointerException     if {@code quicPacket} is null
     */
    protected void processOneRTTPacket(QuicPacket quicPacket) {
        processOneRTTPacket(pathManager.receive(peerAddress(), 0), quicPacket);
    }

    /**
     * Called to process an {@link InitialPacket} after it has been decrypted.
     *
     * @param quicPacket the Quic packet
     * @throws IllegalArgumentException if {@code quicPacket} isn't a INITIAL packet
     * @throws NullPointerException     if {@code quicPacket} is null
     */
    protected void processInitialPacket(QuicPacket quicPacket) {
        Objects.requireNonNull(quicPacket);
        if (quicPacket.packetType() != PacketType.INITIAL) {
            throw new IllegalArgumentException("Not a INITIAL packet: " + quicPacket.packetType().text());
        }
        try {
            if (quicPacket instanceof InitialPacket initial) {
                MaxInitialTimer initialTimer = this.maxInitialTimer;
                if (initialTimer != null) {
                    // will be a no-op after the first call;
                    initialTimer.initialPacketReceived();
                    // we no longer need the timer
                    this.maxInitialTimer = null;
                }
                updatePeerConnectionId(initial);
                processInitialPacketPayload(initial);
                // received initial packet from server - we won't need to replay anything now
                handshakeFlow.localInitial.discardReplayData();
                continueHandshake();
                if (quicTLSEngine.handshakeState() == HandshakeState.NEED_RECV_CRYPTO
                        && quicTLSEngine.keysAvailable(KeySpace.HANDSHAKE)) {
                    // arm the anti-deadlock PTO timer
                    packetSpaces.handshake.runTransmitter();
                }
            } else {
                throw new IllegalStateException("Bad packet type: " + quicPacket.packetType().text());
            }
        } catch (QuicSilentTlsRejection e) {
            terminator.terminate(QuicCloseCommand.silent(e,
                                                         "QUIC TLS server-name policy rejected ClientHello"));
        } catch (RuntimeException failure) {
            onProcessingError(quicPacket, failure);
        }
    }

    /**
     * Updates peer connection-ID state from an Initial packet.
     *
     * @param initial Initial packet carrying the peer source connection ID
     * @throws QuicTransportException if the peer connection ID is invalid for the current state
     */
    protected void updatePeerConnectionId(InitialPacket initial) throws QuicTransportException {
        this.incomingInitialPacketSourceId = initial.sourceId();
        this.peerConnIdManager.finalizeHandshakePeerConnId(initial);
        this.pathManager.bindInitialPath();
    }

    /**
     * Process the payload of an incoming initial packet.
     *
     * @param packet the incoming packet
     * @return the total number of bytes consumed
     * @throws QuicTransportException if the payload contains an invalid QUIC frame
     * @throws IllegalStateException if decoded frame accounting is inconsistent
     */
    protected int processInitialPacketPayload(InitialPacket packet) throws QuicTransportException {
        int provided = 0;
        int total = 0;
        int initialPayloadSize = packet.payloadSize();
        if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
            logDebug("Processing initial packet pn:%s payload:%s",
                      packet.packetNumber(), initialPayloadSize);
        }
        for (var frame : packet.frames()) {
            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                logDebug("received INITIAL frame %s", frame);
            }
            int size = frame.size();
            total += size;
            switch (frame) {
            case AckFrame ack -> {
                incomingInitialFrame(ack);
            }
            case CryptoFrame crypto -> {
                provided = incomingInitialFrame(crypto);
            }
            case PaddingFrame paddingFrame -> {
                incomingInitialFrame(paddingFrame);
            }
            case PingFrame ping -> {
                incomingInitialFrame(ping);
            }
            case ConnectionCloseFrame close -> {
                incomingInitialFrame(close);
            }
            default -> {
                if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                    logDebug("Received invalid frame: " + frame);
                }
                throw new QuicTransportException("Invalid frame in this packet type",
                                                 KeySpace.INITIAL, frame.typeField(),
                                                 PROTOCOL_VIOLATION);
            }
            }
        }
        if (total != initialPayloadSize) {
            throw new IllegalStateException(
                    "Initial payload wasn't fully consumed: %s read, of which %s crypto, from %s size"
                            .formatted(total, provided, initialPayloadSize));
        }
        return total;
    }

    /**
     * Process the payload of an incoming handshake packet.
     *
     * @param packet the incoming packet
     * @return the total number of bytes consumed
     * @throws QuicTransportException if the payload contains an invalid QUIC frame
     * @throws IllegalStateException if decoded frame accounting is inconsistent
     */
    protected int processHandshakePacketPayload(HandshakePacket packet) throws QuicTransportException {
        int provided = 0;
        int total = 0;
        int payloadSize = packet.payloadSize();
        for (var frame : packet.frames()) {
            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                logDebug("received HANDSHAKE frame %s", frame);
            }
            int size = frame.size();
            total += size;
            switch (frame) {
            case AckFrame ack -> {
                incomingHandshakeFrame(ack);
            }
            case CryptoFrame crypto -> {
                provided = incomingHandshakeFrame(crypto);
            }
            case PaddingFrame paddingFrame -> {
                incomingHandshakeFrame(paddingFrame);
            }
            case PingFrame ping -> {
                incomingHandshakeFrame(ping);
            }
            case ConnectionCloseFrame close -> {
                incomingHandshakeFrame(close);
            }
            default -> {
                throw new QuicTransportException("Invalid frame in this packet type",
                                                 KeySpace.HANDSHAKE, frame.typeField(),
                                                 PROTOCOL_VIOLATION);
            }
            }
        }
        if (total != payloadSize) {
            throw new IllegalStateException(
                    "Handshake payload wasn't fully consumed: %s read, of which %s crypto, from %s size"
                            .formatted(total, provided, payloadSize));
        }
        return total;
    }

    /**
     * Called to process an {@link HandshakePacket} after it has been decrypted.
     *
     * @param quicPacket the handshake quic packet
     * @throws IllegalArgumentException if {@code quicPacket} is not a HANDSHAKE packet
     * @throws NullPointerException     if {@code quicPacket} is null
     */
    protected void processHandshakePacket(QuicPacket quicPacket) {
        Objects.requireNonNull(quicPacket);
        if (quicPacket.packetType() != PacketType.HANDSHAKE) {
            throw new IllegalArgumentException("Not a HANDSHAKE packet: " + quicPacket.packetType().text());
        }
        var handshake = this.handshakeFlow.handshakeCF();
        if (handshake.isDone() && LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
            logDebug("Receiving HandshakePacket(%s) after handshake is done: %s",
                      quicPacket.packetNumber(), quicPacket.frames());
        }
        try {
            if (quicPacket instanceof HandshakePacket hs) {
                processHandshakePacketPayload(hs);
                if (!isClientConnection() && !packetSpaces.initial.isClosed()) {
                    logDebug("first Handshake packet processed by server, initiating close of INITIAL packet space");
                    packetSpaces.initial.close();
                }
                continueHandshake();
            } else {
                throw new IllegalStateException("Bad packet type: " + quicPacket.packetType().text());
            }
        } catch (RuntimeException failure) {
            onProcessingError(quicPacket, failure);
        }
    }

    /**
     * Called to process a {@link RetryPacket} after it has been decrypted.
     *
     * @param quicPacket the retry quic packet
     * @throws IllegalArgumentException if {@code quicPacket} is not a RETRY packet
     * @throws NullPointerException     if {@code quicPacket} is null
     */
    protected void processRetryPacket(QuicPacket quicPacket) {
        Objects.requireNonNull(quicPacket);
        if (quicPacket.packetType() != PacketType.RETRY) {
            throw new IllegalArgumentException("Not a RETRY packet: " + quicPacket.packetType().text());
        }
        try {
            if (!(quicPacket instanceof RetryPacket rt)) {
                throw new IllegalStateException("Bad packet type: " + quicPacket.packetType().text());
            }
            if (rt.retryToken().length == 0) {
                if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                    logDebug("Invalid retry, empty token");
                }
                return;
            }
            QuicConnectionId currentPeerConnId = peerConnectionId();
            if (rt.sourceId().equals(currentPeerConnId)) {
                if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                    logDebug("Invalid retry, same connection ID");
                }
                return;
            }
            if (this.peerConnIdManager.retryConnId() != null) {
                if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                    logDebug("Ignoring retry, already got one");
                }
                return;
            }
            // ignore retry if we already received initial packets
            if (incomingInitialPacketSourceId != null) {
                if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                    logDebug("Already received initial, ignoring retry");
                }
                return;
            }
            int version = rt.version();
            QuicVersion retryVersion = QuicVersion.of(version).orElse(null);
            if (retryVersion == null) {
                if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                    logDebug("Ignoring retry packet with unknown version 0x"
                            + Integer.toHexString(version));
                }
                // ignore the packet
                return;
            }
            QuicVersion originalVersion = this.quicVersion; // the original version used to establish the connection
            if (originalVersion != retryVersion) {
                if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                    logDebug("Ignoring retry packet with version 0x"
                            + Integer.toHexString(version)
                            + " since it doesn't match the original version 0x"
                            + Integer.toHexString(originalVersion.versionNumber()));
                }
                // ignore the packet
                return;
            }
            ReentrantLock tl = packetSpaces.initial.transmitLock();
            tl.lock();
            try {
                initialToken = rt.retryToken();
                QuicConnectionId retryConnId = rt.sourceId();
                this.peerConnIdManager.retryConnId(retryConnId);
                quicTLSEngine.deriveInitialKeys(originalVersion, retryConnId.bufferData());
                this.packetSpace(PacketNumberSpace.INITIAL).retry();
                handshakeFlow.localInitial.replayData();
            } finally {
                tl.unlock();
            }
            packetSpaces.initial.runTransmitter();
        } catch (RuntimeException failure) {
            onProcessingError(quicPacket, failure);
        }
    }

    /**
     * Called to process a received {@link VersionNegotiationPacket}.
     *
     * @param quicPacket the {@link VersionNegotiationPacket}
     * @throws IllegalArgumentException if {@code quicPacket} is not a {@link PacketType#VERSIONS}
     *                                 packet
     * @throws NullPointerException     if {@code quicPacket} is null
     */
    protected void processVersionNegotiationPacket(QuicPacket quicPacket) {
        Objects.requireNonNull(quicPacket);
        if (quicPacket.packetType() != PacketType.VERSIONS) {
            throw new IllegalArgumentException("Not a VERSIONS packet type: " + quicPacket.packetType().text());
        }
        // servers aren't expected to receive version negotiation packet
        if (!this.isClientConnection()) {
            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                logDebug("(server) ignoring version negotiation packet");
            }
            return;
        }
        try {
            var handshakeCF = this.handshakeFlow.handshakeCF();
            // we must ignore version negotiation if we already had a successful exchange
            var versionCompatible = this.versionCompatible;
            if (versionCompatible || handshakeCF.isDone()) {
                if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                    logDebug("ignoring version negotiation packet (neg: %s, state: %s, hs: %s)",
                              versionCompatible, stateHandle, handshakeCF);
                }
                return;
            }
            // we shouldn't receive unsolicited version negotiation packets
            if (!(quicPacket instanceof VersionNegotiationPacket negotiate)) {
                if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                    logDebug("Bad packet type %s for %s",
                              quicPacket.getClass().getName(), quicPacket);
                }
                return;
            }
            if (!negotiate.sourceId().equals(originalServerConnId())) {
                if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                    logDebug("Received version negotiation packet with wrong connection id");
                    logDebug("expected source id: %s, received source id: %s",
                              originalServerConnId(), negotiate.sourceId());
                    logDebug("ignoring version negotiation packet (wrong id)");
                }
                return;
            }
            int[] serverSupportedVersions = negotiate.supportedVersions();
            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                logDebug("Received version negotiation packet with supported=%s",
                          Arrays.toString(serverSupportedVersions));
            }
            QuicVersion negotiatedVersion = null;
            for (int v : serverSupportedVersions) {
                QuicVersion serverVersion = QuicVersion.of(v).orElse(null);
                if (serverVersion == null) {
                    if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                        logDebug("Ignoring unrecognized server supported version %d", v);
                    }
                    continue;
                }
                if (serverVersion == this.quicVersion) {
                    // RFC-9000, section 6.2:
                    // A client MUST discard a Version Negotiation packet that lists
                    // the QUIC version selected by the client.
                    if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                        logDebug("ignoring version negotiation packet since the version"
                                + " %d matches the current quic version selected by the client", v);
                    }
                    return;
                }
                // check if the current quic client is enabled for this version
                if (!quicInstance().isVersionAvailable(serverVersion)) {
                    if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                        logDebug("Ignoring server supported version %d because the "
                                + "client isn't enabled for it", v);
                    }
                    continue;
                }
                if (negotiatedVersion == null) {
                    negotiatedVersion = serverVersion;
                    if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                        logDebug("Accepting server supported version %d",
                                  serverVersion.versionNumber());
                    }
                } else {
                    // currently all versions are equal
                    if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                        logDebug("Skipping server supported version %d",
                                  serverVersion.versionNumber());
                    }
                }
            }
            // at this point if negotiatedVersion is null, then it implies that none of the server
            // supported versions are supported by the client. The spec expects us to abandon the
            // current connection attempt in such cases (RFC-9000, section 6.2)
            if (negotiatedVersion == null) {
                String msg = "No support for any of the QUIC versions being negotiated: "
                        + Arrays.toString(serverSupportedVersions);
                if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                    logDebug("No version could be negotiated: %s", msg);
                }
                terminator.terminate(transport(new QuicConnectionException(msg)));
                return;
            }
            // a different version than the current client chosen version has been negotiated,
            // switch the client connection to use this negotiated version
            ReentrantLock tl = packetSpaces.initial.transmitLock();
            tl.lock();
            try {
                if (switchVersion(negotiatedVersion)) {
                    initialToken = quicInstance()
                            .initialTokenFor(peerAddress(), negotiatedVersion)
                            .orElse(BufferData.EMPTY_BYTES);
                    BufferData quicInitialParameters = buildInitialParameters();
                    quicTLSEngine.localQuicTransportParameters(quicInitialParameters);
                    quicTLSEngine.restartHandshake();
                    handshakeFlow.localInitial.reset();
                    continueHandshake();
                    packetSpaces.initial.runTransmitter();
                    this.versionCompatible = true;
                    processedVersionsPacket = true;
                }
            } finally {
                tl.unlock();
            }
        } catch (Throwable t) {
            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                logDebug("Failed to handle packet", t);
            }
        }

    }

    /**
     * Switch to a new version after receiving a version negotiation
     * packet. This method checks that no version was previously
     * negotiated, in which case it switches the connection to the
     * new version and returns true.
     * Otherwise, it returns false.
     *
     * @param negotiated the new version that was negotiated
     * @return true if switching to the new version was successful
     */
    protected boolean switchVersion(QuicVersion negotiated) {
        try {
            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                logDebug("switch to negotiated version %s", negotiated);
            }
            this.quicVersion = negotiated;
            this.decoder = QuicPacketDecoder.of(negotiated);
            this.encoder = QuicPacketEncoder.of(negotiated);
            this.packetSpace(PacketNumberSpace.INITIAL).versionChanged();
            // regenerate the INITIAL keys using the new negotiated Quic version
            this.quicTLSEngine.deriveInitialKeys(negotiated, originalServerConnId().bufferData());
            return true;
        } catch (Throwable t) {
            terminator.terminate(transport(t));
            throw new IllegalStateException("Failed to switch QUIC version", t);
        }
    }

    /**
     * Mark the version as negotiated. No further version changes are possible.
     *
     * @param packetVersion the packet version
     */
    protected void markVersionNegotiated(int packetVersion) {
        int version = this.quicVersion.versionNumber();
        if (!versionNegotiated) {
            if (VERSION_NEGOTIATED.compareAndSet(this, false, true)) {
                // negotiated version finalized
                quicTLSEngine.versionNegotiated(QuicVersion.of(version).get());
            }
        }
    }

    /**
     * Returns a boolean value telling whether the datagram in the
     * protection record is complete.
     * The datagram is complete when no other packet need to be coalesced
     * in the datagram.
     * If a datagram is complete, it is ready to be sent.
     *
     * @param protectionRecord the protection record
     * @return a boolean value telling whether the datagram in the
     *         protection record is complete
     */
    protected boolean isDatagramComplete(ProtectionRecord protectionRecord) {
        return protectionRecord.datagram.remaining() == 0
                || protectionRecord.flags == ProtectionRecord.SINGLE_PACKET
                || (protectionRecord.flags & ProtectionRecord.LAST_PACKET) != 0
                || (protectionRecord.flags & ProtectionRecord.COALESCED) == 0;
    }

    /**
     * This method is called when the handshake is successfully completed.
     *
     * @param result the result of the handshake
     */
    protected void onHandshakeCompletion(HandshakeState result) {
        if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
            logDebug("Quic handshake successfully completed with %s(%s)",
                      quicTLSEngine.applicationProtocol().orElse("<no ALPN>"), peerAddress());
        }
        // now that the handshake has successfully completed, start the
        // idle timeout management for this connection
        this.idleTimeoutManager.start();
    }

    /**
     * Returns handshake state shared across packet spaces and TLS processing.
     *
     * @return handshake flow helper
     */
    protected HandshakeFlow handshakeFlow() {
        return handshakeFlow;
    }

    /**
     * Returns the shared successful-handshake transition, including idle-timeout startup.
     *
     * @return successful-handshake transition
     */
    protected final CompletableFuture<Void> successfulHandshakeCF() {
        return successfulHandshakeCF;
    }

    /**
     * Arms the client-side timer that bounds waiting for the first Initial response.
     */
    protected void startInitialTimer() {
        if (!isClientConnection()) {
            return;
        }
        Duration responseTimeout = Objects.requireNonNull(initialResponseTimeout, "initialResponseTimeout");
        MaxInitialTimer initialTimer = maxInitialTimer;
        if (initialTimer == null) {
            Deadline maxInitialDeadline = null;
            maxInitialTimerLock.lock();
            try {
                initialTimer = maxInitialTimer;
                if (initialTimer == null) {
                    Deadline now = TimeSource.now();
                    maxInitialDeadline = now.plus(responseTimeout);
                    initialTimer = new MaxInitialTimer(this.endpoint().timer(), maxInitialDeadline, responseTimeout);
                    maxInitialTimer = initialTimer;
                }
            } finally {
                maxInitialTimerLock.unlock();
            }
            if (maxInitialDeadline != null) {
                if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                    logDebug("Arming QUIC initial timer for %s", responseTimeout);
                }
                initialTimer.timerQueue.reschedule(initialTimer, maxInitialDeadline);
            }
        }
    }

    /**
     * Allocate a {@link ByteBuffer} that can be used to encrypt the
     * given packet.
     *
     * @param packet the packet to encrypt
     * @return a new {@link ByteBuffer} with sufficient space to encrypt
     *        the given packet.
     */
    protected ByteBuffer allocateDatagramForEncryption(QuicPacket packet) {
        int size = packet.size();
        if (packet.hasLength()) { // packet can be coalesced
            size = Math.max(size, maxDatagramSize());
        }
        if (size > maxDatagramSize()) {

            if (LOGGER.isLoggable(System.Logger.Level.ERROR)) {
                var error = new AssertionError("%s: Size too big: %s > %s".formatted(
                        logTag(),
                        size, maxDatagramSize()));
                log(LOGGER, System.Logger.Level.ERROR, "Packet too big: %s", error, packet.prettyPrint());
            }
            // Revisit: if we implement Path MTU detection, then the max datagram size
            //       may evolve, increasing or decreasing as the path change.
            //       In which case - we may want to tune this, down and only
            //       log an error or warning?
            String errMsg = "Failed to encode packet, too big: " + size;
            terminator.terminate(transport(PROTOCOL_VIOLATION, errMsg));
            throw terminator.termination().closeCause();
        }
        return outgoingByteBuffer(size);
    }

    /**
     * Retrieves cryptographic messages from TLS engine, enqueues them for sending
     * and starts the transmitter.
     */
    protected void continueHandshake() {
        handshakeScheduler.runOrSchedule();
    }

    /**
     * Initializes server-side handshake state once the client's Initial is available.
     *
     * @param clientSelectedPeerId destination connection ID chosen by the client
     * @throws IllegalStateException if local transport parameters cannot be prepared for TLS
     */
    protected final void initializeServerHandshake(QuicConnectionId clientSelectedPeerId) {
        Objects.requireNonNull(clientSelectedPeerId, "clientSelectedPeerId");
        this.peerConnIdManager.originalServerConnId(clientSelectedPeerId);
        handshakeFlow.markHandshakeStart();
        quicTLSEngine.deriveInitialKeys(quicVersion, clientSelectedPeerId.bufferData());
        markVersionNegotiated(quicVersion.versionNumber());
        BufferData quicInitialParameters = buildInitialParameters();
        quicTLSEngine.localQuicTransportParameters(quicInitialParameters);
    }

    /**
     * Advances handshake processing and fails the handshake futures on error.
     */
    protected void continueHandshake0() {
        try {
            continueHandshake1();
        } catch (QuicSilentTlsRejection failure) {
            terminator.terminate(QuicCloseCommand.silent(failure,
                                                         "QUIC TLS server-name policy rejected ClientHello"));
        } catch (RuntimeException failure) {
            if (failure instanceof QuicTransportException transportFailure
                    && transportFailure.errorCode() != QuicTransportErrors.INTERNAL_ERROR.code()) {
                String detail = transportFailure.getMessage();
                log(LOGGER,
                    System.Logger.Level.DEBUG,
                    "Closing connection after peer handshake protocol violation: %s",
                    detail == null ? transportFailure.getClass().getSimpleName() : detail);
                log(LOGGER,
                    System.Logger.Level.TRACE,
                    "Peer handshake protocol violation",
                    transportFailure);
            } else {
                log(LOGGER,
                    System.Logger.Level.ERROR,
                    "Local failure while advancing QUIC handshake",
                    failure);
            }
            terminator.terminate(QuicCloseCommand.transport(failure));
        }
    }

    /**
     * Decodes and validates transport parameters received from TLS.
     *
     * @param byteBuffer encoded transport parameters
     * @throws QuicTransportException if the parameters are invalid for this connection
     */
    protected void consumeQuicParameters(ByteBuffer byteBuffer) throws QuicTransportException {
        QuicTransportParameters params = QuicTransportParameters.decode(byteBuffer);
        if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
            logDebug("Received peer Quic transport params: %s", params);
        }
        if (quicConfig.unsafeRawData() && LOGGER.isLoggable(System.Logger.Level.TRACE)) {
            log(LOGGER,
                System.Logger.Level.TRACE,
                "UNSAFE raw peer Quic transport parameter values: %s",
                params.toStringWithValues());
        }
        QuicConnectionId retryConnId = this.peerConnIdManager.retryConnId();
        validatePeerTransportParameters(params, retryConnId);
        if (params.isPresent(active_connection_id_limit)) {
            long limit = params.intParameter(active_connection_id_limit);
            if (limit < 2) {
                throw new QuicTransportException(
                        "Invalid active_connection_id_limit " + limit,
                        0, QuicTransportErrors.TRANSPORT_PARAMETER_ERROR);
            }
        }
        if (params.isPresent(ParameterId.stateless_reset_token)) {
            byte[] statelessResetToken = params.parameter(ParameterId.stateless_reset_token)
                    .orElseThrow(() -> new IllegalStateException(
                            "stateless_reset_token was reported present but its value is absent"));
            if (statelessResetToken.length != RESET_TOKEN_LENGTH) {
                // RFC states 16 bytes for stateless token
                throw new QuicTransportException(
                        "Invalid stateless reset token length " + statelessResetToken.length,
                        0, QuicTransportErrors.TRANSPORT_PARAMETER_ERROR);
            }
        }
        Optional<VersionInformation> versionInformation =
                params.versionInformationParameter(version_information);
        if (versionInformation.isPresent()) {
            VersionInformation vi = versionInformation.orElseThrow();
            // RFC 9368 requires this membership only for client-sent Version Information.
            if (!isClientConnection()) {
                boolean chosenVersionAvailable = false;
                for (int availableVersion : vi.availableVersions()) {
                    if (availableVersion == vi.chosenVersion()) {
                        chosenVersionAvailable = true;
                        break;
                    }
                }
                if (!chosenVersionAvailable) {
                    throw new QuicTransportException(
                            "[version_information] Chosen Version is not included in available versions",
                            0, QuicTransportErrors.TRANSPORT_PARAMETER_ERROR);
                }
            }
            if (vi.chosenVersion() != quicVersion().versionNumber()) {
                throw new QuicTransportException(
                        "[version_information] Chosen Version does not match version in use",
                        0, QuicTransportErrors.VERSION_NEGOTIATION_ERROR);
            }
            if (processedVersionsPacket) {
                if (vi.availableVersions().length == 0) {
                    throw new QuicTransportException(
                            "[version_information] available versions empty",
                            0, QuicTransportErrors.VERSION_NEGOTIATION_ERROR);
                }
                if (Arrays.stream(vi.availableVersions())
                        .anyMatch(i -> i == originalVersion.versionNumber())) {
                    throw new QuicTransportException(
                            "[version_information] original version was available",
                            0, QuicTransportErrors.VERSION_NEGOTIATION_ERROR);
                }
            }
        } else {
            if (processedVersionsPacket && quicVersion != QuicVersion.QUIC_V1) {
                throw new QuicTransportException(
                        "version_information parameter absent",
                        0, QuicTransportErrors.VERSION_NEGOTIATION_ERROR);
            }
        }
        handleIncomingPeerTransportParams(params);
        // params.intParameter(ParameterId.max_idle_timeout, TimeUnit.SECONDS.toMillis(30));
        // params.parameter(ParameterId.stateless_reset_token, ...); // no token
        // params.intParameter(ParameterId.initial_max_data, DEFAULT_INITIAL_MAX_DATA);
        // params.intParameter(ParameterId.initial_max_stream_data_bidi_local, DEFAULT_INITIAL_STREAM_MAX_DATA);
        // params.intParameter(ParameterId.initial_max_stream_data_bidi_remote, DEFAULT_INITIAL_STREAM_MAX_DATA);
        // params.intParameter(ParameterId.initial_max_stream_data_uni, DEFAULT_INITIAL_STREAM_MAX_DATA);
        // params.intParameter(ParameterId.initial_max_streams_bidi, DEFAULT_MAX_STREAMS);
        // params.intParameter(ParameterId.initial_max_streams_uni, DEFAULT_MAX_STREAMS);
        // params.intParameter(ParameterId.ack_delay_exponent, 3); // unit 2^3 microseconds
        // params.intParameter(ParameterId.max_ack_delay, 25); //25 millis
        // params.preferredAddressParameter(ParameterId.preferred_address, ...);
        // params.intParameter(ParameterId.active_connection_id_limit, 2);
    }

    /**
     * Validates peer transport parameters according to the current connection role.
     *
     * @param params      decoded peer transport parameters
     * @param retryConnId retry connection ID, if a Retry exchange occurred
     * @throws QuicTransportException if the parameters violate QUIC requirements
     */
    protected void validatePeerTransportParameters(QuicTransportParameters params,
                                                   QuicConnectionId retryConnId)
            throws QuicTransportException {
        if (isClientConnection()) {
            validateServerTransportParameters(params, retryConnId);
        } else {
            validateClientTransportParameters(params, retryConnId);
        }
    }

    /**
     * Returns the number of (active) connection ids that this endpoint is willing
     * to accept from the peer for a given connection.
     *
     * @return the number of (active) connection ids that this endpoint is willing
     *         to accept from the peer for a given connection
     */
    protected long localActiveConnectionIdLimit() {
        // currently we don't accept anything more than 2 (the RFC defined default minimum)
        return runtimeConfig.transportParameters().activeConnectionIdLimit();
    }

    /**
     * Builds the local transport-parameter block for the Initial flight.
     *
     * @return encoded transport parameters ready for TLS
     */
    protected BufferData buildInitialParameters() {
        QuicTransportParameters params = quicInstance.transportParameters();
        putIntParameterIfAbsent(params, active_connection_id_limit, this::localActiveConnectionIdLimit);
        long idleTimeoutMillis = quicConfig.idleTimeout().toMillis();
        putIntParameterIfAbsent(params, max_idle_timeout, () -> idleTimeoutMillis);
        putIntParameterIfAbsent(params, max_udp_payload_size, () -> {
            return (long) this.endpoint.maxUdpPayloadSize();
        });
        putIntParameterIfAbsent(params, initial_max_data, () -> defaultInitialMaxData);
        putIntParameterIfAbsent(params, initial_max_stream_data_bidi_local,
                            () -> defaultInitialStreamMaxData);
        putIntParameterIfAbsent(params, initial_max_stream_data_uni, () -> defaultInitialStreamMaxData);
        putIntParameterIfAbsent(params, initial_max_stream_data_bidi_remote, () -> defaultInitialStreamMaxData);
        putIntParameterIfAbsent(params, initial_max_streams_uni, () -> defaultMaxUniStreams);
        putIntParameterIfAbsent(params, initial_max_streams_bidi, () -> defaultMaxBidiStreams);
        if (!params.isPresent(initial_source_connection_id)) {
            params.parameter(initial_source_connection_id, connectionId.bytes());
        }
        if (!params.isPresent(version_information)) {
            VersionInformation vi =
                    QuicTransportParameters.buildVersionInformation(quicVersion,
                                                                    quicInstance().availableVersions());
            params.versionInformationParameter(version_information, vi);
        }
        customizeInitialParameters(params);
        // params.intParameter(ParameterId.ack_delay_exponent, 3); // unit 2^3 microseconds
        // params.intParameter(ParameterId.max_ack_delay, 25); //25 millis
        BufferData buf = BufferData.create(params.size());
        params.encode(buf);
        if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
            logDebug("local transport params: %s", params);
        }
        if (quicConfig.unsafeRawData() && LOGGER.isLoggable(System.Logger.Level.TRACE)) {
            log(LOGGER,
                System.Logger.Level.TRACE,
                "UNSAFE raw local Quic transport parameter values: %s",
                params.toStringWithValues());
        }
        newLocalTransportParameters(params);
        return buf;
    }

    /**
     * Hook for subclasses to customize local transport parameters before encoding.
     *
     * @param params mutable transport parameters to customize
     */
    protected void customizeInitialParameters(QuicTransportParameters params) {
    }

    /**
     * Publishes newly generated local transport parameters to connection subsystems.
     *
     * @param params local transport parameters
     */
    protected void newLocalTransportParameters(QuicTransportParameters params) {
        localTransportParameters = params;
        oneRttRcvQueue.newLocalParameters(params);
        streams.newLocalTransportParameters(params);
        long idleTimeout = params.intParameterOrDefault(max_idle_timeout, 0);
        this.idleTimeoutManager.localIdleTimeout(idleTimeout);
    }

    /**
     * Called when new quic transport parameters are available from the peer.
     *
     * @param params the peer's new quic transport parameter
     */
    protected void handleIncomingPeerTransportParams(QuicTransportParameters params) {
        peerTransportParameters = params;
        this.idleTimeoutManager.peerIdleTimeout(params.intParameter(max_idle_timeout));
        // The peer value can use the full QUIC variable-length integer range. Saturate it before narrowing; the
        // effective datagram size is also capped by the local path MTU in maxDatagramSize().
        maxPeerAdvertisedPayloadSize =
                (int) Math.min(params.intParameter(max_udp_payload_size), Integer.MAX_VALUE);
        congestionController.updateMaxDatagramSize(maxDatagramSize());
        if (params.isPresent(ParameterId.initial_max_data)) {
            oneRttSndQueue.updateMaxData(params.intParameter(ParameterId.initial_max_data), true);
        }
        streams.newPeerTransportParameters(params);
        packetSpaces.app().updatePeerTransportParameters(
                params.intParameter(ParameterId.max_ack_delay),
                params.intParameter(ParameterId.ack_delay_exponent));
        // param value for this param is already validated outside of this method, so we just
        // set the value without any validations
        this.peerActiveConnIdsLimit = params.intParameter(active_connection_id_limit);
        if (params.isPresent(ParameterId.stateless_reset_token)) {
            // the stateless reset token for the handshake connection id
            byte[] statelessResetToken = params.parameter(ParameterId.stateless_reset_token)
                    .orElseThrow(() -> new IllegalStateException(
                            "stateless_reset_token was reported present but its value is absent"));
            // register with peer connid manager
            this.peerConnIdManager.handshakeStatelessResetToken(statelessResetToken);
        }
        if (params.isPresent(ParameterId.preferred_address)) {
            byte[] val = params.parameter(ParameterId.preferred_address)
                    .orElseThrow(() -> new IllegalStateException(
                            "preferred_address was reported present but its value is absent"));
            ByteBuffer preferredConnId = QuicTransportParameters.preferredConnectionId(val);
            byte[] preferredStatelessResetToken = QuicTransportParameters.preferredStatelessResetToken(val);
            this.peerConnIdManager.handlePreferredAddress(preferredConnId, preferredStatelessResetToken);
        }
        if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
            logDebug("incoming peer parameters handled");
        }
    }

    /**
     * Processes an ACK frame received in Initial packet space.
     *
     * @param frame received ACK frame
     * @throws QuicTransportException if ACK processing fails
     */
    protected void incomingInitialFrame(AckFrame frame) throws QuicTransportException {
        packetSpaces.initial.processAckFrame(frame);
        if (!handshakeFlow.handshakeReachedPeerCF.isDone()) {
            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                logDebug("completing handshakeStartedCF normally");
            }
            handshakeFlow.handshakeReachedPeerCF.complete(null);
        }
    }

    /**
     * Processes a CRYPTO frame received in Initial packet space.
     *
     * @param frame received CRYPTO frame
     * @return number of bytes provided to the TLS engine
     * @throws QuicTransportException if buffered crypto data exceeds the allowed capacity
     */
    protected int incomingInitialFrame(CryptoFrame frame) throws QuicTransportException {
        // make sure to provide the frames in order, and
        // buffer them if at the wrong offset
        if (!handshakeFlow.handshakeReachedPeerCF.isDone()) {
            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                logDebug("completing handshakeStartedCF normally");
            }
            handshakeFlow.handshakeReachedPeerCF.complete(null);
        }
        return processIncomingCryptoFrame(frame, KeySpace.INITIAL, handshakeFlow.peerInitial);
    }

    /**
     * Processes a PADDING frame received in Initial packet space.
     *
     * @param frame received PADDING frame
     * @throws QuicTransportException never thrown by the current implementation
     */
    protected void incomingInitialFrame(PaddingFrame frame) throws QuicTransportException {
        // nothing to do
    }

    /**
     * Processes a PING frame received in Initial packet space.
     *
     * @param frame received PING frame
     * @throws QuicTransportException never thrown by the current implementation
     */
    protected void incomingInitialFrame(PingFrame frame) throws QuicTransportException {
        // nothing to do
    }

    /**
     * Processes a CONNECTION_CLOSE frame received in Initial packet space.
     *
     * @param frame received CONNECTION_CLOSE frame
     * @throws QuicTransportException if termination handling fails
     */
    protected void incomingInitialFrame(ConnectionCloseFrame frame)
            throws QuicTransportException {
        terminator.incomingConnectionCloseFrame(KeySpace.INITIAL, frame);
    }

    /**
     * Processes an ACK frame received in Handshake packet space.
     *
     * @param frame received ACK frame
     * @throws QuicTransportException if ACK processing fails
     */
    protected void incomingHandshakeFrame(AckFrame frame) throws QuicTransportException {
        packetSpaces.handshake.processAckFrame(frame);
    }

    /**
     * Processes a CRYPTO frame received in Handshake packet space.
     *
     * @param frame received CRYPTO frame
     * @return number of bytes provided to the TLS engine
     * @throws QuicTransportException if buffered crypto data exceeds the allowed capacity
     */
    protected int incomingHandshakeFrame(CryptoFrame frame) throws QuicTransportException {
        return processIncomingCryptoFrame(frame, KeySpace.HANDSHAKE, handshakeFlow.peerHandshake);
    }

    /**
     * Processes a PADDING frame received in Handshake packet space.
     *
     * @param frame received PADDING frame
     * @throws QuicTransportException never thrown by the current implementation
     */
    protected void incomingHandshakeFrame(PaddingFrame frame) throws QuicTransportException {
        // nothing to do
    }

    /**
     * Processes a PING frame received in Handshake packet space.
     *
     * @param frame received PING frame
     * @throws QuicTransportException never thrown by the current implementation
     */
    protected void incomingHandshakeFrame(PingFrame frame) throws QuicTransportException {
        // nothing to do
    }

    /**
     * Processes a CONNECTION_CLOSE frame received in Handshake packet space.
     *
     * @param frame received CONNECTION_CLOSE frame
     * @throws QuicTransportException if termination handling fails
     */
    protected void incomingHandshakeFrame(ConnectionCloseFrame frame)
            throws QuicTransportException {
        terminator.incomingConnectionCloseFrame(KeySpace.HANDSHAKE, frame);
    }

    /**
     * Processes an ACK frame received in application packet space.
     *
     * @param ackFrame received ACK frame
     * @throws QuicTransportException if ACK processing fails
     */
    protected void incoming1RTTFrame(AckFrame ackFrame) throws QuicTransportException {
        packetSpaces.app.processAckFrame(ackFrame);
    }

    /**
     * Processes a STREAM frame received in application packet space.
     *
     * @param frame received STREAM frame
     * @throws QuicTransportException if stream-state validation fails
     */
    protected void incoming1RTTFrame(StreamFrame frame) throws QuicTransportException {
        QuicReceiverStream stream = receivingStream(frame);
        if (stream != null) {
            streams.processIncomingFrame(stream, frame);
        }
    }

    /**
     * Processes a CRYPTO frame received in application packet space.
     *
     * @param frame received CRYPTO frame
     * @throws QuicTransportException if buffered crypto data exceeds the allowed capacity
     */
    protected void incoming1RTTFrame(CryptoFrame frame) throws QuicTransportException {
        processIncomingCryptoFrame(frame, KeySpace.ONE_RTT, peerCryptoFlow);
    }

    /**
     * Processes a RESET_STREAM frame received in application packet space.
     *
     * @param frame received RESET_STREAM frame
     * @throws QuicTransportException if stream-state validation fails
     */
    protected void incoming1RTTFrame(ResetStreamFrame frame) throws QuicTransportException {
        long streamId = frame.streamId();
        QuicReceiverStream stream = receivingStream(streamId, frame.typeField());
        if (stream != null) {
            streams.processIncomingFrame(stream, frame);
        }
    }

    /**
     * Processes a STREAM_DATA_BLOCKED frame received in application packet space.
     *
     * @param frame received STREAM_DATA_BLOCKED frame
     * @throws QuicTransportException if stream-state validation fails
     */
    protected void incoming1RTTFrame(StreamDataBlockedFrame frame)
            throws QuicTransportException {
        QuicReceiverStream stream = receivingStream(frame.streamId(), frame.typeField());
        if (stream != null) {
            streams.processIncomingFrame(stream, frame);
        }
    }

    /**
     * Processes a DATA_BLOCKED frame received in application packet space.
     *
     * @param frame received DATA_BLOCKED frame
     * @throws QuicTransportException reserved for future validation failures
     */
    protected void incoming1RTTFrame(DataBlockedFrame frame) throws QuicTransportException {
        // DATA_BLOCKED is only advisory. The receiver sends MAX_DATA when its
        // connection window advances, so no immediate action is required here.
    }

    /**
     * Processes a STREAMS_BLOCKED frame received in application packet space.
     *
     * @param frame received STREAMS_BLOCKED frame
     * @throws QuicTransportException if the advertised stream count is invalid
     */
    protected void incoming1RTTFrame(StreamsBlockedFrame frame)
            throws QuicTransportException {
        if (frame.maxStreams() > MAX_STREAMS_VALUE_LIMIT) {
            throw new QuicTransportException("Invalid maxStreams value %s"
                                                     .formatted(frame.maxStreams()),
                                             KeySpace.ONE_RTT,
                                             frame.typeField(), QuicTransportErrors.FRAME_ENCODING_ERROR);
        }
        streams.peerStreamsBlocked(frame);
    }

    /**
     * Processes a PADDING frame received in application packet space.
     *
     * @param frame received PADDING frame
     * @throws QuicTransportException never thrown by the current implementation
     */
    protected void incoming1RTTFrame(PaddingFrame frame) throws QuicTransportException {
        // nothing to do
    }

    /**
     * Processes a MAX_DATA frame received in application packet space.
     *
     * @param frame received MAX_DATA frame
     * @throws QuicTransportException if flow-control state cannot be updated
     */
    protected void incoming1RTTFrame(MaxDataFrame frame) throws QuicTransportException {
        oneRttSndQueue.updateMaxData(frame.maxData(), false);
    }

    /**
     * Processes a MAX_STREAM_DATA frame received in application packet space.
     *
     * @param frame received MAX_STREAM_DATA frame
     * @throws QuicTransportException if stream-state validation fails
     */
    protected void incoming1RTTFrame(MaxStreamDataFrame frame)
            throws QuicTransportException {
        long streamId = frame.streamID();
        QuicSenderStream stream = sendingStream(streamId, frame.typeField());
        if (stream != null) {
            streams.updateMaxStreamData(stream, frame.maxStreamData());
        }
    }

    /**
     * Processes a MAX_STREAMS frame received in application packet space.
     *
     * @param frame received MAX_STREAMS frame
     * @throws QuicTransportException if the advertised stream limit is invalid
     */
    protected void incoming1RTTFrame(MaxStreamsFrame frame) throws QuicTransportException {
        if (frame.maxStreams() > MAX_STREAMS_VALUE_LIMIT) {
            throw new QuicTransportException("Invalid maxStreams value %s"
                                                     .formatted(frame.maxStreams()),
                                             KeySpace.ONE_RTT,
                                             frame.typeField(), QuicTransportErrors.FRAME_ENCODING_ERROR);
        }
        boolean increased = streams.tryIncreaseStreamLimit(frame);
        if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
            logDebug((increased ? "increased" : "did not increase")
                    + " " + (frame.isBidi() ? "bidi" : "uni")
                    + " stream limit to " + frame.maxStreams());
        }
    }

    /**
     * Processes a STOP_SENDING frame received in application packet space.
     *
     * @param frame received STOP_SENDING frame
     * @throws QuicTransportException if stream-state validation fails
     */
    protected void incoming1RTTFrame(StopSendingFrame frame) throws QuicTransportException {
        long streamId = frame.streamID();
        QuicSenderStream stream = sendingStream(streamId, frame.typeField());
        if (stream != null) {
            streams.stopSendingReceived(stream,
                                        frame.errorCode());
        }
    }

    /**
     * Processes a PING frame received in application packet space.
     *
     * @param frame received PING frame
     * @throws QuicTransportException never thrown by the current implementation
     */
    protected void incoming1RTTFrame(PingFrame frame) throws QuicTransportException {
        // nothing to do
    }

    /**
     * Processes a CONNECTION_CLOSE frame received in application packet space.
     *
     * @param frame received CONNECTION_CLOSE frame
     * @throws QuicTransportException if termination handling fails
     */
    protected void incoming1RTTFrame(ConnectionCloseFrame frame)
            throws QuicTransportException {
        terminator.incomingConnectionCloseFrame(KeySpace.ONE_RTT, frame);
    }

    /**
     * Processes a HANDSHAKE_DONE frame received in application packet space.
     *
     * @param frame received HANDSHAKE_DONE frame
     * @throws QuicTransportException if handshake confirmation handling fails
     */
    protected void incoming1RTTFrame(HandshakeDoneFrame frame)
            throws QuicTransportException {
        if (!isClientConnection()) {
            throw new QuicTransportException("Server must not receive HANDSHAKE_DONE",
                                             KeySpace.ONE_RTT,
                                             frame.typeField(),
                                             PROTOCOL_VIOLATION);
        }
        quicTLSEngine.tryReceiveHandshakeDone();

        // A client can reach NEED_RECV_HANDSHAKE_DONE before its first Handshake
        // packet is actually sent. Receiving HANDSHAKE_DONE must still discard
        // Initial state in that case so reordered or premature server frames
        // cannot keep Initial keys around after handshake confirmation.
        if (!packetSpaces.initial.isClosed()) {
            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                logDebug("received HANDSHAKE_DONE from server, initiating close of INITIAL packet space");
            }
            packetSpaces.initial.close();
        }
        if (!packetSpaces.handshake.isClosed()) {
            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                logDebug("received HANDSHAKE_DONE from server, initiating close of HANDSHAKE packet space");
            }
            packetSpaces.handshake.close();
        }
        packetSpaces.app.confirmHandshake();
    }

    /**
     * Processes a NEW_CONNECTION_ID frame received in application packet space.
     *
     * @param frame received NEW_CONNECTION_ID frame
     * @throws QuicTransportException if the frame is invalid for the current connection state
     */
    protected void incoming1RTTFrame(NewConnectionIDFrame frame)
            throws QuicTransportException {
        if (peerConnectionId().length() == 0) {
            throw new QuicTransportException(
                    "NEW_CONNECTION_ID not allowed here",
                    KeySpace.ONE_RTT, frame.typeField(), PROTOCOL_VIOLATION);
        }
        this.peerConnIdManager.handleNewConnectionIdFrame(frame);
        packetSpaces.app.runTransmitter();
    }

    /**
     * Processes a RETIRE_CONNECTION_ID frame received in application packet space.
     *
     * @param oneRttPacket packet carrying the frame
     * @param frame        received RETIRE_CONNECTION_ID frame
     * @throws QuicTransportException if the frame cannot be applied to local connection IDs
     */
    protected void incoming1RTTFrame(OneRttPacket oneRttPacket,
                                     RetireConnectionIDFrame frame)
            throws QuicTransportException {
        this.localConnIdManager.handleRetireConnectionIdFrame(oneRttPacket.destinationId(),
                                                              PacketType.ONERTT, frame);
        packetSpaces.app.runTransmitter();
    }

    /**
     * Processes a NEW_TOKEN frame received in application packet space.
     *
     * @param frame received NEW_TOKEN frame
     * @throws QuicTransportException if the frame is invalid for the endpoint role or carries an invalid token
     */
    protected void incoming1RTTFrame(NewTokenFrame frame) throws QuicTransportException {
        if (!quicInstance.isClient()) {
            throw new QuicTransportException("Server must not receive NEW_TOKEN",
                                             KeySpace.ONE_RTT,
                                             frame.typeField(), PROTOCOL_VIOLATION);
        }
        // as per RFC 9000, section 19.7, token cannot be empty and if it is, then
        // a connection error of type FRAME_ENCODING_ERROR needs to be raised
        byte[] newToken = frame.token();
        if (newToken.length == 0) {
            throw new QuicTransportException("Empty token in NEW_TOKEN frame",
                                             KeySpace.ONE_RTT,
                                             frame.typeField(), QuicTransportErrors.FRAME_ENCODING_ERROR);
        }
        // set this as the initial token to be used in INITIAL packets when attempting
        // any new subsequent connections against this same target server
        quicInstance.registerNewToken(peerAddress(), quicVersion, newToken);
        if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
            logDebug("Registered a new (initial) token for peer " + peerAddress());
        }
    }

    /**
     * Processes a PATH_RESPONSE frame received in application packet space.
     *
     * @param frame received PATH_RESPONSE frame
     * @throws QuicTransportException reserved for future validation failures
     */
    protected void incoming1RTTFrame(PathResponseFrame frame)
            throws QuicTransportException {
        incoming1RTTFrame(pathManager.receive(peerAddress(), 0), frame);
    }

    /**
     * Processes a PATH_CHALLENGE frame received in application packet space.
     *
     * @param frame received PATH_CHALLENGE frame
     * @throws QuicTransportException reserved for future validation failures
     */
    protected void incoming1RTTFrame(PathChallengeFrame frame)
            throws QuicTransportException {
        incoming1RTTFrame(pathManager.receive(peerAddress(), 0), frame);
    }

    /**
     * Returns a new {@code ByteBuffer} to encode and encrypt packets in a datagram.
     *
     * @return a new {@code ByteBuffer} to encode and encrypt packets in a datagram.
     * This method may either allocate a new heap BteBuffer or return a (possibly
     * new) Direct ByteBuffer from the connection's Direct Byte Buffer Pool.
     *
     * @param size the maximum size of the datagram
     */
    protected ByteBuffer outgoingByteBuffer(int size) {
        boolean trace = LOGGER.isLoggable(System.Logger.Level.TRACE);
        if (useDirectBufferPool) {
            if (size <= maxDatagramSize()) {
                ByteBuffer buffer = bbPool.poll();
                if (buffer != null) {
                    if (buffer.limit() >= maxDatagramSize()) {
                        if (trace) {
                            log(LOGGER, System.Logger.Level.TRACE, "DIRECTBB: got direct buffer from pool");
                        }
                        return buffer;
                    }
                    bbAllocated.decrementAndGet();
                    if (trace) {
                        log(LOGGER, System.Logger.Level.TRACE, "DIRECTBB: releasing direct buffer");
                    }
                    buffer = null;
                }

                int allocated;
                while ((allocated = bbAllocated.get()) < MAX_DBB_POOL_SIZE) {
                    if (bbAllocated.compareAndSet(allocated, allocated + 1)) {
                        if (trace) {
                            log(LOGGER, System.Logger.Level.TRACE, "DIRECTBB: allocating direct buffer #%s", allocated + 1);
                        }
                        return ByteBuffer.allocateDirect(maxDatagramSize());
                    }
                }
                if (trace) {
                    log(LOGGER, System.Logger.Level.TRACE, "DIRECTBB: too many buffers allocated: %s", allocated);
                }

            } else if (trace) {
                log(LOGGER, System.Logger.Level.TRACE, "DIRECTBB: wrong size %s", size);
            }
        }
        return ByteBuffer.allocate(size);
    }

    /**
     * Returns a {@link io.helidon.quic.QuicEndpoint.Datagram} which contains
     * an encrypted QUIC packet containing
     * a {@linkplain ConnectionCloseFrame CONNECTION_CLOSE frame}. The CONNECTION_CLOSE
     * frame will have a frame type of {@code 0x1c} and error code of {@code NO_ERROR}.
     * <p>
     * This method should only be invoked when the {@link QuicEndpoint} is being closed
     * and the endpoint wants to send out a {@code CONNECTION_CLOSE} frame on a best-effort
     * basis (in a fire and forget manner).
     *
     * @return the datagram containing the QUIC packet with a CONNECTION_CLOSE frame or
     *        an {@linkplain Optional#empty() empty Optional} if the datagram couldn't
     *        be constructed.
     */
    final Optional<QuicEndpoint.Datagram> connectionCloseDatagram() {
        Optional<QuicPathManager.SendPermit> reservation = pathManager.reserve(maxDatagramSize());
        if (reservation.isEmpty()) {
            return Optional.empty();
        }
        QuicPathManager.SendPermit permit = reservation.orElseThrow();
        try {
            ByteBuffer quicPktPayload = this.terminator.makeConnectionCloseDatagram(permit);
            if (quicPktPayload.remaining() > permit.size()) {
                permit.release();
                return Optional.empty();
            }
            permit.resize(quicPktPayload.remaining());
            return Optional.of(QuicDatagram.create(this,
                                                   permit.destination(),
                                                   quicPktPayload,
                                                   permit));
        } catch (RuntimeException e) {
            permit.release();
            // ignore any exception because providing the connection close datagram
            // when the endpoint is being closed, is on best-effort basis
            return Optional.empty();
        }
    }

    /**
     * Called when a datagram is being released, either from
     * {@link #datagramSent(QuicDatagram)}, {@link #datagramDiscarded(QuicDatagram)},
     * or {@link #datagramDropped(QuicDatagram)}.
     * This method may either release the datagram and let it get garbage collected,
     * or return it to the pool.
     *
     * @param datagram the released datagram
     */
    protected void datagramReleased(QuicDatagram datagram) {
        discardRetiredPathControlFlights();
        boolean trace = LOGGER.isLoggable(System.Logger.Level.TRACE);
        if (trace) {
            log(LOGGER, System.Logger.Level.TRACE, "DIRECTBB: datagram released %s", datagram.payload().isDirect());
        }
        if (useDirectBufferPool) {
            ByteBuffer buffer = datagram.payload();
            buffer.clear();
            if (buffer.isDirect()) {
                if (buffer.limit() >= maxDatagramSize()) {
                    if (trace) {
                        log(LOGGER, System.Logger.Level.TRACE, "DIRECTBB: offering buffer to pool");
                    }
                    bbPool.offer(buffer);
                } else {
                    if (trace) {
                        log(LOGGER, System.Logger.Level.TRACE, "DIRECTBB: releasing direct buffer (too small)");
                    }
                    bbAllocated.decrementAndGet();
                }
            }
        }
    }

    void stopPathValidation() {
        pathValidationTimer.stop();
    }

    void discardRetiredPathControlFlights() {
        while (true) {
            OptionalLong retiredGeneration = pathManager.pollRetiredPathGeneration();
            if (retiredGeneration.isEmpty()) {
                return;
            }
            ((PacketSpaceManager) packetSpaces.app)
                    .discardPathControlFlights(retiredGeneration.orElseThrow());
        }
    }

    void pushConnectionCloseDatagram(InetSocketAddress destination,
                                     ByteBuffer datagram,
                                     QuicPathManager.SendPermit permit) {
        if (stateHandle.isMarked(QuicConnectionState.DRAINING)) {
            // a CONNECTION_CLOSE frame is being sent to the peer when the local
            // connection state is in DRAINING. This implies that the local endpoint
            // is responding to an incoming CONNECTION_CLOSE frame from the peer.
            // we switch this connection to one that does not respond to incoming packets.
            endpoint.pushClosedDatagram(this, destination, datagram, permit);
        } else if (stateHandle.isMarked(QuicConnectionState.CLOSING)) {
            // a CONNECTION_CLOSE frame is being sent to the peer when the local
            // connection state is in CLOSING. For such cases, we switch this
            // connection in the endpoint to one which responds with
            // CONNECTION_CLOSE frame for any subsequent incoming packets
            // from the peer.
            endpoint.pushClosingDatagram(this, destination, datagram, permit);
        } else {
            // should not happen
            throw new IllegalStateException("connection is neither draining nor closing,"
                                                    + " cannot send a connection close frame");
        }
    }

    /**
     * Returns true if the packet contains a CONNECTION_CLOSE frame, false otherwise.
     *
     * @return true if the packet contains a CONNECTION_CLOSE frame, false otherwise.
     *
     * @param packet the QUIC packet
     */
    private static boolean containsConnectionClose(QuicPacket packet) {
        for (QuicFrame frame : packet.frames()) {
            if (frame instanceof ConnectionCloseFrame) {
                return true;
            }
        }
        return false;
    }

    private void runPacketSpaceTransmitters() {
        packetSpaces.initial.runTransmitter();
        packetSpaces.handshake.runTransmitter();
        packetSpaces.app.runTransmitter();
    }

    private void internalProcessIncoming(QuicPathManager.ReceiveContext receiveContext,
                                         ByteBuffer destConnId,
                                         QuicPacket.HeadersType headersType,
                                         ByteBuffer buffer) {
        try {
            int packetIndex = 0;
            while (buffer.hasRemaining()) {
                int startPos = buffer.position();
                packetIndex++;
                boolean isLongHeader = QuicPacketDecoder.peekHeaderType(buffer, startPos) == QuicPacket.HeadersType.LONG;
                // It's only safe to check version here if versionNegotiated is true.
                // We might be receiving an INITIAL packet before the version negotiation
                // has been handled.
                if (isLongHeader) {
                    var headerResult = QuicPacketDecoder.peekLongHeader(buffer);
                    if (headerResult.isEmpty()) {
                        if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                            logDebug("Dropping long header packet (%s in datagram): too short",
                                      packetIndex);
                        }
                        return;
                    }
                    LongHeader header = headerResult.orElseThrow();
                    if (!header.destinationId().matches(destConnId)) {
                        if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                            logDebug("Dropping long header packet (%s in datagram):"
                                              + " wrong connection id (received length %s, expected length %s)",
                                      packetIndex,
                                      header.destinationId().length(),
                                      destConnId.remaining());
                        }
                        if (quicConfig.unsafeRawData() && LOGGER.isLoggable(System.Logger.Level.TRACE)) {
                            log(LOGGER,
                                System.Logger.Level.TRACE,
                                "UNSAFE raw connection IDs for dropped long header packet: %s vs %s",
                                header.destinationId().toHexString(),
                                Utils.asHexString(destConnId));
                        }
                        return;
                    }
                    var peekedVersion = header.version();
                    var version = this.quicVersion.versionNumber();
                    if (version != peekedVersion) {
                        if (peekedVersion == 0) {
                            if (!versionCompatible) {
                                VersionNegotiationPacket packet = codingContext.parsePacket(buffer)
                                        .map(VersionNegotiationPacket.class::cast)
                                        .orElseThrow(() ->
                                                             new IllegalStateException("Expected version negotiation packet"));
                                processDecrypted(receiveContext, packet);
                            } else {
                                if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                                    logDebug("Versions packet (%s in datagram) ignored", packetIndex);
                                }
                            }
                            return;
                        }
                        QuicVersion packetVersion = QuicVersion.of(peekedVersion).orElse(null);
                        if (packetVersion == null) {
                            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                                logDebug("Unknown Quic version in long header packet"
                                                  + " (%s in datagram) %s: 0x%x",
                                          packetIndex, headersType, peekedVersion);
                            }
                            return;
                        } else if (versionNegotiated) {
                            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                                logDebug("Dropping long header packet (%s in datagram)"
                                                  + " with version %s, already negotiated %s",
                                          packetIndex, packetVersion, quicVersion);
                            }
                            return;
                        } else if (!quicInstance().isVersionAvailable(packetVersion)) {
                            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                                logDebug("Dropping long header packet (%s in datagram)"
                                                  + " with disabled version %s",
                                          packetIndex, packetVersion);
                            }
                            return;
                        } else {
                            // do we need to be less trusting here?
                            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                                logDebug("Switching version to %s, previous: %s",
                                          packetVersion, quicVersion);
                            }
                            switchVersion(packetVersion);
                        }
                    }
                    if (decoder.peekPacketType(buffer) == PacketType.INITIAL
                            && !quicTLSEngine.keysAvailable(KeySpace.INITIAL)) {
                        if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                            logDebug("Dropping INITIAL packet (%s in datagram): %s",
                                      packetIndex, "keys discarded");
                        }
                        decoder.skipPacket(buffer, startPos, logTag());
                        continue;
                    }
                } else {
                    var cid = QuicPacketDecoder.peekShortConnectionId(buffer, destConnId.remaining()).orElse(null);
                    if (cid == null) {
                        if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                            logDebug("Dropping short header packet (%s in datagram):"
                                    + " too short", packetIndex);
                        }
                        return;
                    }
                    if (cid.mismatch(destConnId) != -1) {
                        if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                            logDebug("Dropping short header packet (%s in datagram):"
                                              + " wrong connection id (received length %s, expected length %s)",
                                      packetIndex, cid.remaining(), destConnId.remaining());
                        }
                        if (quicConfig.unsafeRawData() && LOGGER.isLoggable(System.Logger.Level.TRACE)) {
                            log(LOGGER,
                                System.Logger.Level.TRACE,
                                "UNSAFE raw connection IDs for dropped short header packet: %s vs %s",
                                Utils.asHexString(cid),
                                Utils.asHexString(destConnId));
                        }

                        return;
                    }

                }
                ByteBuffer packet = decoder.nextPacketSlice(buffer, buffer.position(), logTag());
                PacketType packetType = decoder.peekPacketType(packet);
                if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                    logDebug("unprotecting packet (%s in datagram) %s(%s bytes)",
                              packetIndex, packetType, packet.remaining());
                }
                decrypt(receiveContext, packet);
            }
        } catch (RuntimeException failure) {
            processPacketFailure(decoder.peekPacketType(buffer), failure, true);
        } catch (AssertionError failure) {
            log(LOGGER,
                System.Logger.Level.ERROR,
                "Local failure while processing incoming packet",
                failure);
            terminator.terminate(QuicCloseCommand.transport(failure));
        }
    }

    private void processDecrypted(QuicPathManager.ReceiveContext receiveContext, QuicPacket quicPacket) {
        if (!stateHandle.opened()) {
            return;
        }
        PacketType packetType = quicPacket.packetType();
        long packetNumber = quicPacket.packetNumber();
        if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
            logDebug("processDecrypted %s(%d)", packetType, packetNumber);
        }
        logPacket(true, quicPacket);
        if (packetType != PacketType.VERSIONS) {
            versionCompatible = true;
            // versions will also set versionCompatible later
        }
        if (isClientConnection()
                && quicPacket instanceof InitialPacket longPacket
                && quicPacket.frames().stream().anyMatch(CryptoFrame.class::isInstance)) {
            markVersionNegotiated(longPacket.version());
        }
        if (packetType != PacketType.ONERTT && !pathManager.knownPath(receiveContext)) {
            return;
        }
        PacketSpace packetSpace = null;
        if (packetNumber >= 0) {
            packetSpace = packetSpace(quicPacket.numberSpace());

            // From RFC 9000, Section 13.2.3:
            // A receiver MUST retain an ACK Range unless it can ensure that
            // it will not subsequently accept packets with numbers in
            // that range. Maintaining a minimum packet number that increases
            // as ranges are discarded is one way to achieve this with minimal
            // state.
            long threshold = packetSpace.minimumPacketNumberThreshold();
            if (packetNumber <= threshold) {
                // discard the packet, as we are no longer acknowledging
                // packets in this range.
                if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                    logDebug("discarding packet %s(%d) - threshold: %d",
                              packetType, packetNumber, threshold);
                }
                return;
            }
            if (packetSpace.isAcknowledged(packetNumber)) {
                if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                    logDebug("discarding packet %s(%d) - duplicated",
                              packetType, packetNumber, threshold);
                }
                return;
            }

            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                logDebug("receiving packet %s(pn:%s, %s)", packetType,
                          packetNumber, quicPacket.frames());
            }
        }
        boolean nonProbing = false;
        if (packetType == PacketType.ONERTT) {
            for (QuicFrame frame : quicPacket.frames()) {
                if (!(frame instanceof PathChallengeFrame)
                        && !(frame instanceof PathResponseFrame)
                        && !(frame instanceof NewConnectionIDFrame)
                        && !(frame instanceof PaddingFrame)) {
                    nonProbing = true;
                    break;
                }
            }
        }
        boolean budgetIncreasedBeforeAuthentication = receiveContext.amplificationBudgetIncreased();
        QuicPathManager.ReceiveResult pathResult = pathManager.authenticated(receiveContext,
                                                                             packetNumber,
                                                                             nonProbing,
                                                                             pathValidationTimeoutSupplier);
        discardRetiredPathControlFlights();
        if (!pathResult.accepted()) {
            return;
        }
        if (pathResult.pathChanged()) {
            resetForPathChange(pathResult.generation());
        }
        if (!budgetIncreasedBeforeAuthentication && receiveContext.amplificationBudgetIncreased()) {
            runPacketSpaceTransmitters();
        }
        schedulePathValidation();
        if (!isClientConnection() && packetType == PacketType.HANDSHAKE) {
            pathManager.addressValidated(receiveContext.source());
        }
        switch (packetType) {
        case VERSIONS -> processVersionNegotiationPacket(quicPacket);
        case INITIAL -> processInitialPacket(quicPacket);
        case ONERTT -> processOneRTTPacket(receiveContext, quicPacket);
        case HANDSHAKE -> processHandshakePacket(quicPacket);
        case RETRY -> processRetryPacket(quicPacket);
        case ZERORTT -> {
            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                logDebug("Ignoring unsupported 0-RTT packet");
            }
            return;
        }
        case NONE -> throw new IllegalStateException("Unrecognized packet type");
        default -> throw new IllegalStateException("Unrecognized packet type");
        }
        // packet has been processed successfully - connection isn't idle (RFC-9000, section 10.1)
        this.terminator.peerPacketProcessed();
        if (packetSpace != null) {
            packetSpace.packetReceived(
                    packetType,
                    packetNumber,
                    quicPacket.isAckEliciting());
        }
    }

    /**
     * Get or open a peer initiated stream with the given stream ID.
     *
     * @param streamId  the id of the remote stream
     * @param frameType type of the frame received, used in exceptions
     * @return the remote initiated stream identified by the given
     *        stream ID, or an empty optional
     * @throws QuicTransportException if the streamID is higher than allowed
     */
    private Optional<QuicStream> openOrGetRemoteStream(long streamId, long frameType) throws QuicTransportException {
        return streams.ensureRemoteStream(streamId, frameType);
    }

    private void processOneRTTPacket(QuicPathManager.ReceiveContext receiveContext, QuicPacket quicPacket) {
        Objects.requireNonNull(quicPacket);
        if (quicPacket.packetType() != PacketType.ONERTT) {
            throw new IllegalArgumentException("Not a ONERTT packet: " + quicPacket.packetType().text());
        }
        try {
            processApplicationPacket(receiveContext, quicPacket, PacketType.ONERTT);
        } catch (RuntimeException failure) {
            onProcessingError(quicPacket, failure);
        }
    }

    private QuicReceiverStream receivingStream(StreamFrame frame) throws QuicTransportException {
        return receivingStream(frame.streamId(), frame.typeField(), frame);
    }

    private int processIncomingCryptoFrame(CryptoFrame frame, KeySpace keySpace, CryptoDataFlow flow) {
        peerCryptoFlowLock.lock();
        try {
            boolean packetSpaceClosed = switch (keySpace) {
                case INITIAL -> packetSpaces.initial.isClosed();
                case HANDSHAKE -> packetSpaces.handshake.isClosed();
                case ONE_RTT -> packetSpaces.app.isClosed();
                case RETRY, ZERO_RTT -> throw new IllegalArgumentException(
                        "No peer crypto reassembly flow exists for key space " + keySpace);
            };
            if (packetSpaceClosed) {
                return 0;
            }
            long buffer = frame.offset() + frame.length() - flow.offset();
            if (buffer > MAX_INCOMING_CRYPTO_CAPACITY) {
                throw new QuicTransportException("Crypto buffer exceeded, required: " + buffer,
                                                 keySpace,
                                                 frame.frameType(),
                                                 QuicTransportErrors.CRYPTO_BUFFER_EXCEEDED);
            }
            int provided = 0;
            var nextFrame = flow.receive(frame);
            while (nextFrame.isPresent()) {
                CryptoFrame readyFrame = nextFrame.orElseThrow();
                if (keySpace == KeySpace.INITIAL && LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                    logDebug("Provide crypto frame to engine: %s", readyFrame);
                }
                packetTLSEngine().consumeHandshakeBytesBuffer(keySpace, readyFrame.payload());
                provided += readyFrame.length();
                nextFrame = flow.poll();
                if (keySpace == KeySpace.INITIAL && LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                    logDebug("Provided: " + provided);
                }
            }
            return provided;
        } finally {
            peerCryptoFlowLock.unlock();
        }
    }

    private void incoming1RTTFrame(QuicPathManager.ReceiveContext receiveContext,
                                   PathResponseFrame frame)
            throws QuicTransportException {
        boolean matched = pathManager.pathResponse(frame.data(), System.nanoTime(), pathValidationTimeoutNanos());
        discardRetiredPathControlFlights();
        if (!matched) {
            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                logDebug("Ignoring unmatched PATH_RESPONSE frame");
            }
            return;
        }
        schedulePathValidation();
        packetSpaces.app.runTransmitter();
    }

    private void incoming1RTTFrame(QuicPathManager.ReceiveContext receiveContext,
                                   PathChallengeFrame frame) {
        pathManager.pathChallenge(receiveContext, frame.data());
        discardRetiredPathControlFlights();
        packetSpaces.app.runTransmitter();
    }

    /*
     * delegate handling of the datagrams to the executor to free up
     * the endpoint readLoop. Helps with processing ACKs in a more
     * timely fashion, which avoids too many retransmission.
     * The endpoint readLoop runs on a single thread, while this loop
     * will have one thread per connection which helps with a better
     * utilization of the system resources.
     */
    private void scheduleForDecryption(IncomingDatagram datagram) {
        // Processes an incoming encrypted packet that has just been
        // read off the network.
        var received = datagram.buffer.remaining();
        if (incomingLoopScheduler.isStopped()) {
            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                logDebug("scheduleForDecryption closed: dropping datagram (%d bytes)",
                          received);
            }
            return;
        }
        if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
            logDebug("scheduleForDecryption: %s bytes [idbytes: %s(%s,%s)]",
                      received, datagram.destConnId().getClass().getSimpleName(),
                      datagram.destConnId().position(), datagram.destConnId().limit());
        }
        if (!incoming.offer(datagram)) {
            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                logDebug("scheduleForDecryption closed: dropping datagram (%d bytes)", received);
            }
            return;
        }

        try {
            incomingLoopScheduler.runOrSchedule(quicInstance().executor());
        } catch (RuntimeException | Error schedulingFailure) {
            incoming.removeAndRelease(datagram);
            throw schedulingFailure;
        }
    }

    private void incoming() {
        try {
            IncomingDatagram datagram = incoming.poll();
            while (datagram != null) {
                ByteBuffer buffer = datagram.buffer;
                int remaining = buffer.remaining();
                try {
                    if (!incomingLoopScheduler.isStopped()) {
                        internalProcessIncoming(datagram,
                                                datagram.destConnId(),
                                                datagram.headersType(),
                                                datagram.buffer());
                    }
                } catch (RuntimeException failure) {
                    log(LOGGER, System.Logger.Level.ERROR, "%s", failure, "Failed to process datagram");
                    terminator.terminate(QuicCloseCommand.transport(failure));
                } finally {
                    incoming.release(remaining);
                }
                datagram = incoming.poll();
            }
        } catch (RuntimeException failure) {
            log(LOGGER, System.Logger.Level.ERROR, "%s", failure, "Failed to drain incoming datagrams");
            terminator.terminate(QuicCloseCommand.transport(failure));
        }
    }

    /**
     * Schedule an incoming quic packet for decryption.
     * The ByteBuffer should contain a single packet, and its
     * limit should be set at the end of the packet.
     *
     * @param buffer a byte buffer containing the incoming packet
     */
    private void decrypt(QuicPathManager.ReceiveContext receiveContext, ByteBuffer buffer) {
        // Processes an incoming encrypted packet that has just been
        // read off the network.
        PacketType packetType = decoder.peekPacketType(buffer);
        var received = buffer.remaining();
        var pos = buffer.position();
        if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
            logDebug("decrypt %s(pos=%d, remaining=%d)",
                      packetType, pos, received);
        }
        Optional<QuicPacket> packet;
        try {
            packet = codingContext.parsePacket(buffer);
        } catch (RuntimeException failure) {
            processPacketFailure(packetType, failure, true);
            return;
        }
        if (packet.isEmpty()) {
            if (packetType == PacketType.HANDSHAKE) {
                packetSpaces.initial.fastRetransmit();
            }
            return;
        }
        try {
            processDecrypted(receiveContext, packet.orElseThrow());
        } catch (RuntimeException failure) {
            processPacketFailure(packetType, failure, false);
        }
    }

    private void processPacketFailure(PacketType packetType, Throwable failure, boolean discardableDecodeFailure) {
        if (discardableDecodeFailure
                && (failure instanceof QuicPacketDecodeException
                    || failure instanceof BufferUnderflowException
                    || failure instanceof QuicKeyUnavailableException)) {
            logPeerPacketFailure("Discarding peer packet after decode failure", packetType, failure);
            return;
        }
        if (failure instanceof QuicTransportException transportFailure) {
            if (transportFailure.errorCode() == QuicTransportErrors.INTERNAL_ERROR.code()) {
                log(LOGGER,
                    System.Logger.Level.ERROR,
                    "Local failure while processing incoming packet (type=%s)",
                    transportFailure,
                    packetType);
            } else {
                logPeerPacketFailure("Closing connection after peer protocol violation", packetType, transportFailure);
            }
            terminator.terminate(QuicCloseCommand.transport(transportFailure));
            return;
        }
        log(LOGGER,
            System.Logger.Level.ERROR,
            "Local failure while processing incoming packet (type=%s)",
            failure,
            packetType);
        terminator.terminate(QuicCloseCommand.transport(failure));
    }

    private void logPeerPacketFailure(String message, PacketType packetType, Throwable failure) {
        String detail = failure.getMessage();
        log(LOGGER,
            System.Logger.Level.DEBUG,
            "%s (type=%s): %s",
            message,
            packetType,
            detail == null ? failure.getClass().getSimpleName() : detail);
        log(LOGGER,
            System.Logger.Level.TRACE,
            "%s (type=%s)",
            failure,
            message,
            packetType);
    }

    private boolean isExpectedKeyDiscardFailure(QuicPacket packet, Throwable failure) {
        if (!(failure instanceof QuicKeyUnavailableException)) {
            return false;
        }
        return packetSpace(packet.numberSpace()).isClosed()
                || stateHandle.isMarked(QuicConnectionState.CLOSING) && containsConnectionClose(packet);
    }

    private long pathValidationTimeoutNanos() {
        long maxAckDelayMillis = ((PacketSpaceManager) packetSpaces.app).peerMaxAckDelayMillis();
        Duration peerMaxAckDelay = Duration.ofMillis(maxAckDelayMillis);
        Duration currentPto = rttEstimator.basePtoDuration().plus(peerMaxAckDelay);
        Duration initialPto = runtimeConfig.recovery().initialRtt().multipliedBy(3).plus(peerMaxAckDelay);
        Duration base = currentPto.compareTo(initialPto) >= 0 ? currentPto : initialPto;
        return base.multipliedBy(3).toNanos();
    }

    private void schedulePathValidation() {
        OptionalLong deadline = pathManager.nextValidationDeadlineNanos();
        if (deadline.isEmpty()) {
            return;
        }
        long delay = Math.max(0, deadline.orElseThrow() - System.nanoTime());
        Deadline timerDeadline = TimeSource.now().plusNanos(delay);
        pathValidationTimer.schedule(timerDeadline);
    }

    private void resetForPathChange(long generation) {
        pathRecoveryState.transition(generation, () -> {
            rttEstimator.resetForPath();
            congestionController.resetForPath(maxDatagramSize());
        });
        ((PacketSpaceManager) packetSpaces.app)
                .discardObsoletePathControlFlights(pathRecoveryState.generation());
        pathManager.pathChangeCompleted(generation);
        runPacketSpaceTransmitters();
    }

    /**
     * Returns true if queued frames are available for sending.
     *
     * @return true if queued frames are available for sending.
     */
    private boolean hasQueuedFrames() {
        return !outgoing1RTTFrames.isEmpty();
    }

    private void checkAbort(PacketNumberSpace packetNumberSpace) {
        // if pto backoff > 32 (i.e. PTO expired 5 times in a row), abort,
        // unless we haven't reached MIN_PTO_BACKOFF_TIMEOUT
        var backoff = rttEstimator.ptoBackoff();
        if (backoff > rttEstimator.maxPtoBackoff()) {
            // If the maximum backoff is exceeded, we close the connection
            // only if the associated backoff timeout exceeds the
            // MIN_PTO_BACKOFF_TIMEOUT. Otherwise, we allow the backoff
            // factor to grow again past the MAX_PTO_BACKOFF
            if (rttEstimator.isMinBackoffTimeoutExceeded()) {
                if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                    logDebug("OUT: %s: Too many probe timeouts %s",
                              packetNumberSpace, rttEstimator.state());
                    StringBuilder sb = new StringBuilder(logTag());
                    sb.append(" State: ").append(stateHandle().toString());
                    for (PacketNumberSpace sp : PacketNumberSpace.values()) {
                        if (sp == PacketNumberSpace.NONE) {
                            continue;
                        }
                        if (packetSpaces.get(sp) instanceof PacketSpaceManager m) {
                            sb.append("\nPacketSpace: ").append(sp).append('\n');
                            m.debugState("  ", sb);
                        }
                    }
                    logDebug(sb.toString());
                }
                var pto = rttEstimator.basePtoDuration();
                var to = pto.multipliedBy(backoff);
                if (to.compareTo(rttEstimator.maxPtoBackoffTimeout()) > 0) {
                    to = rttEstimator.maxPtoBackoffTimeout();
                }
                String msg = "%s: Too many probe time outs (%s: backoff %s, duration %s, %s)"
                        .formatted(logTag(), packetNumberSpace, backoff,
                                   to, rttEstimator.state());
                QuicCloseCommand closeCommand = QuicCloseCommand.transport(new QuicConnectionException(msg));
                terminator.terminate(closeCommand);
            } else {
                if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                    logDebug("%s: Max PTO backoff reached (%s) before min probe timeout exceeded (%s),"
                                     + " allow more backoff %s",
                              packetNumberSpace,
                              backoff,
                              rttEstimator.minPtoBackoffTimeout(),
                              rttEstimator.state());
                }
            }
        }
    }

    // this method is called when a packet has been acknowledged
    private void packetAcknowledged(QuicPacket packet) {
        // process packet frames to track acknowledgement
        // of RESET_STREAM frames etc...
        if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
            logDebug("Packet %s(pn:%s) is acknowledged by peer",
                      packet.packetType(),
                      packet.packetNumber());
        }
        packet.frames().forEach(this::frameAcknowledged);
    }

    // this method is called when a frame has been acknowledged
    private void frameAcknowledged(QuicFrame frame) {
        if (frame instanceof ResetStreamFrame reset) {
            long streamId = reset.streamId();
            if (streams.isSendingStream(streamId)) {
                streams.streamResetAcknowledged(reset);
            }
        } else if (frame instanceof StreamFrame streamFrame) {
            if (streamFrame.isLast()) {
                streams.streamDataSentAcknowledged(streamFrame);
            }
        }
    }

    private <T extends QuicSenderStream> CompletableFuture<T> openNewLocalStream(
            Supplier<CompletableFuture<T>> streamSupplier) {
        if (!stateHandle.opened()) {
            return MinimalFuture.failedMinimalFuture(new ClosedChannelException());
        }
        var handshake = handshakeFlow.handshakeCF();
        if (handshake.isDone()) {
            if (handshake.isCompletedExceptionally()) {
                return handshake.thenCompose(_ -> streamSupplier.get());
            }
            try {
                return streamSupplier.get();
            } catch (RuntimeException | Error failure) {
                return MinimalFuture.failedMinimalFuture(failure);
            }
        }
        var result = MinimalFuture.<T>create();
        var acquisition = new AtomicReference<CompletableFuture<T>>();
        result.whenComplete((_, _) -> {
            if (result.isCancelled()) {
                CompletableFuture<T> pending = acquisition.get();
                if (pending != null) {
                    pending.cancel(false);
                }
            }
        });
        handshake.whenComplete((_, handshakeFailure) -> {
            if (handshakeFailure != null) {
                result.completeExceptionally(handshakeFailure);
                return;
            }
            if (result.isDone()) {
                return;
            }
            CompletableFuture<T> pending;
            try {
                pending = streamSupplier.get();
            } catch (RuntimeException | Error failure) {
                result.completeExceptionally(failure);
                return;
            }
            acquisition.set(pending);
            pending.whenComplete((stream, failure) -> {
                if (failure != null) {
                    result.completeExceptionally(failure);
                } else if (!result.complete(stream)) {
                    closeUnclaimedLocalStream(stream);
                }
            });
            if (result.isCancelled()) {
                pending.cancel(false);
            }
        });
        return result;
    }

    private QuicConnectionId localConnectionIdOrThrow() {
        return localConnectionId().orElseThrow();
    }

    private void processApplicationPacket(QuicPathManager.ReceiveContext receiveContext,
                                          QuicPacket quicPacket,
                                          PacketType packetType) throws QuicTransportException {
        KeySpace keySpace = packetType.keySpace().orElseThrow();
        if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
            logDebug("processing packet %s(%s)", packetType, quicPacket.packetNumber());
        }
        var frames = quicPacket.frames();
        if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
            logDebug("processing frames: " + frames.stream()
                    .map(Object::getClass)
                    .map(Class::getSimpleName)
                    .collect(Collectors.joining(", ", "[", "]")));
        }
        for (var frame : frames) {
            if (!frame.isValidIn(packetType)) {
                throw new QuicTransportException("Invalid frame in %s packet".formatted(packetType.text()),
                                                 keySpace,
                                                 frame.typeField(),
                                                 PROTOCOL_VIOLATION);
            }
            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                logDebug("received %s frame %s",
                          packetType == PacketType.ONERTT ? "1-RTT" : "0-RTT",
                          frame);
            }
            switch (frame) {
            case AckFrame ackFrame -> incoming1RTTFrame(ackFrame);
            case StreamFrame streamFrame -> incoming1RTTFrame(streamFrame);
            case CryptoFrame crypto -> incoming1RTTFrame(crypto);
            case ResetStreamFrame resetStreamFrame -> incoming1RTTFrame(resetStreamFrame);
            case DataBlockedFrame dataBlockedFrame -> incoming1RTTFrame(dataBlockedFrame);
            case StreamDataBlockedFrame streamDataBlockedFrame -> incoming1RTTFrame(streamDataBlockedFrame);
            case StreamsBlockedFrame streamsBlockedFrame -> incoming1RTTFrame(streamsBlockedFrame);
            case PaddingFrame paddingFrame -> incoming1RTTFrame(paddingFrame);
            case MaxDataFrame maxData -> incoming1RTTFrame(maxData);
            case MaxStreamDataFrame maxStreamData -> incoming1RTTFrame(maxStreamData);
            case MaxStreamsFrame maxStreamsFrame -> incoming1RTTFrame(maxStreamsFrame);
            case StopSendingFrame stopSendingFrame -> incoming1RTTFrame(stopSendingFrame);
            case PingFrame ping -> incoming1RTTFrame(ping);
            case ConnectionCloseFrame close -> incoming1RTTFrame(close);
            case HandshakeDoneFrame handshakeDoneFrame -> incoming1RTTFrame(handshakeDoneFrame);
            case NewConnectionIDFrame newCid -> incoming1RTTFrame(newCid);
            case RetireConnectionIDFrame retireCid -> {
                if (quicPacket instanceof OneRttPacket oneRttPacket) {
                    incoming1RTTFrame(oneRttPacket, retireCid);
                } else {
                    throw new QuicTransportException("Invalid frame in %s packet".formatted(packetType.text()),
                                                     keySpace,
                                                     retireCid.typeField(),
                                                     PROTOCOL_VIOLATION);
                }
            }
            case NewTokenFrame newTokenFrame -> incoming1RTTFrame(newTokenFrame);
            case PathResponseFrame pathResponseFrame -> incoming1RTTFrame(receiveContext, pathResponseFrame);
            case PathChallengeFrame pathChallengeFrame -> incoming1RTTFrame(receiveContext, pathChallengeFrame);
            default -> {
                if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                    logDebug("Frame type: %s not supported yet", frame.getClass());
                }
            }
            }
        }
    }

    /**
     * Gets a receiving stream instance for the given ID, used for processing
     * incoming STREAM, RESET_STREAM and STREAM_DATA_BLOCKED frames.
     * Returns null if the instance is gone already. Throws an exception if the stream ID is incorrect.
     *
     * @param streamId  stream ID
     * @param frameType received frame type. Used in QuicTransportException
     * @return receiver stream, or null if stream is already gone
     * @throws QuicTransportException if the stream ID is not a valid receiving stream
     */
    private QuicReceiverStream receivingStream(long streamId, long frameType) throws QuicTransportException {
        return receivingStream(streamId, frameType, null);
    }

    private QuicReceiverStream receivingStream(long streamId, long frameType, StreamFrame initialFrame)
            throws QuicTransportException {
        var stream = findStream(streamId).orElse(null);
        boolean isLocalStream = isLocalStream(streamId);
        boolean isUnidirectional = isUnidirectional(streamId);
        if (isLocalStream && isUnidirectional) {
            // stream is write-only
            throw new QuicTransportException("Stream %s (type %s) is unidirectional"
                                                     .formatted(streamId, streamType(streamId)),
                                             KeySpace.ONE_RTT, frameType, QuicTransportErrors.STREAM_STATE_ERROR);
        }
        if (stream == null && isLocalStream) {
            // the stream is either closed or bad stream
            if (!isExistingStreamId(streamId)) {
                throw new QuicTransportException("No such stream %s (type %s)"
                                                         .formatted(streamId, streamType(streamId)),
                                                 KeySpace.ONE_RTT, frameType,
                                                 QuicTransportErrors.STREAM_STATE_ERROR);
            }
            return null;
        }

        if (stream == null) {
            // Note: The quic protocol allows any peer to open
            //       a bidirectional remote stream.
            //       The HTTP/3 protocol does not allow a server to open a
            //       bidirectional stream on the client. If this is a client
            //       connection and the stream type is bidirectional and
            //       remote, the connection will be closed by the HTTP/3
            //       higher level protocol but not here, since this is
            //       not a Quic protocol error.
            if (initialFrame != null) {
                streams.processInitialRemoteStreamFrame(initialFrame);
                return null;
            }
            stream = openOrGetRemoteStream(streamId, frameType).orElse(null);
            if (stream == null) {
                return null;
            }
        }
        return (QuicReceiverStream) stream;
    }

    /**
     * Gets a sending stream instance for the given ID, used for processing
     * incoming MAX_STREAM_DATA and STOP_SENDING frames.
     * Returns null if the instance is gone already. Throws an exception if the stream ID is incorrect.
     *
     * @param streamId  stream ID
     * @param frameType received frame type. Used in QuicTransportException
     * @return sender stream, or null if stream is already gone
     * @throws QuicTransportException if the stream ID is not a valid sending stream
     */
    private QuicSenderStream sendingStream(long streamId, long frameType) throws QuicTransportException {
        var stream = findStream(streamId).orElse(null);
        boolean isLocalStream = isLocalStream(streamId);
        boolean isUnidirectional = isUnidirectional(streamId);
        if (!isLocalStream && isUnidirectional) {
            // stream is read-only
            throw new QuicTransportException("Stream %s (type %s) is unidirectional"
                                                     .formatted(streamId, streamType(streamId)),
                                             QuicTLSEngine.KeySpace.ONE_RTT, frameType, QuicTransportErrors.STREAM_STATE_ERROR);
        }
        if (stream == null && isLocalStream) {
            // the stream is either closed or bad stream
            if (!isExistingStreamId(streamId)) {
                throw new QuicTransportException("No such stream %s (type %s)"
                                                         .formatted(streamId, streamType(streamId)),
                                                 QuicTLSEngine.KeySpace.ONE_RTT, frameType,
                                                 QuicTransportErrors.STREAM_STATE_ERROR);
            }
            return null;
        }

        if (stream == null) {
            stream = openOrGetRemoteStream(streamId, frameType).orElse(null);
            if (stream == null) {
                return null;
            }
        }
        return (QuicSenderStream) stream;
    }

    /**
     * Pushes the {@linkplain ProtectionRecord#datagram() datagram} contained in
     * the {@code protectionRecord}, through the {@linkplain QuicEndpoint endpoint}.
     *
     * @param protectionRecord the ProtectionRecord containing the datagram
     */
    private void pushEncryptedDatagram(ProtectionRecord protectionRecord) {
        long packetNumber = protectionRecord.packet().packetNumber();
        long retransmittedPacketNumber = protectionRecord.retransmittedPacketNumber();
        boolean pktContainsConnClose = containsConnectionClose(protectionRecord.packet());
        // if the connection isn't open then except for the packet containing a CONNECTION_CLOSE
        // frame, we don't push any other packets.
        if (!isOpen() && !pktContainsConnClose) {
            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                logDebug("connection isn't open - ignoring %s(pn:%s): frames:%s",
                          protectionRecord.packet.packetType(),
                          protectionRecord.packet.packetNumber(),
                          protectionRecord.packet.frames());
            }
            protectionRecord.permit().release();
            datagramDropped(QuicDatagram.create(this,
                                                protectionRecord.destination(),
                                                protectionRecord.datagram));
            return;
        }
        // ProtectionRecord currently owns the datagram and the first packet metadata.
        // Coalesced packets share the datagram buffer and are accounted through
        // datagram send/drop callbacks.
        ByteBuffer datagram = protectionRecord.datagram();
        int firstPacketOffset = protectionRecord.firstPacketOffset();
        // flip the datagram
        datagram.limit(datagram.position());
        datagram.position(firstPacketOffset);
        protectionRecord.permit().resize(datagram.remaining());
        if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
            PacketType packetType = protectionRecord.packet().packetType();
            int packetOffset = protectionRecord.packetOffset();
            if (packetOffset == firstPacketOffset) {
                logDebug("Pushing datagram([%s(%d)], %d)", packetType, packetNumber,
                          datagram.remaining());
            } else {
                logDebug("Pushing coalesced datagram([%s(%d)], %d)",
                          packetType, packetNumber, datagram.remaining());
            }
        }

        // upon successful sending of the datagram, notify that the packet was sent
        // we call packetSent just before sending the packet here, to make sure
        // that the PendingAcknowledgement will be present in the queue before
        // we receive the ACK frame from the server. Not doing this would create
        // a race where the peer might be able to send the ack, and we might process
        // it, before the PendingAcknowledgement is added.
        QuicPacket packet = protectionRecord.packet();
        PacketSpace packetSpace = packetSpace(packet.numberSpace());
        packetSpace.packetSent(packet,
                               retransmittedPacketNumber,
                               packetNumber,
                               protectionRecord.permit().generation());

        // Record the logical send before endpoint dispatch. A peer response can be processed
        // as soon as the datagram is dispatched, and that receive must grant a new restart.
        if (packet.isAckEliciting()) {
            this.terminator.ackElicitingPacketSent();
        }

        // if we are sending a packet containing a CONNECTION_CLOSE frame, then we
        // also switch/remove the current connection instance in the endpoint.
        if (pktContainsConnClose) {
            pushConnectionCloseDatagram(protectionRecord.destination(), datagram, protectionRecord.permit());
        } else {
            endpoint.pushDatagram(this,
                                  protectionRecord.destination(),
                                  datagram,
                                  protectionRecord.permit());
        }
    }

    // adaptation to Function<? super Void, HandshakeFlow>
    private HandshakeFlow sendFirstInitialPacket(Void unused) {
        // may happen if connection cancelled before endpoint is
        // created
        Optional<QuicTermination> termination = termination();
        if (termination.isPresent()) {
            throw new CompletionException(termination.orElseThrow().closeCause());
        }
        try {
            startInitialTimer();
            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                logDebug("connection ID length: %s, %s: %s - %s",
                         connectionId.length(),
                         endpoint,
                         endpoint.name(),
                         endpoint.localAddressString());
            }
            if (quicConfig.unsafeRawData() && LOGGER.isLoggable(System.Logger.Level.TRACE)) {
                log(LOGGER,
                    System.Logger.Level.TRACE,
                    "UNSAFE raw connection ID: %s",
                    connectionId.toHexString());
            }
            var localAddress = endpoint.localAddress();
            var conflict = Utils.addressConflict(localAddress, peerAddress());
            if (conflict.isPresent()) {
                String msg = conflict.orElseThrow();
                if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                    logDebug("%s (local: %s, remote: %s)", msg, localAddress, peerAddress());
                }
                log(LOGGER,
                    System.Logger.Level.ERROR,
                    "%s (local: %s, remote: %s)",
                    msg,
                    localAddress,
                    peerAddress());
                throw new QuicConnectionException(msg);
            }
            QuicConnectionId clientSelectedPeerId = initialServerConnectionId();
            this.peerConnIdManager.originalServerConnId(clientSelectedPeerId);
            handshakeFlow.markHandshakeStart();
            stateHandle.markHelloSent();
            // the "original version" used to establish the connection
            QuicVersion originalVersion = this.quicVersion;
            quicTLSEngine.deriveInitialKeys(originalVersion, clientSelectedPeerId.bufferData());
            BufferData quicInitialParameters = buildInitialParameters();
            quicTLSEngine.localQuicTransportParameters(quicInitialParameters);
            handshakeFlow.localInitial.keepReplayData();
            continueHandshake();
            packetSpaces.initial.runTransmitter();
        } catch (Throwable t) {
            terminator.terminate(transport(t));
            throw new CompletionException(termination().orElseThrow().closeCause());
        }
        return handshakeFlow;
    }

    private QuicConnectionId initialServerConnectionId() {
        byte[] bytes = new byte[INITIAL_SERVER_CONNECTION_ID_LENGTH];
        RANDOM.nextBytes(bytes);
        return new PeerConnectionId(bytes);
    }

    /**
     * Compose a list of Quic frames containing a crypto frame and an ack frame,
     * omitting null frames.
     *
     * @param crypto the crypto frame
     * @param ack    the ack frame
     * @return A list of {@link QuicFrame}.
     */
    private List<QuicFrame> makeList(CryptoFrame crypto, AckFrame ack) {
        List<QuicFrame> frames = new ArrayList<>(2);
        if (crypto != null) {
            frames.add(crypto);
        }
        if (ack != null) {
            frames.add(ack);
        }
        return frames;
    }

    private QuicPacketTLSEngine packetTLSEngine() {
        return QuicPacketTLSEngine.internal(quicTLSEngine);
    }

    private void continueHandshake1() {
        HandshakeFlow flow = handshakeFlow;
        // make sure the localInitialQueue is not modified concurrently
        // while we are in this loop
        boolean handshakeDataAvailable = false;
        boolean initialDataAvailable = false;
        for (;;) {
            var handshakeState = quicTLSEngine.handshakeState();
            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                logDebug("continueHandshake: state: %s", handshakeState);
            }
            if (handshakeState == QuicTLSEngine.HandshakeState.NEED_SEND_CRYPTO) {
                // buffer next TLS message
                KeySpace keySpace = quicTLSEngine.currentSendKeySpace();
                ByteBuffer payloadBuffer;
                handshakeLock.lock();
                try {
                    payloadBuffer = packetTLSEngine().handshakeBytesBuffer(keySpace)
                            .orElseThrow(() -> new IllegalStateException(
                                    "Handshake state requires outbound CRYPTO, but no handshake bytes are available"));
                    if (keySpace == KeySpace.INITIAL) {
                        flow.localInitial.enqueue(payloadBuffer);
                        initialDataAvailable = true;
                    } else if (keySpace == KeySpace.HANDSHAKE) {
                        flow.localHandshake.enqueue(payloadBuffer);
                        handshakeDataAvailable = true;
                    }
                } finally {
                    handshakeLock.unlock();
                }

                if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                    logDebug("continueHandshake: buffered %s bytes in %s keyspace",
                              payloadBuffer.remaining(), keySpace);
                }
            } else if (handshakeState == QuicTLSEngine.HandshakeState.NEED_TASK) {
                quicTLSEngine.delegatedTask()
                        .orElseThrow(() -> new IllegalStateException(
                                "Handshake state requires a delegated task, but none is available"))
                        .run();
            } else if (handshakeState == QuicTLSEngine.HandshakeState.NEED_SEND_HANDSHAKE_DONE) {
                if (quicTLSEngine.tryMarkHandshakeDone()) {
                    enqueue1RTTFrame(HandshakeDoneFrame.create());
                    packetSpaces.app.confirmHandshake();
                    packetSpaces.app.runTransmitter();
                    logDebug("queued HANDSHAKE_DONE for 1-RTT transmission");
                }
                return;
            } else {
                if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                    logDebug("continueHandshake: nothing to do (state: %s)", handshakeState);
                    if (handshakeState == QuicTLSEngine.HandshakeState.NEED_SEND_HANDSHAKE_DONE) {
                        logDebug("continueHandshake: transport has no HANDSHAKE_DONE send path; "
                                + "server handshake cannot complete until a HandshakeDoneFrame is queued");
                    }
                }
                logDebugIf(handshakeState == QuicTLSEngine.HandshakeState.NEED_SEND_HANDSHAKE_DONE,
                           "continueHandshake reached NEED_SEND_HANDSHAKE_DONE, but no transport code queues HANDSHAKE_DONE");
                if (initialDataAvailable) {
                    packetSpaces.initial.runTransmitter();
                }
                if (handshakeDataAvailable && flow.localInitial.remaining() == 0) {
                    packetSpaces.handshake.runTransmitter();
                }
                return;
            }
        }
    }

    private boolean sendData(PacketNumberSpace packetNumberSpace)
            throws QuicKeyUnavailableException, QuicTransportException {
        if (packetNumberSpace != PacketNumberSpace.APPLICATION) {
            // This method can be called by two packet spaces: INITIAL and HANDSHAKE.
            // We need to lock to make sure that the method is not run concurrently.
            handshakeLock.lock();
            try {
                return sendInitialOrHandshakeData(packetNumberSpace);
            } finally {
                handshakeLock.unlock();
            }
        } else {
            return oneRttSndQueue.send1RTTData();
        }
    }

    private boolean sendPriorityData(PacketNumberSpace packetNumberSpace)
            throws QuicKeyUnavailableException, QuicTransportException {
        return packetNumberSpace == PacketNumberSpace.APPLICATION && oneRttSndQueue.sendPriority1RTTData();
    }

    private boolean sendInitialOrHandshakeData(PacketNumberSpace packetNumberSpace)
            throws QuicKeyUnavailableException, QuicTransportException {
        logDebug("Send %s data", packetNumberSpace);
        HandshakeFlow flow = handshakeFlow;
        QuicConnectionId peerConnId = peerConnectionId();
        if (packetNumberSpace == PacketNumberSpace.INITIAL && flow.localInitial.remaining() > 0) {
            Optional<QuicPathManager.SendPermit> reservation =
                    pathManager.reserve(SMALLEST_MAXIMUM_DATAGRAM_SIZE);
            if (reservation.isPresent()
                    && reservation.orElseThrow().size() < SMALLEST_MAXIMUM_DATAGRAM_SIZE) {
                reservation.orElseThrow().release();
                return false;
            }
            if (reservation.isEmpty()) {
                return false;
            }
            QuicPathManager.SendPermit permit = reservation.orElseThrow();
            boolean transferred = false;
            try {
            // process buffered initial data
            byte[] token = initialToken();
            int tksize = token.length;
            PacketSpace packetSpace = packetSpaces.get(PacketNumberSpace.INITIAL);
            int maxDstIdLength = isClientConnection()
                    ? MAX_CONNECTION_ID_LENGTH   // reserve space for the id to grow
                    : peerConnId.length();
            int maxSrcIdLength = connectionId.length();
            // compute maxPayloadSize given maxSizeBeforeEncryption
            var largestAckedPN = packetSpace.largestPeerAcknowledgedPacketNumber();
            var packetNumber = packetSpace.allocateNextPN();
            int maxPayloadSize = QuicPacketEncoder.computeMaxInitialPayloadSize(codingContext,
                                                                                4,
                                                                                tksize,
                                                                                maxSrcIdLength,
                                                                                maxDstIdLength,
                                                                                SMALLEST_MAXIMUM_DATAGRAM_SIZE);
            if (maxPayloadSize < 5) {
                // token too long, can't fit a crypto frame in this packet. Abort.
                String msg = "Initial token too large, maxPayload: " + maxPayloadSize;
                terminator.terminate(QuicCloseCommand.transport(new QuicConnectionException(msg)));
                return false;
            }
            AckFrame ackFrame = packetSpace.nextAckFrame(false, maxPayloadSize).orElse(null);
            int ackSize = ackFrame == null ? 0 : ackFrame.size();
            CryptoFrame crypto = flow.localInitial.produceFrame(maxPayloadSize - ackSize).orElse(null);
            List<QuicFrame> frames = makeList(crypto, ackFrame);
            OutgoingQuicPacket packet = encoder.newInitialPacket(
                    connectionId, peerConnId, token,
                    packetNumber, largestAckedPN, frames, codingContext);
            stateHandle.markHelloSent();
            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                logDebug("protecting initial quic hello packet for %s(%s) - %d bytes",
                          Arrays.toString(quicTLSEngine.sslParameters().getApplicationProtocols()),
                          peerAddress(), packet.size());
            }
            transferred = true;
            pushDatagram(ProtectionRecord.single(packet,
                                                 permit.destination(),
                                                 permit,
                                                 this::allocateDatagramForEncryption));
            if (flow.localHandshake.remaining() > 0) {
                logDebug("local handshake has remaining, starting HANDSHAKE transmitter");
                packetSpaces.handshake.runTransmitter();
            }
            } finally {
                if (!transferred) {
                    permit.release();
                }
            }
        } else if (packetNumberSpace == PacketNumberSpace.HANDSHAKE && flow.localHandshake.remaining() > 0) {
            Optional<QuicPathManager.SendPermit> reservation =
                    pathManager.reserve(SMALLEST_MAXIMUM_DATAGRAM_SIZE);
            if (reservation.isEmpty()) {
                return false;
            }
            QuicPathManager.SendPermit permit = reservation.orElseThrow();
            boolean transferred = false;
            try {
            // process buffered handshake data
            PacketSpaceManager packetSpace =
                    (PacketSpaceManager) packetSpaces.get(PacketNumberSpace.HANDSHAKE);
            // compute maxPayloadSize given maxSizeBeforeEncryption
            var largestAckedPN = packetSpace.largestPeerAcknowledgedPacketNumber();
            long packetNumber = packetSpace.nextPacketNumber().get();
            int maxPayloadSize = QuicPacketEncoder.computeMaxHandshakePayloadSize(codingContext,
                                                                                  packetNumber,
                                                                                  connectionId.length(),
                                                                                  peerConnId.length(),
                                                                                  permit.size());
            if (maxPayloadSize < 4) {
                return false;
            }
            if (packetSpace.allocateNextPN() != packetNumber) {
                throw new IllegalStateException("Handshake packet number changed while preparing a packet");
            }
            AckFrame ackFrame = packetSpace.nextAckFrame(false, maxPayloadSize - 4).orElse(null);
            int ackSize = ackFrame == null ? 0 : ackFrame.size();
            maxPayloadSize = maxPayloadSize - ackSize;

            CryptoFrame crypto = flow.localHandshake.produceFrame(maxPayloadSize).orElse(null);
            List<QuicFrame> frames = makeList(crypto, ackFrame);
            OutgoingQuicPacket packet = encoder.newHandshakePacket(
                    connectionId, peerConnId,
                    packetNumber, largestAckedPN, frames, codingContext, logTag());
            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                logDebug("protecting handshake quic hello packet for %s(%s) - %d bytes",
                          Arrays.toString(quicTLSEngine.sslParameters().getApplicationProtocols()),
                          peerAddress(), packet.size());
            }
            transferred = true;
            pushDatagram(ProtectionRecord.single(packet,
                                                 permit.destination(),
                                                 permit,
                                                 this::allocateDatagramForEncryption));
            var handshakeState = quicTLSEngine.handshakeState();
            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                logDebug("Handshake state is now: %s", handshakeState);
                if (handshakeState == HandshakeState.NEED_SEND_HANDSHAKE_DONE) {
                    logDebug("Handshake packet sender reached NEED_SEND_HANDSHAKE_DONE but does not "
                            + "queue a HandshakeDoneFrame");
                }
            }
            logDebugIf(handshakeState == HandshakeState.NEED_SEND_HANDSHAKE_DONE,
                       "handshake sender finished CRYPTO emission and TLS moved to NEED_SEND_HANDSHAKE_DONE without queuing "
                       + "HANDSHAKE_DONE");
            if (flow.localHandshake.remaining() == 0
                    && quicTLSEngine.isTLSHandshakeComplete()
                    && !flow.handshakeCF.isDone()) {
                if (stateHandle.markHandshakeComplete()) {
                    if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                        logDebug("Handshake completed");
                    }
                    completeHandshakeCF();
                }
            }
            if (!packetSpaces.initial().isClosed() && flow.localInitial.remaining() > 0) {
                logDebug("local initial has remaining, starting INITIAL transmitter");
                packetSpaces.initial.runTransmitter();
            }
            } finally {
                if (!transferred) {
                    permit.release();
                }
            }
        } else {
            return false;
        }
        return true;
    }

    private void validateServerTransportParameters(QuicTransportParameters params,
                                                   QuicConnectionId retryConnId)
            throws QuicTransportException {
        if (params.isPresent(ParameterId.retry_source_connection_id)) {
            if (retryConnId == null) {
                throw new QuicTransportException("Retry connection ID was set even though no retry was performed",
                                                 0, QuicTransportErrors.TRANSPORT_PARAMETER_ERROR);
            } else if (!params.matches(ParameterId.retry_source_connection_id, retryConnId)) {
                throw new QuicTransportException("Retry connection ID does not match",
                                                 0, QuicTransportErrors.TRANSPORT_PARAMETER_ERROR);
            }
        } else if (retryConnId != null) {
            throw new QuicTransportException("Retry connection ID was expected but absent",
                                             0, QuicTransportErrors.TRANSPORT_PARAMETER_ERROR);
        }
        if (!params.isPresent(ParameterId.original_destination_connection_id)) {
            throw new QuicTransportException(
                    "Original connection ID transport parameter missing",
                    0, QuicTransportErrors.TRANSPORT_PARAMETER_ERROR);
        }
        if (!params.isPresent(initial_source_connection_id)) {
            throw new QuicTransportException(
                    "Initial source connection ID transport parameter missing",
                    0, QuicTransportErrors.TRANSPORT_PARAMETER_ERROR);
        }
        QuicConnectionId clientSelectedPeerConnId = this.peerConnIdManager.originalServerConnId();
        if (!params.matches(ParameterId.original_destination_connection_id, clientSelectedPeerConnId)) {
            throw new QuicTransportException(
                    "Original connection ID does not match",
                    0, QuicTransportErrors.TRANSPORT_PARAMETER_ERROR);
        }
        if (!params.matches(initial_source_connection_id, incomingInitialPacketSourceId)) {
            throw new QuicTransportException(
                    "Initial source connection ID does not match",
                    0, QuicTransportErrors.TRANSPORT_PARAMETER_ERROR);
        }
        if (peerConnectionId().length() == 0
                && params.isPresent(ParameterId.preferred_address)) {
            throw new QuicTransportException(
                    "Preferred address present but connection ID has zero length",
                    0, QuicTransportErrors.TRANSPORT_PARAMETER_ERROR);
        }
    }

    private void validateClientTransportParameters(QuicTransportParameters params,
                                                   QuicConnectionId retryConnId)
            throws QuicTransportException {
        if (retryConnId != null) {
            throw new QuicTransportException("Retry connection ID is not expected on server side",
                                             0, QuicTransportErrors.TRANSPORT_PARAMETER_ERROR);
        }
        if (params.isPresent(ParameterId.retry_source_connection_id)) {
            throw new QuicTransportException("Client must not send retry_source_connection_id",
                                             0, QuicTransportErrors.TRANSPORT_PARAMETER_ERROR);
        }
        if (params.isPresent(ParameterId.original_destination_connection_id)) {
            throw new QuicTransportException("Client must not send original_destination_connection_id",
                                             0, QuicTransportErrors.TRANSPORT_PARAMETER_ERROR);
        }
        if (params.isPresent(ParameterId.preferred_address)) {
            throw new QuicTransportException("Client must not send preferred_address",
                                             0, QuicTransportErrors.TRANSPORT_PARAMETER_ERROR);
        }
        if (params.isPresent(ParameterId.stateless_reset_token)) {
            throw new QuicTransportException("Client must not send stateless_reset_token",
                                             0, QuicTransportErrors.TRANSPORT_PARAMETER_ERROR);
        }
        if (!params.isPresent(initial_source_connection_id)) {
            throw new QuicTransportException(
                    "Initial source connection ID transport parameter missing",
                    0, QuicTransportErrors.TRANSPORT_PARAMETER_ERROR);
        }
        if (!params.matches(initial_source_connection_id, incomingInitialPacketSourceId)) {
            throw new QuicTransportException(
                    "Initial source connection ID does not match",
                    0, QuicTransportErrors.TRANSPORT_PARAMETER_ERROR);
        }
    }

    // the token to be included in initial packets, if any.
    private byte[] initialToken() {
        return initialToken;
    }

    private List<QuicFrame> ackOrPing(AckFrame ack, boolean sendPing) {
        if (sendPing) {
            return ack == null ? List.of(PingFrame.create()) : List.of(PingFrame.create(), ack);
        }
        return List.of(ack);
    }

    /**
     * Emit a possibly non ACK-eliciting packet containing the given ACK frame.
     *
     * @param packetSpaceManager the packet space manager on behalf
     *                          of which the acknowledgement should
     *                          be sent.
     * @param ackFrame           the ACK frame to be sent.
     * @param sendPing           whether a PING frame should be sent.
     * @return the emitted packet number, or -1L if not applicable or not emitted
     */
    private long emitAckPacket(PacketSpace packetSpaceManager, AckFrame ackFrame,
                               boolean sendPing)
            throws QuicKeyUnavailableException, QuicTransportException {
        if (ackFrame == null && !sendPing) {
            return -1L;
        }
        if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
            if (sendPing) {
                logDebug("Sending PING packet %s ack",
                          ackFrame == null ? "without" : "with");
            } else {
                logDebug("sending ACK packet");
            }
        }
        List<QuicFrame> frames = ackOrPing(ackFrame, sendPing);
        PacketNumberSpace packetNumberSpace = packetSpaceManager.packetNumberSpace();
        if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
            logDebug("Sending packet for %s, frame=%s", packetNumberSpace, frames);
        }
        KeySpace keySpace = switch (packetNumberSpace) {
            case APPLICATION -> KeySpace.ONE_RTT;
            case HANDSHAKE -> KeySpace.HANDSHAKE;
            case INITIAL -> KeySpace.INITIAL;
            default -> throw new UnsupportedOperationException(
                    "Invalid packet number space: " + packetNumberSpace.text());
        };
        logDebugIf(sendPing, "%s: sending PingFrame", keySpace);
        Optional<QuicPathManager.SendPermit> reservation = pathManager.reserve(maxDatagramSize());
        if (reservation.isEmpty()) {
            return -1L;
        }
        QuicPathManager.SendPermit permit = reservation.orElseThrow();
        boolean transferred = false;
        try {
            QuicConnectionId destinationConnectionId;
            if (packetNumberSpace == PacketNumberSpace.APPLICATION) {
                Optional<PeerConnIdManager.PathCidBinding> binding = pathManager.cidBinding(permit);
                if (binding.isEmpty()) {
                    return -1L;
                }
                destinationConnectionId = binding.orElseThrow().connectionId();
            } else {
                destinationConnectionId = peerConnectionId();
            }
            QuicPacket ackpacket = encoder.newOutgoingPacket(keySpace,
                                                             packetSpaceManager, localConnectionIdOrThrow(),
                                                             destinationConnectionId, initialToken(), frames, codingContext);
            if (ackpacket.size() > permit.size()) {
                return -1L;
            }
            transferred = true;
            pushDatagram(ProtectionRecord.single(ackpacket,
                                                 permit.destination(),
                                                 permit,
                                                 this::allocateDatagramForEncryption));
            return ackpacket.packetNumber();
        } finally {
            if (!transferred) {
                permit.release();
            }
        }
    }

    private LinkedList<QuicFrame> removeOutdatedFrames(List<QuicFrame> frames) {
        // Remove frames that should not be retransmitted
        LinkedList<QuicFrame> result = new LinkedList<>();
        for (QuicFrame f : frames) {
            if (!(f instanceof PaddingFrame)
                    && !(f instanceof AckFrame)
                    && !(f instanceof PathChallengeFrame)
                    && !(f instanceof PathResponseFrame)) {
                result.add(f);
            }
        }
        return result;
    }

    /* ========================================================
     *   Direct Byte Buffer Pool
     * ======================================================== */

    /**
     * Retransmit the given packet on behalf of the given packet space
     * manager.
     *
     * @param packetSpaceManager the packet space manager on behalf of
     *                          which the packet is being retransmitted
     * @param packet             the unacknowledged packet which should be retransmitted
     */
    private boolean retransmit(PacketSpace packetSpaceManager, QuicPacket packet, int attempts)
            throws QuicKeyUnavailableException, QuicTransportException {
        if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
            logDebug("Retransmitting packet [type=%s, pn=%d, attempts:%d]: %s",
                      packet.packetType(), packet.packetNumber(), attempts, packet);
        }

        Optional<QuicPathManager.SendPermit> reservation = pathManager.reserve(maxDatagramSize());
        if (reservation.isEmpty()) {
            return false;
        }
        QuicPathManager.SendPermit permit = reservation.orElseThrow();
        boolean transferred = false;
        try {
            PacketNumberSpace packetNumberSpace = packetSpaceManager.packetNumberSpace();
            if (packetNumberSpace == PacketNumberSpace.INITIAL && permit.size() < SMALLEST_MAXIMUM_DATAGRAM_SIZE) {
                // Initial packets are padded to the minimum datagram size, including retransmissions.
                return false;
            }
            long oldPacketNumber = packet.packetNumber();

            long largestAckedPN = packetSpaceManager.largestPeerAcknowledgedPacketNumber();
            long newPacketNumber = packetSpaceManager.allocateNextPN();
            int maxDatagramSize = permit.size();
            QuicConnectionId destinationConnectionId;
            if (packetNumberSpace == PacketNumberSpace.APPLICATION) {
                Optional<PeerConnIdManager.PathCidBinding> binding = pathManager.cidBinding(permit);
                if (binding.isEmpty()) {
                    return false;
                }
                destinationConnectionId = binding.orElseThrow().connectionId();
            } else {
                destinationConnectionId = peerConnectionId();
            }
            int dstIdLength = destinationConnectionId.length();
            int initialDstIdLength = MAX_CONNECTION_ID_LENGTH; // reserve space for the ID to grow

            int maxPayloadSize = switch (packetNumberSpace) {
                case APPLICATION -> QuicPacketEncoder.computeMaxOneRTTPayloadSize(
                        codingContext,
                        newPacketNumber,
                        dstIdLength,
                        maxDatagramSize,
                        largestAckedPN);
                case INITIAL -> QuicPacketEncoder.computeMaxInitialPayloadSize(
                        codingContext, computePacketNumberLength(newPacketNumber,
                                                                 codingContext.largestAckedPN(PacketNumberSpace.INITIAL)),
                        ((InitialPacket) packet).tokenLength(),
                        localConnectionIdOrThrow().length(), initialDstIdLength, maxDatagramSize);
                case HANDSHAKE -> QuicPacketEncoder.computeMaxHandshakePayloadSize(
                        codingContext, newPacketNumber, localConnectionIdOrThrow().length(),
                        dstIdLength, maxDatagramSize);
                default -> throw new IllegalArgumentException(
                        "Invalid packet number space: " + packetNumberSpace.text());
            };

            // The new packet may have larger size(), which might no longer fit inside
            // the maximum datagram size supported on the path. To avoid that, we
            // strip the padding and old ack frame from the original packet, and
            // include the new ack frame only if it fits in the available size.
            LinkedList<QuicFrame> frames = removeOutdatedFrames(packet.frames());
            int size = frames.stream().mapToInt(QuicFrame::size).sum();
            int remaining = maxPayloadSize - size;
            if (remaining < 0) {
                return false;
            }
            AckFrame ack = packetSpaceManager.nextAckFrame(false, remaining).orElse(null);
            if (ack != null) {
                frames.addFirst(ack);
            }
            QuicPacket retransmitted =
                    switch (packet.packetType()) {
                        case INITIAL -> encoder.newInitialPacket(localConnectionIdOrThrow(),
                                                                 destinationConnectionId, ((InitialPacket) packet).token(),
                                                                 newPacketNumber, largestAckedPN, frames,
                                                                 codingContext);
                        case HANDSHAKE -> encoder.newHandshakePacket(localConnectionIdOrThrow(),
                                                                     destinationConnectionId, newPacketNumber, largestAckedPN,
                                                                     frames, codingContext, logTag());
                        case ONERTT -> encoder.newOneRttPacket(
                                destinationConnectionId, newPacketNumber, largestAckedPN,
                                frames, codingContext, logTag());
                        case ZERORTT -> throw new IllegalArgumentException("Cannot retransmit unsupported 0-RTT packet");
                        default -> throw new IllegalArgumentException("packetType: %s, packet: %s"
                                                                              .formatted(packet.packetType().text(),
                                                                                         packet.packetNumber()));
                    };

            logDebug("OUT: retransmitting packet [%s] pn:%s as pn:%s",
                       packet.packetType(), oldPacketNumber, newPacketNumber);
            transferred = true;
            pushDatagram(ProtectionRecord.retransmitting(retransmitted,
                                                         oldPacketNumber,
                                                         permit.destination(),
                                                         permit,
                                                         this::allocateDatagramForEncryption));
            return true;
        } finally {
            if (!transferred) {
                permit.release();
            }
        }
    }

    private void logDebugIf(boolean enabled, String format, Object... args) {
        if (enabled) {
            logDebug(format, args);
        }
    }

    private void logPacket(boolean received, QuicPacket packet) {
        if (LOGGER.isLoggable(System.Logger.Level.TRACE)) {
            log(LOGGER,
                System.Logger.Level.TRACE,
                "%s %s",
                received ? "<=" : "=>",
                packet.prettyPrint());
        }
    }

    private void logDebug(String format, Object... args) {
        if (args.length == 0) {
            log(LOGGER, System.Logger.Level.DEBUG, "%s", format);
        } else {
            log(LOGGER, System.Logger.Level.DEBUG, format, args);
        }
    }

    private void logDebug(String message, Throwable throwable) {
        log(LOGGER, System.Logger.Level.DEBUG, "%s", throwable, message);
    }

    /**
     * Connection-owned budget handle shared by QUIC reassembly structures.
     */
    @Api.Internal
    public interface ReassemblyBudget extends OrderedFlow.ReassemblyBudget, AutoCloseable {
        /**
         * Stops this flow from retaining additional nodes. Existing permits can still be released.
         */
        @Override
        void close();
    }

    /**
     * An abstraction to represent the connection state as a bit mask.
     * This is not an enum as some stages can overlap.
     */
    public abstract static class QuicConnectionState {
        /**
         * State flag for a newly created connection.
         */
        public static final int NEW = 0;
        /**
         * State flag indicating that the first Initial packet has been sent.
         */
        public static final int HISENT = 1;
        /**
         * State flag indicating that the handshake completed.
         */
        public static final int HSCOMPLETE = 16;
        /**
         * State flag for the RFC 9000 closing state.
         */
        public static final int CLOSING = 128;
        /**
         * State flag for the RFC 9000 draining state.
         */
        public static final int DRAINING = 256;
        /**
         * State flag indicating that CONNECTION_CLOSE was acknowledged or received.
         */
        public static final int CLOSED = 512;

        /**
         * Creates a state view.
         */
        protected QuicConnectionState() {
        }

        /**
         * Returns whether a state value contains the supplied mask.
         *
         * @param state state value to test
         * @param mask  mask to test
         * @return {@code true} if all bits in the mask are set, or both values are zero
         */
        public static boolean isMarked(int state, int mask) {
            return mask == 0 ? state == 0 : (state & mask) == mask;
        }

        /**
         * Returns a human-readable label for the supplied state value.
         *
         * @param state state value to format
         * @return text representation of the state
         */
        public static String toString(int state) {
            if (state == NEW) {
                return "new";
            }
            if (isMarked(state, CLOSED)) {
                return "closed";
            }
            if (isMarked(state, DRAINING)) {
                return "draining";
            }
            if (isMarked(state, CLOSING)) {
                return "closing";
            }
            if (isMarked(state, HSCOMPLETE)) {
                return "handshakeComplete";
            }
            if (isMarked(state, HISENT)) {
                return "helloSent";
            }
            return "Unknown(" + state + ")";
        }

        /**
         * Returns the current state bit mask.
         *
         * @return current state bit mask
         */
        public abstract int state();

        /**
         * Returns whether the first Initial flight has been sent.
         *
         * @return {@code true} if the hello-sent bit is set
         */
        public boolean helloSent() {
            return isMarked(HISENT);
        }

        /**
         * Returns whether the handshake completed.
         *
         * @return {@code true} if the handshake-complete bit is set
         */
        public boolean handshakeComplete() {
            return isMarked(HSCOMPLETE);
        }

        /**
         * Returns whether the connection is in the closing state.
         *
         * @return {@code true} if the closing bit is set
         */
        public boolean closing() {
            return isMarked(CLOSING);
        }

        /**
         * Returns whether the connection is in the draining state.
         *
         * @return {@code true} if the draining bit is set
         */
        public boolean draining() {
            return isMarked(DRAINING);
        }

        /**
         * Returns whether the connection is still open for normal processing.
         *
         * @return {@code true} if the connection is neither closing, draining, nor closed
         */
        public boolean opened() {
            return (state() & (CLOSED | DRAINING | CLOSING)) == 0;
        }

        /**
         * Returns whether the current state contains the supplied mask.
         *
         * @param mask mask to test
         * @return {@code true} if all bits in the mask are set
         */
        public boolean isMarked(int mask) {
            return isMarked(state(), mask);
        }

        @Override
        public String toString() {
            return toString(state());
        }
    }

    record ClientConnectionCreation(QuicClientConnection connection, QuicEndpoint endpoint) {
    }

    record EndpointRoutes(List<QuicConnectionId> connectionIds,
                          List<QuicPacketReceiver.PeerResetToken> resetTokens) {
    }

    record PacketSpaces(PacketSpace initial, PacketSpace handshake, PacketSpace app) {
        public static PacketSpaces forConnection(QuicConnectionImpl connection) {
            var initialPktSpaceMgr = new PacketSpaceManager(connection, PacketNumberSpace.INITIAL);
            return new PacketSpaces(initialPktSpaceMgr,
                                    new PacketSpaceManager.HandshakePacketSpaceManager(connection, initialPktSpaceMgr),
                                    new PacketSpaceManager.OneRttPacketSpaceManager(connection));
        }

        public PacketSpace get(PacketNumberSpace pnspace) {
            return switch (pnspace) {
                case INITIAL -> initial();
                case HANDSHAKE -> handshake();
                case APPLICATION -> app();
                default -> throw new IllegalArgumentException(pnspace.text());
            };
        }

        public void close() {
            initial.close();
            handshake.close();
            app.close();
        }

        private QuicOneRttContext oneRttContext() {
            var appPacketSpaceMgr = app();
            return (QuicOneRttContext) appPacketSpaceMgr;
        }
    }

    /**
     * A protection record contains a packet to encrypt, and a datagram that may already
     * contain encrypted packets. The firstPacketOffset indicates the position of the
     * first encrypted packet in the datagram. The packetOffset indicates the position
     * at which this packet will be - or has been - written in the datagram.
     * Before the packet is encrypted and written to the datagram, the packetOffset
     * should be the same as the datagram buffer position.
     * After the packet has been written, the packetOffset should indicate
     * at which position the packet has been written. The datagram position
     * indicates where to write the next packet.
     * <p>
     * Additionally, a {@code ProtectionRecord} may carry some flags indicating the
     * intended usage of the datagram. The following flags are supported:
     * <ul>
     *    <li>{@link #SINGLE_PACKET}: the default - it is not expected that the
     *        datagram will contain more packets</li>
     *    <li>{@link #COALESCED}: should be used if it is expected that the
     *        datagram will contain more than one packet</li>
     *    <li>{@link #LAST_PACKET}: should be used in conjunction with {@link #COALESCED}
     *        to indicate that the packet being protected is the last that will be
     *        added to the datagram</li>
     * </ul>
     *
     * @param packet            the packet to encrypt
     * @param datagram          the datagram in which the encrypted packet should be written
     * @param firstPacketOffset the position of the first encrypted packet in the datagram
     * @param packetOffset      the offset at which the packet should be / has been written in the datagram
     * @param flags             a bit mask containing some details about the datagram being sent out
     * <p>Note: Flag values can be combined, but some combinations
     *        may not make sense. A single packet can also be identified as any
     *        packet that doesn't have the {@code COALESCED} bit on.
     *        The flag is used to convey information that may be used to figure
     *        out whether to send the datagram right away, or whether to wait for
     *        more packet to be coalesced inside it.
     */
    record ProtectionRecord(QuicPacket packet, ByteBuffer datagram,
                            int firstPacketOffset, int packetOffset,
                            long retransmittedPacketNumber, int flags,
                            InetSocketAddress destination,
                            QuicPathManager.SendPermit permit) {
        /**
         * This is the default.
         * This protection record is adding a single packet to be sent into
         * the datagram and the datagram can be sent as soon as the packet
         * has been encrypted.
         */
        public static final int SINGLE_PACKET = 0;
        /**
         * This can be used when it is expected that more than one packet
         * will be added to this datagram. We should wait until the last packet
         * has been added before sending the datagram out.
         */
        public static final int COALESCED = 1;
        /**
         * This protection record is adding the last packet to be sent into
         * the datagram and the datagram can be sent as soon as the packet
         * has been encrypted.
         */
        public static final int LAST_PACKET = 2;

        // indicate that the packet is not retransmitted
        private static final long NOT_RETRANSMITTED = -1L;

        /**
         * Records the intent of protecting a packet that will be sent as soon
         * as it has been encrypted, without waiting for more packets to be
         * coalesced into the datagram.
         *
         * @param packet    the packet to protect
         * @param allocator an allocator to allocate the datagram
         * @return a protection record to submit for packet protection
         */
        public static ProtectionRecord single(QuicPacket packet,
                                              Function<QuicPacket, ByteBuffer> allocator) {
            ByteBuffer datagram = allocator.apply(packet);
            int offset = datagram.position();
            return new ProtectionRecord(packet, datagram,
                                        offset, offset, NOT_RETRANSMITTED, 0, null, null);
        }

        public static ProtectionRecord single(QuicPacket packet,
                                              InetSocketAddress destination,
                                              QuicPathManager.SendPermit permit,
                                              Function<QuicPacket, ByteBuffer> allocator) {
            ByteBuffer datagram = allocator.apply(packet);
            int offset = datagram.position();
            return new ProtectionRecord(packet, datagram,
                                        offset, offset, NOT_RETRANSMITTED, 0,
                                        destination, permit);
        }

        /**
         * Records the intent of protecting a packet that retransmits
         * a previously transmitted packet. The packet will be sent as soon
         * as it has been encrypted, without waiting for more packets to be
         * coalesced into the datagram.
         *
         * @param packet                    the packet to protect
         * @param retransmittedPacketNumber the packet number of the original
         *                                 packet that was considered lost
         * @param allocator                 an allocator to allocate the datagram
         * @return a protection record to submit for packet protection
         */
        public static ProtectionRecord retransmitting(QuicPacket packet,
                                                      long retransmittedPacketNumber,
                                                      Function<QuicPacket, ByteBuffer> allocator) {
            ByteBuffer datagram = allocator.apply(packet);
            int offset = datagram.position();
            return new ProtectionRecord(packet, datagram, offset, offset,
                                        retransmittedPacketNumber, 0, null, null);
        }

        public static ProtectionRecord retransmitting(QuicPacket packet,
                                                      long retransmittedPacketNumber,
                                                      InetSocketAddress destination,
                                                      QuicPathManager.SendPermit permit,
                                                      Function<QuicPacket, ByteBuffer> allocator) {
            ByteBuffer datagram = allocator.apply(packet);
            int offset = datagram.position();
            return new ProtectionRecord(packet, datagram, offset, offset,
                                        retransmittedPacketNumber, 0, destination, permit);
        }

        /**
         * Records the intent of protecting a packet that will be followed by
         * more packets to be coalesced in the same datagram. The datagram
         * should not be sent until the last packet has been coalesced.
         *
         * @param packet            the packet to protect
         * @param datagram          the datagram in which packet will be coalesced
         * @param firstPacketOffset the offset of the first packet in the datagram
         * @return a protection record to submit for packet protection
         */
        public static ProtectionRecord more(QuicPacket packet, ByteBuffer datagram, int firstPacketOffset) {
            return new ProtectionRecord(packet, datagram, firstPacketOffset,
                                        datagram.position(), NOT_RETRANSMITTED, COALESCED, null, null);
        }

        /**
         * Records the intent of protecting the last packet that will be
         * coalesced in the given datagram. The datagram can be sent as soon
         * as the packet has been encrypted and coalesced into the given
         * datagram.
         *
         * @param packet            the packet to protect
         * @param datagram          the datagram in which packet will be coalesced
         * @param firstPacketOffset the offset of the first packet in the datagram
         * @return a protection record to submit for packet protection
         */
        public static ProtectionRecord last(QuicPacket packet, ByteBuffer datagram, int firstPacketOffset) {
            return new ProtectionRecord(packet, datagram, firstPacketOffset,
                                        datagram.position(), NOT_RETRANSMITTED, LAST_PACKET | COALESCED, null, null);
        }

        public ProtectionRecord encrypt(CodingContext codingContext)
                throws QuicKeyUnavailableException, QuicTransportException {
            PacketType packetType = packet.packetType();
            // keep track of position before encryption
            int preEncryptPos = datagram.position();
            try {
                codingContext.writePacket(packet, datagram);
            } catch (BufferOverflowException e) {
                throw new QuicTransportException("Encrypted packet exceeds its allocated datagram",
                                                 packetType.keySpace().orElseThrow(),
                                                 0,
                                                 QuicTransportErrors.INTERNAL_ERROR.code(),
                                                 e);
            }
            ProtectionRecord encrypted = withOffset(preEncryptPos);
            return encrypted;
        }

        ProtectionRecord withOffset(int packetOffset) {
            if (this.packetOffset == packetOffset) {
                return this;
            }
            return new ProtectionRecord(packet, datagram, firstPacketOffset,
                                        packetOffset, retransmittedPacketNumber, flags, destination, permit);
        }

        ProtectionRecord withPath(InetSocketAddress destination, QuicPathManager.SendPermit permit) {
            return new ProtectionRecord(packet, datagram, firstPacketOffset,
                                        packetOffset, retransmittedPacketNumber, flags, destination, permit);
        }
    }

    static final class ReassemblyBudgetOwner implements AutoCloseable {
        private final ReentrantLock lock = new ReentrantLock();
        private int retained;
        private boolean accepting = true;

        ReassemblyBudget newBudget() {
            return new Budget();
        }

        @Override
        public void close() {
            lock.lock();
            try {
                accepting = false;
            } finally {
                lock.unlock();
            }
        }

        private final class Budget implements ReassemblyBudget {
            private int retained;
            private boolean accepting = true;

            @Override
            public boolean tryAcquire() {
                lock.lock();
                try {
                    if (!ReassemblyBudgetOwner.this.accepting
                            || !accepting
                            || retained >= MAX_REASSEMBLY_NODES_PER_FLOW
                            || ReassemblyBudgetOwner.this.retained >= MAX_REASSEMBLY_NODES_PER_CONNECTION) {
                        return false;
                    }
                    retained++;
                    ReassemblyBudgetOwner.this.retained++;
                    return true;
                } finally {
                    lock.unlock();
                }
            }

            @Override
            public void release(int count) {
                if (count < 0) {
                    throw new IllegalArgumentException("Reassembly permit release must not be negative: " + count);
                }
                lock.lock();
                try {
                    if (count > retained) {
                        throw new IllegalStateException("Releasing " + count + " reassembly permits with only "
                                                                + retained + " retained");
                    }
                    retained -= count;
                    ReassemblyBudgetOwner.this.retained -= count;
                } finally {
                    lock.unlock();
                }
            }

            @Override
            public void close() {
                lock.lock();
                try {
                    accepting = false;
                } finally {
                    lock.unlock();
                }
            }
        }
    }

    private static final class ClientConnection extends QuicConnectionImpl implements QuicClientConnection {
        private ClientConnection(QuicInstance quicInstance,
                                 QuicRuntimeConfig runtimeConfig,
                                 InetSocketAddress peerAddress,
                                 InetSocketAddress tlsPeer,
                                 SSLParameters sslParameters,
                                 Duration initialResponseTimeout,
                                 long labelId) {
            super(quicInstance,
                  runtimeConfig,
                  peerAddress,
                  tlsPeer.getHostString(),
                  tlsPeer.getPort(),
                  sslParameters,
                  initialResponseTimeout,
                  "QuicClientConnection(%s)",
                  labelId);
        }
    }

    private static final class PathSendBlockedException extends IllegalStateException {
        private PathSendBlockedException(String message) {
            super(message);
        }
    }

    private static final class IncomingDatagram extends QuicPathManager.ReceiveContext {
        private final ByteBuffer destConnId;
        private final QuicPacket.HeadersType headersType;
        private final ByteBuffer buffer;

        private IncomingDatagram(InetSocketAddress source,
                                 ByteBuffer destConnId,
                                 QuicPacket.HeadersType headersType,
                                 ByteBuffer buffer) {
            super(source, buffer.remaining());
            this.destConnId = destConnId;
            this.headersType = headersType;
            this.buffer = buffer;
        }

        private ByteBuffer destConnId() {
            return destConnId;
        }

        private QuicPacket.HeadersType headersType() {
            return headersType;
        }

        private ByteBuffer buffer() {
            return buffer;
        }
    }

    /**
     * A state handle is a mutable implementation of {@link QuicConnectionState}
     * that allows to view the volatile connection int variable {@code state} as
     * a {@code QuicConnectionState}, and provides methods to mutate it in
     * a thread safe atomic way.
     */
    protected final class StateHandle extends QuicConnectionState {
        /**
         * Creates a state handle backed by this connection's volatile state field.
         */
        protected StateHandle() {
        }

        @Override
        public int state() {
            return state;
        }

        /**
         * Marks that the first Initial flight has been sent.
         *
         * @return {@code true} if the bit changed from unset to set
         */
        public boolean markHelloSent() {
            return mark(HISENT);
        }

        /**
         * Marks that the handshake completed.
         *
         * @return {@code true} if the bit changed from unset to set
         */
        public boolean markHandshakeComplete() {
            return mark(HSCOMPLETE);
        }

        /**
         * Updates the state to a new state value with the passed bit {@code mask} set.
         *
         * @param mask The state mask
         * @return true if previously the state value didn't have the {@code mask} set and this
         *        method successfully updated the state value to set the {@code mask}
         */
        boolean mark(int mask) {
            int state;
            int desired;
            do {
                state = state();
                desired = state;
                if ((state & mask) == mask) {
                    return false; // already set
                }
                desired = state | mask;
            } while (!STATE.compareAndSet(QuicConnectionImpl.this, state, desired));
            return true; // compareAndSet switched the old state to the desired state
        }
    }

    /**
     * Keeps track of handshake state.
     * <p>
     * - handshakeCF   the handshake completable future
     * - localInitial  the local initial crypto writer queue
     * - peerInitial   the peer initial crypto flow
     * - localHandshake the local handshake crypto queue
     * - peerHandshake the peer handshake crypto flow
     */
    protected final class HandshakeFlow {

        // a CompletableFuture which will get completed when the handshake initiated locally,
        // has "reached" the peer i.e. when the peer acknowledges or replies to the first
        // INITIAL packet sent by an endpoint
        private final CompletableFuture<Void> handshakeReachedPeerCF;
        private final CompletableFuture<HandshakeState> handshakeCF;
        private final CryptoWriterQueue localInitial = CryptoWriterQueue.create();
        private final ReassemblyBudget peerInitialBudget = newReassemblyBudget();
        private final CryptoDataFlow peerInitial = CryptoDataFlow.create(peerInitialBudget, KeySpace.INITIAL);
        private final CryptoWriterQueue localHandshake = CryptoWriterQueue.create();
        private final ReassemblyBudget peerHandshakeBudget = newReassemblyBudget();
        private final CryptoDataFlow peerHandshake = CryptoDataFlow.create(peerHandshakeBudget, KeySpace.HANDSHAKE);
        private final AtomicBoolean handshakeStarted = new AtomicBoolean();

        private HandshakeFlow() {
            this.handshakeCF = MinimalFuture.<HandshakeState>create();
            this.handshakeReachedPeerCF = MinimalFuture.<Void>create();
            // ensure that the handshakeReachedPeerCF gets completed exceptionally
            // if an exception is raised before the first INITIAL packet is
            // acked by the peer.
            handshakeCF.whenComplete((r, t) -> {
                logDebug("handshake completed %s",
                           t == null ? "successfully" : "exceptionally");
                if (t != null) {
                    handshakeReachedPeerCF.completeExceptionally(t);
                }
            });
        }

        /**
         * Returns the CompletableFuture representing a handshake.
         *
         * @return the CompletableFuture representing a handshake.
         */
        public CompletableFuture<HandshakeState> handshakeCF() {
            return this.handshakeCF;
        }

        /**
         * Fails the handshake futures with the supplied cause.
         *
         * @param cause cause of the handshake failure
         */
        public void failHandshakeCFs(Throwable cause) {
            QuicConnectionException connectionException = null;
            if (!handshakeCF.isDone()) {
                connectionException = connectionException(cause);
                handshakeCF.completeExceptionally(connectionException);
            }
            if (!handshakeReachedPeerCF.isDone()) {
                if (connectionException == null) {
                    connectionException = connectionException(cause);
                }
                handshakeReachedPeerCF.completeExceptionally(connectionException);
            }
        }

        private QuicConnectionException connectionException(Throwable cause) {
            if (cause instanceof QuicConnectionException connectionException) {
                return connectionException;
            }
            return new QuicConnectionException("QUIC connection establishment failed", cause);
        }

        /**
         * Marks the start of a handshake.
         *
         * @throws IllegalStateException If handshake has already started
         */
        private void markHandshakeStart() {
            if (!handshakeStarted.compareAndSet(false, true)) {
                throw new IllegalStateException("Handshake has already started on "
                                                        + QuicConnectionImpl.this.logTag());
            }
        }
    }

    /**
     * Connection-local implementation of {@link CodingContext}.
     */
    protected class QuicCodingContext implements CodingContext {
        /**
         * Creates a coding context backed by this connection.
         */
        protected QuicCodingContext() {
        }

        @Override
        public long largestProcessedPN(PacketNumberSpace packetSpace) {
            return QuicConnectionImpl.this.largestProcessedPN(packetSpace);
        }

        @Override
        public long largestAckedPN(PacketNumberSpace packetSpace) {
            return QuicConnectionImpl.this.largestAckedPN(packetSpace);
        }

        @Override
        public int connectionIdLength() {
            return QuicConnectionImpl.this.connectionIdLength();
        }

        @Override
        public int maxAckRangesPerFrame() {
            return quicConfig.maxAckRangesPerFrame();
        }

        @Override
        public int writePacket(QuicPacket packet, ByteBuffer buffer)
                throws QuicKeyUnavailableException, QuicTransportException {
            int start = buffer.position();
            encoder.encode(packet, buffer, this, QuicConnectionImpl.this.logTag());
            return buffer.position() - start;
        }

        @Override
        public Optional<QuicPacket> parsePacket(ByteBuffer src)
                throws QuicKeyUnavailableException, QuicTransportException {
            return decoder.decodeOwned(src, this, QuicConnectionImpl.this.logTag())
                    .map(QuicPacket.class::cast);
        }

        @Override
        public QuicConnectionId originalServerConnId() {
            return QuicConnectionImpl.this.originalServerConnId();
        }

        @Override
        public QuicTLSEngine tlsEngine() {
            return quicTLSEngine;
        }

        @Override
        public String logTag() {
            return QuicConnectionImpl.this.logTag();
        }

        @Override
        public boolean unsafeRawData() {
            return quicConfig.unsafeRawData();
        }

        @Override
        public boolean verifyToken(QuicConnectionId destinationID, byte[] token) {
            return QuicConnectionImpl.this.verifyToken(destinationID, token);
        }
    }

    /**
     * Timer that drives connection path validation retries and reports a
     * {@link QuicTransportErrors#NO_VIABLE_PATH NO_VIABLE_PATH} transport error
     * when no usable path remains.
     */
    final class PathValidationTimer implements QuicTimedEvent {
        private final long eventId = QuicTimerQueue.newEventId();
        private final ReentrantLock lock = new ReentrantLock();
        private volatile Deadline deadline = Deadline.MAX;
        private volatile Deadline updatedDeadline = Deadline.MAX;
        private boolean stopped;

        @Override
        public long eventId() {
            return eventId;
        }

        @Override
        public Deadline deadline() {
            return deadline;
        }

        @Override
        public Deadline handle() {
            lock.lock();
            try {
                if (stopped) {
                    return Deadline.MAX;
                }
                QuicPathManager.TimeoutResult result = pathManager.validationTimedOut(System.nanoTime());
                discardRetiredPathControlFlights();
                if (result.pathFailed()) {
                    deadline = Deadline.MAX;
                    updatedDeadline = Deadline.MAX;
                    terminator.terminate(transport(NO_VIABLE_PATH, "QUIC path validation failed"));
                    return Deadline.MAX;
                }
                if (result.pathChanged()) {
                    resetForPathChange(result.generation());
                }
                packetSpaces.app.runTransmitter();
                OptionalLong next = pathManager.nextValidationDeadlineNanos();
                if (next.isEmpty()) {
                    updatedDeadline = Deadline.MAX;
                } else {
                    long delay = Math.max(0, next.orElseThrow() - System.nanoTime());
                    updatedDeadline = TimeSource.now().plusNanos(delay);
                }
                deadline = updatedDeadline;
                return updatedDeadline;
            } finally {
                lock.unlock();
            }
        }

        @Override
        public Deadline refreshDeadline() {
            lock.lock();
            try {
                deadline = updatedDeadline;
                return deadline;
            } finally {
                lock.unlock();
            }
        }

        private void schedule(Deadline newDeadline) {
            lock.lock();
            try {
                if (stopped) {
                    return;
                }
                updatedDeadline = newDeadline;
                endpoint.timer().reschedule(this, newDeadline);
            } finally {
                lock.unlock();
            }
        }

        private void stop() {
            lock.lock();
            try {
                if (stopped) {
                    return;
                }
                stopped = true;
                updatedDeadline = Deadline.MAX;
                endpoint.timer().reschedule(this, Deadline.MAX);
            } finally {
                lock.unlock();
            }
        }
    }

    /**
     * A {@link QuicTimedEvent} used to interrupt the handshake if no response
     * to the first Initial packet is received within a reasonable delay. This
     * timer is used only on the client side.
     */
    final class MaxInitialTimer implements QuicTimedEvent {
        private final Deadline maxInitialDeadline;
        private final Duration responseTimeout;
        private final QuicTimerQueue timerQueue;
        private final long eventId;
        private volatile Deadline deadline;
        private volatile boolean initialPacketReceived;
        private volatile boolean connectionClosed;

        // optimization: if done is true it avoids volatile read
        // of initialPacketReceived and/or connectionClosed
        // from initialPacketReceived()
        private boolean done;

        private MaxInitialTimer(QuicTimerQueue timerQueue, Deadline maxDeadline, Duration responseTimeout) {
            this.eventId = QuicTimerQueue.newEventId();
            this.timerQueue = timerQueue;
            maxInitialDeadline = maxDeadline;
            this.responseTimeout = responseTimeout;
            deadline = maxDeadline;
        }

        @Override
        public Deadline deadline() {
            return deadline;
        }

        /**
         * This method is called if the timer expires.
         * If no initial packet has been received (
         * {@link #initialPacketReceived()} was never called),
         * the connection's handshake future is completed exceptionally.
         * Calling this method a second time is a no-op.
         *
         * @return {@link Deadline#MAX}, always.
         */
        @Override
        public Deadline handle() {
            if (done) {
                return Deadline.MAX;
            }
            boolean firsPacketReceived = initialPacketReceived;
            boolean closed = connectionClosed;
            if (!firsPacketReceived && !closed) {
                var connectException = new QuicConnectionException(
                        "No response from peer after %s".formatted(responseTimeout));
                if (QuicConnectionImpl.this.handshakeFlow.handshakeCF()
                        .completeExceptionally(connectException)) {
                    // abandon the connection, but sends ConnectionCloseFrame
                    QuicCloseCommand command = QuicCloseCommand.transport(
                            new QuicTransportException(connectException.getMessage(),
                                                       KeySpace.INITIAL, 0, QuicTransportErrors.APPLICATION_ERROR));
                    terminator.terminate(command);
                }
                connectionClosed = true;
                done = true;
                closed = true;
            }
            return Deadline.MAX;
        }

        @Override
        public long eventId() {
            return eventId;
        }

        @Override
        public Deadline refreshDeadline() {
            boolean firstPacketReceived = initialPacketReceived;
            boolean closed = connectionClosed;
            Deadline newDeadline = deadline;
            if (closed || firstPacketReceived) {
                newDeadline = Deadline.MAX;
                deadline = Deadline.MAX;
            }
            return newDeadline;
        }

        /**
         * Called when an initial packet is received from the
         * peer. At this point the MaxInitialTimer is disarmed,
         * and further calls to this method are no-op.
         */
        void initialPacketReceived() {
            if (done) {
                return; // races are OK - avoids volatile read
            }
            boolean firsPacketReceived = initialPacketReceived;
            boolean closed = connectionClosed;
            done = firsPacketReceived || closed;
            if (done) {
                return;
            }
            initialPacketReceived = true;
            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                logDebug("QUIC initial timer disarmed after %s",
                          responseTimeout.minus(Deadline.between(now(), maxInitialDeadline)));
            }
            if (!closed) {
                // rescheduling with Deadline.MAX will take the
                // MaxInitialTimer out of the timer queue.
                timerQueue.reschedule(this, Deadline.MAX);
            }
        }

        private Deadline now() {
            return TimeSource.now();
        }
    }

    /**
     * A class used to check that 1-RTT received data doesn't exceed
     * the MAX_DATA of the connection.
     */
    class OneRttFlowControlledReceivingQueue {
        private static final long MIN_BUFFER_SIZE = 16L << 10; // 16k
        // Desired buffer size; used when updating maxStreamData
        private final long desiredBufferSize;
        private final Supplier<String> logTag;
        private final ReentrantLock stateLock = new ReentrantLock();
        private volatile long receivedData;
        private volatile long maxData;
        private volatile long processedData;

        OneRttFlowControlledReceivingQueue(Supplier<String> logTag, long defaultInitialMaxData) {
            this.logTag = Objects.requireNonNull(logTag);
            this.desiredBufferSize = Math.clamp(defaultInitialMaxData, MIN_BUFFER_SIZE, MAX_VL_INTEGER);
        }

        public void increaseProcessedData(long diff) {
            long processed;
            long received;
            long max;
            stateLock.lock();
            try {
                processedData += diff;
                processed = processedData;
                received = receivedData;
                max = maxData;
            } finally {
                stateLock.unlock();
            }
            if (LOGGER.isLoggable(System.Logger.Level.TRACE)) {
                QuicConnectionImpl.this.log(LOGGER,
                                            System.Logger.Level.TRACE,
                                            "Processed: %s, received: %s, max:%s",
                                            processed,
                                            received,
                                            max);
            }
            if (needSendMaxData()) {
                runAppPacketSpaceTransmitter();
            }
        }

        public boolean needSendMaxData() {
            return maxData - processedData < desiredBufferSize / 2;
        }

        /**
         * Called when new local parameters are available.
         *
         * @param localParameters the new local paramaters
         */
        void newLocalParameters(QuicTransportParameters localParameters) {
            if (localParameters.isPresent(ParameterId.initial_max_data)) {
                long maxData = this.maxData;
                long newMaxData = localParameters.intParameter(ParameterId.initial_max_data);
                while (maxData < newMaxData) {
                    if (MAX_RCV_DATA.compareAndSet(this, maxData, newMaxData)) {
                        break;
                    }
                    maxData = this.maxData;
                }
            }
        }

        /**
         * Checks whether the give frame would cause the connection max data.
         * to be exceeded. If no, increase the amount of data processed by
         * this connection by the length of the frame. If yes, sends a
         * ConnectionCloseFrame with FLOW_CONTROL_ERROR.
         *
         * @param diff      number of bytes newly received
         * @param frameType type of frame received
         * @throws QuicTransportException if processing this frame would cause the connection
         *                               max data to be exceeded
         */
        void checkAndIncreaseReceivedData(long diff, long frameType) throws QuicTransportException {
            long max;
            long processed;
            boolean exceeded;
            stateLock.lock();
            try {
                max = maxData;
                processed = receivedData;
                if (max - processed < diff) {
                    exceeded = true;
                } else {
                    try {
                        processed = Math.addExact(processed, diff);
                        receivedData = processed;
                        exceeded = false;
                    } catch (ArithmeticException x) {
                        // should not happen - flow control should have
                        // caught that
                        processed = Long.MAX_VALUE;
                        receivedData = Long.MAX_VALUE;
                        exceeded = true;
                    }
                }
            } finally {
                stateLock.unlock();
            }
            if (exceeded) {
                String reason = "Connection max data exceeded: max data processed=%s, max connection data=%s"
                        .formatted(processed, max);
                throw new QuicTransportException(reason,
                                                 QuicTLSEngine.KeySpace.ONE_RTT,
                                                 frameType,
                                                 QuicTransportErrors.FLOW_CONTROL_ERROR);
            }
        }

        String logTag() {
            return logTag.get();
        }

        private long bumpMaxData() {
            long newMaxData = processedData + desiredBufferSize;
            long maxData = this.maxData;
            if (newMaxData - maxData < (desiredBufferSize / 5)) {
                return 0;
            }
            while (maxData < newMaxData) {
                if (MAX_RCV_DATA.compareAndSet(this, maxData, newMaxData)) {
                    return newMaxData;
                }
                maxData = this.maxData;
            }
            return 0;
        }
    }

    /**
     * An event loop triggered when stream data is available for sending.
     * We use a sequential scheduler here to make sure we don't send
     * more data than allowed by the connection's flow control.
     * This guarantee that only one thread composes flow controlled
     * OneRTT packets at a given time, which in turn guarantees that the
     * credit computed at the beginning of the loop will still be
     * available after the packet has been composed.
     */
    class OneRttFlowControlledSendingQueue {
        private final ReentrantLock stateLock = new ReentrantLock();
        private volatile long dataProcessed;
        private volatile long maxData;

        /**
         * Called when a MAX_DATA frame is received.
         * This method is a no-op if the given value is less than the
         * current max stream data for the connection.
         *
         * @param maxData   the maximum data offset that the peer is prepared
         *                 to accept for the whole connection
         * @param isInitial true when processing transport parameters,
         *                 false when processing MaxDataFrame
         * @return the actual max data after taking the given value into account
         */
        public long updateMaxData(long maxData, boolean isInitial) {
            long max;
            long processed;
            boolean wasblocked;
            boolean unblocked = false;
            do {
                stateLock.lock();
                try {
                    max = this.maxData;
                    processed = dataProcessed;
                } finally {
                    stateLock.unlock();
                }
                wasblocked = max <= processed;
                if (max < maxData) {
                    if (MAX_SND_DATA.compareAndSet(this, max, maxData)) {
                        max = maxData;
                        unblocked = (wasblocked && max > processed);
                    }
                }
            } while (max < maxData);
            if (unblocked && !isInitial) {
                packetSpaces.app.runTransmitter();
            }
            return max;
        }

        /**
         * Returns the remaining credit for this connection.
         *
         * @return the remaining credit for this connection.
         */
        public long credit() {
            stateLock.lock();
            try {
                return maxData - dataProcessed;
            } finally {
                stateLock.unlock();
            }
        }

        // We can continue sending if we have credit and data is available to send
        private boolean canSend() {
            long availableCredit = credit();
            return availableCredit > 0 && streams.hasAvailableData()
                    || availableCredit <= 0 && streams.hasZeroCreditSendableData()
                    || streams.hasControlFrames()
                    || hasQueuedFrames()
                    || pathManager.hasPendingProbe()
                    || oneRttRcvQueue.needSendMaxData();
        }

        private PacketType applicationPacketTypeCandidate() {
            return PacketType.ONERTT;
        }

        private int computeMaxApplicationPayloadSize(PacketType packetType,
                                                     long packetNumber,
                                                     int dstIdLength,
                                                     int maxDatagramSize,
                                                     long largestPeerAckedPN) {
            return switch (packetType) {
                case ONERTT -> QuicPacketEncoder.computeMaxOneRTTPayloadSize(
                        codingContext,
                        packetNumber,
                        dstIdLength,
                        maxDatagramSize,
                        largestPeerAckedPN);
                default -> throw new IllegalArgumentException("Unsupported application packet type: " + packetType);
            };
        }

        // implementation of the sending loop.
        private boolean send1RTTData() {
            return send1RTTData(false);
        }

        private boolean sendPriority1RTTData() {
            return send1RTTData(true);
        }

        private boolean send1RTTData(boolean priorityResponseOnly) {
            Throwable failure;
            streamDispatchLock.lock();
            try {
                return doSend1RTTData(priorityResponseOnly);
            } catch (Throwable t) {
                failure = t;
            } finally {
                streamDispatchLock.unlock();
            }
            if (failure instanceof QuicKeyUnavailableException qkue) {
                if (!QuicConnectionImpl.this.stateHandle().opened()) {
                    // connection is already being closed and that explains the
                    // key unavailability (they might have been discarded). just log
                    // and return
                    if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                        logDebug("failed to send stream data, reason: " + qkue.getMessage());
                    }
                    return false;
                }
                // Connection is still open but its packet-protection keys unexpectedly disappeared.
                failure = new IllegalStateException("QUIC packet-protection keys are unavailable", qkue);
            }
            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                logDebug("failed to send stream data", failure);
            }
            // close the connection to make sure it's not just ignored
            terminator.terminate(QuicCloseCommand.transport(failure));
            return false;
        }

        private boolean doSend1RTTData(boolean priorityResponseOnly)
                throws QuicKeyUnavailableException, QuicTransportException {
            PacketSpace space = packetSpace(PacketNumberSpace.APPLICATION);
            if (!stateHandle().opened()) {
                return false;
            }
            int maxDatagramSize = maxDatagramSize();
            HandshakeState handshakeState = quicTLSEngine.handshakeState();
            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                logDebug("send1RTTData entry: state=%s, canSend=%s, queuedFrames=%d, hasStreamData=%s",
                          handshakeState, canSend(),
                          outgoing1RTTFrames.size(),
                          streams.hasAvailableData());
            }
            logDebugIf(handshakeState == HandshakeState.NEED_SEND_HANDSHAKE_DONE,
                       "application sender entered with TLS state NEED_SEND_HANDSHAKE_DONE, queuedFrames=%s, hasStreamData=%s",
                       outgoing1RTTFrames.size(), streams.hasAvailableData());
            if (!canSend()) {
                return false;
            }
            int probeSize = Math.min(maxDatagramSize, SMALLEST_MAXIMUM_DATAGRAM_SIZE);
            if (priorityResponseOnly) {
                Optional<QuicPathManager.ProbeSend> pendingResponse = pathManager.pollSendableResponse(probeSize);
                if (pendingResponse.isEmpty()) {
                    return false;
                }
                return sendProbe(space, pendingResponse.orElseThrow());
            }
            Optional<QuicPathManager.ProbeSend> pendingProbe = pathManager.pollSendableProbe(probeSize);
            if (pendingProbe.isPresent()) {
                return sendProbe(space, pendingProbe.orElseThrow());
            }

            Optional<QuicPathManager.SendPermit> reservation = pathManager.reserve(maxDatagramSize);
            if (reservation.isEmpty()) {
                return false;
            }
            QuicPathManager.SendPermit permit = reservation.orElseThrow();
            boolean transferred = false;
            try {
                Optional<PeerConnIdManager.PathCidBinding> binding = pathManager.cidBinding(permit);
                if (binding.isEmpty()) {
                    return false;
                }
                QuicConnectionId peerConnectionId = binding.orElseThrow().connectionId();
                int dstIdLength = peerConnectionId.length();
                maxDatagramSize = permit.size();
                long packetNumber = space.allocateNextPN();
                long largestPeerAckedPN = space.largestPeerAcknowledgedPacketNumber();
                PacketType packetType = applicationPacketTypeCandidate();
                int remaining = computeMaxApplicationPayloadSize(packetType,
                                                                 packetNumber,
                                                                 dstIdLength,
                                                                 maxDatagramSize,
                                                                 largestPeerAckedPN);
                if (remaining == 0) {
                    return false;
                }
                List<QuicFrame> frames = new ArrayList<>();
                remaining -= addConnectionControlFrames(remaining, frames, binding.orElseThrow().sequence());
                long produced = streams.produceFramesToSend(encoder, remaining, credit(), frames);
                if (frames.isEmpty()) {
                    // produced cannot be > 0 unless there are some frames to send
                    return false;
                }
                // non-atomic operation should be OK since sendStreamData0 is called
                // only from the sending loop, and this is the only place where we
                // mutate dataProcessed.
                dataProcessed += produced;
                QuicPacket packet = encoder.newOneRttPacket(peerConnectionId,
                                                            packetNumber,
                                                            largestPeerAckedPN,
                                                            frames,
                                                            codingContext,
                                                            logTag());
                if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                    logDebug("send1RTTData sending %s pn=%s frames=%s tlsState=%s",
                              packetType, packetNumber, frames, handshakeState);
                }
                logDebugIf(handshakeState == HandshakeState.NEED_SEND_HANDSHAKE_DONE,
                           "sending %s packet while TLS state is NEED_SEND_HANDSHAKE_DONE; frames=%s",
                           packetType,
                           frames);
                transferred = true;
                QuicConnectionImpl.this.sendApplicationPacket(packet, permit);
                streams.streamFramesDispatched(frames);
                return true;
            } finally {
                if (!transferred) {
                    permit.release();
                }
                QuicConnectionImpl.this.discardRetiredPathControlFlights();
            }
        }

        private boolean sendProbe(PacketSpace space, QuicPathManager.ProbeSend probeSend)
                throws QuicKeyUnavailableException, QuicTransportException {
            QuicPathManager.Probe probe = probeSend.probe();
            QuicPathManager.SendPermit permit = probeSend.permit();
            QuicConnectionId peerConnectionId = probeSend.binding().connectionId();
            boolean transferred = false;
            try {
                long packetNumber = space.allocateNextPN();
                long largestPeerAckedPN = space.largestPeerAcknowledgedPacketNumber();
                List<QuicFrame> frames = new ArrayList<>();
                frames.add(probe.frame());
                if (probe.ping()) {
                    frames.add(PingFrame.create());
                }
                QuicPacket packet = encoder.newOneRttPacket(peerConnectionId,
                                                            packetNumber,
                                                            largestPeerAckedPN,
                                                            frames,
                                                            codingContext,
                                                            logTag());
                int targetSize = permit.size();
                boolean padToMinimum = probe.padToMinimum() && targetSize >= SMALLEST_MAXIMUM_DATAGRAM_SIZE;
                if (packet.size() > targetSize) {
                    return !pathManager.requeue(probe);
                }
                if (padToMinimum && packet.size() < targetSize) {
                    int paddingSize = targetSize - packet.size();
                    for (int attempt = 0; attempt < 3; attempt++) {
                        frames = new ArrayList<>();
                        frames.add(probe.frame());
                        if (probe.ping()) {
                            frames.add(PingFrame.create());
                        }
                        frames.add(PaddingFrame.create(paddingSize));
                        packet = encoder.newOneRttPacket(peerConnectionId,
                                                        packetNumber,
                                                        largestPeerAckedPN,
                                                        frames,
                                                        codingContext,
                                                        logTag());
                        int adjustment = targetSize - packet.size();
                        if (adjustment == 0) {
                            break;
                        }
                        paddingSize += adjustment;
                        if (paddingSize <= 0) {
                            return !pathManager.requeue(probe);
                        }
                    }
                }
                if (packet.size() > permit.size() || padToMinimum && packet.size() != targetSize) {
                    return !pathManager.requeue(probe);
                }
                if (!pathManager.probeSent(probe, packet.size(), System.nanoTime())) {
                    return true;
                }
                transferred = true;
                QuicConnectionImpl.this.sendApplicationPacket(packet, permit);
                schedulePathValidation();
                return true;
            } finally {
                if (!transferred) {
                    permit.release();
                }
                QuicConnectionImpl.this.discardRetiredPathControlFlights();
            }
        }

        /**
         * Produces connection-level control frames for sending in the next one-rtt
         * packet. The frames are added to the provided list.
         *
         * @param maxAllowedBytes maximum number of bytes the method is allowed to add
         * @param frames          list where the frames are added
         * @param destinationConnectionIdSequence sequence number of the packet destination connection ID
         * @return number of bytes added
         */
        private int addConnectionControlFrames(int maxAllowedBytes,
                                               List<QuicFrame> frames,
                                               long destinationConnectionIdSequence) {
            int added = 0;
            int remaining = maxAllowedBytes;
            QuicFrame f;
            f = outgoing1RTTFrames.peek();
            while (f != null) {
                if (!f.isValidIn(PacketType.ONERTT)) {
                    break;
                }
                int frameSize = f.size();
                if (frameSize <= remaining) {
                    outgoing1RTTFrames.remove();
                    frames.add(f);
                    added += frameSize;
                    remaining -= frameSize;
                } else {
                    break;
                }
                f = outgoing1RTTFrames.peek();
            }
            // NEW_CONNECTION_ID
            f = localConnIdManager.nextFrame(remaining);
            while (f != null) {
                int frameSize = f.size();
                frames.add(f);
                added += frameSize;
                remaining -= frameSize;
                f = localConnIdManager.nextFrame(remaining);
            }
            // RETIRE_CONNECTION_ID
            f = peerConnIdManager.nextFrame(remaining, destinationConnectionIdSequence);
            while (f != null) {
                int frameSize = f.size();
                frames.add(f);
                added += frameSize;
                remaining -= frameSize;
                f = peerConnIdManager.nextFrame(remaining, destinationConnectionIdSequence);
            }

            if (remaining == 0) {
                return added;
            }
            PacketSpace space = packetSpace(PacketNumberSpace.APPLICATION);
            AckFrame ack = space.nextAckFrame(false, remaining).orElse(null);
            if (ack != null) {
                int ackFrameSize = ack.size();
                if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                    logDebug("Adding AckFrame");
                }
                frames.add(ack);
                added += ackFrameSize;
                remaining -= ackFrameSize;
            }
            long credit = credit();
            if (credit < remaining && remaining > 10) {
                if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                    logDebug("Adding DataBlockedFrame");
                }
                DataBlockedFrame dbf = DataBlockedFrame.create(maxData);
                frames.add(dbf);
                added += dbf.size();
                remaining -= dbf.size();
            }
            // max data
            if (remaining > 10) {
                long maxData = oneRttRcvQueue.bumpMaxData();
                if (maxData != 0) {
                    if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                        logDebug("Adding MaxDataFrame (processed: %s)",
                                  oneRttRcvQueue.processedData);
                    }
                    MaxDataFrame mdf = MaxDataFrame.create(maxData);
                    frames.add(mdf);
                    added += mdf.size();
                    remaining -= mdf.size();
                }
            }
            // session ticket
            if (quicTLSEngine.currentSendKeySpace() == KeySpace.ONE_RTT) {
                packetTLSEngine().handshakeBytesBuffer(KeySpace.ONE_RTT)
                        .ifPresent(localCryptoFlow::enqueue);
                if (localCryptoFlow.remaining() > 0 && remaining > 3) {
                    CryptoFrame frame = localCryptoFlow.produceFrame(remaining).orElse(null);
                    if (frame != null) {
                        if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                            logDebug("Adding CryptoFrame");
                        }
                        frames.add(frame);
                        added += frame.size();
                        remaining -= frame.size();
                    }
                }
            }
            return added;
        }
    }

    private final class ConnectionPeerInfo implements PeerInfo {
        private final boolean remote;

        private ConnectionPeerInfo(boolean remote) {
            this.remote = remote;
        }

        @Override
        public SocketAddress address() {
            return remote ? peerAddress() : localAddress();
        }

        @Override
        public String host() {
            SocketAddress address = address();
            if (address instanceof InetSocketAddress inetAddress) {
                return inetAddress.getHostString();
            }
            return address.toString();
        }

        @Override
        public int port() {
            SocketAddress address = address();
            return address instanceof InetSocketAddress inetAddress ? inetAddress.getPort() : -1;
        }

        @Override
        public Optional<Principal> tlsPrincipal() {
            SSLSession session = quicTLSEngine.session();
            if (session == null) {
                return Optional.empty();
            }
            if (!remote) {
                return Optional.ofNullable(session.getLocalPrincipal());
            }
            try {
                return Optional.ofNullable(session.getPeerPrincipal());
            } catch (SSLPeerUnverifiedException e) {
                return Optional.empty();
            }
        }

        @Override
        public Optional<Certificate[]> tlsCertificates() {
            SSLSession session = quicTLSEngine.session();
            if (session == null) {
                return Optional.empty();
            }
            Certificate[] certificates;
            if (remote) {
                try {
                    certificates = session.getPeerCertificates();
                } catch (SSLPeerUnverifiedException e) {
                    return Optional.empty();
                }
            } else {
                certificates = session.getLocalCertificates();
            }
            return certificates == null ? Optional.empty() : Optional.of(certificates.clone());
        }
    }
}
