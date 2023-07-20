package io.helidon.nima.websocket.client;

import io.helidon.builder.api.Prototype;
import io.helidon.config.metadata.Configured;
import io.helidon.config.metadata.ConfiguredOption;
import io.helidon.nima.webclient.api.HttpClientConfig;

/**
 * WebSocket full webclient configuration.
 * The client configuration also contains all necessary configuration for HTTP, as WebSocket upgrades from HTTP.
 *
 * @see io.helidon.nima.webclient.api.WebClient#client(io.helidon.nima.webclient.spi.Protocol,
 *         io.helidon.nima.webclient.spi.ProtocolConfig)
 */
@Prototype.Blueprint
@Configured
interface WsClientConfigBlueprint extends HttpClientConfig, Prototype.Factory<WsClient> {
    /**
     * WebSocket specific configuration.
     *
     * @return protocol specific configuration
     */
    @ConfiguredOption("create()")
    WsClientProtocolConfig protocolConfig();
}
