package io.helidon.nima.http2.webclient;

import java.time.Duration;

import io.helidon.builder.api.Prototype;
import io.helidon.config.metadata.Configured;
import io.helidon.config.metadata.ConfiguredOption;
import io.helidon.nima.webclient.spi.ProtocolConfig;

@Prototype.Blueprint(builderInterceptor = Http2ClientConfigSupport.ProtocolConfigInterceptor.class)
@Configured
interface Http2ClientProtocolConfigBlueprint extends ProtocolConfig {
    @Override
    default String type() {
        return Http2ProtocolProvider.CONFIG_KEY;
    }

    @ConfiguredOption(Http2ProtocolProvider.CONFIG_KEY)
    @Override
    String name();

    /**
     * Prior knowledge of HTTP/2 capabilities of the server. If server we are connecting to does not
     * support HTTP/2 and prior knowledge is set to {@code false}, only features supported by HTTP/1 will be available
     * and attempts to use HTTP/2 specific will throw an {@link UnsupportedOperationException}.
     * <h4>Plain text connection</h4>
     * If prior knowledge is set to {@code true}, we will not attempt an upgrade of connection and use prior knowledge.
     * If prior knowledge is set to {@code false}, we will initiate an HTTP/1 connection and upgrade it to HTTP/2,
     * if supported by the server.
     * plaintext connection ({@code h2c}).
     * <h4>TLS protected connection</h4>
     * If prior knowledge is set to {@code true}, we will negotiate protocol using HTTP/2 only, failing if not supported.
     * if prior knowledge is set to {@code false}, we will negotiate protocol using both HTTP/2 and HTTP/1, using the protocol
     * supported by server.
     *
     * @return whether to use prior knowledge of HTTP/2
     */
    @ConfiguredOption("false")
    boolean priorKnowledge();

    /**
     * Configure initial MAX_FRAME_SIZE setting for new HTTP/2 connections.
     * Maximum size of data frames in bytes the client is prepared to accept from the server.
     * Default value is 2^14(16_384).
     *
     * @return data frame size in bytes between 2^14(16_384) and 2^24-1(16_777_215)
     */
    @ConfiguredOption("16384")
    int maxFrameSize();

    /**
     * Configure initial MAX_HEADER_LIST_SIZE setting for new HTTP/2 connections.
     * Sends to the server the maximum header field section size client is prepared to accept.
     * Defaults to {@code -1}, which means "unconfigured".
     *
     * @return units of octets
     */
    @ConfiguredOption("-1")
    long maxHeaderListSize();

    /**
     * Configure INITIAL_WINDOW_SIZE setting for new HTTP/2 connections.
     * Sends to the server the size of the largest frame payload client is willing to receive.
     * Defaults to {@value io.helidon.nima.http2.WindowSize#DEFAULT_WIN_SIZE}.
     *
     * @return units of octets
     */
    @ConfiguredOption("65535")
    int initialWindowSize();

    /**
     * First connection window update increment sent right after the connection is established.
     * Defaults to {@code 33_554_432}.
     *
     * @return number of bytes the client is prepared to receive as data from all the streams combined
     */
    @ConfiguredOption("33554432")
    int prefetch();

    /**
     * Timeout for blocking between windows size check iterations.
     *
     * @return timeout
     */
    @ConfiguredOption("PT0.1S")
    Duration flowControlBlockTimeout();
}
