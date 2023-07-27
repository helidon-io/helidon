package io.helidon.nima.http2.webclient;

import io.helidon.builder.api.Prototype;
import io.helidon.config.metadata.ConfiguredOption;
import io.helidon.nima.webclient.api.HttpClientConfig;

/**
 * HTTP/2 full webclient configuration.
 */
@Prototype.Blueprint
interface Http2ClientConfigBlueprint extends HttpClientConfig, Prototype.Factory<Http2Client> {
    /**
     * HTTP/2 specific configuration.
     *
     * @return protocol specific configuration
     */
    @ConfiguredOption("create()")
    Http2ClientProtocolConfig protocolConfig();
}
