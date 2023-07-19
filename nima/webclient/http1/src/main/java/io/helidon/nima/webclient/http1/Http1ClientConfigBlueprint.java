package io.helidon.nima.webclient.http1;

import io.helidon.builder.api.Prototype;
import io.helidon.nima.webclient.api.HttpClientConfig;

/**
 * HTTP/1.1. full webclient configuration.
 */
@Prototype.Blueprint(builderInterceptor = Http1ClientConfigSupport.Http1BuilderInterceptor.class)
interface Http1ClientConfigBlueprint extends HttpClientConfig, Prototype.Factory<Http1Client> {
    /**
     * HTTP/1.1 specific configuration.
     *
     * @return protocol specific configuration
     */
    Http1ClientProtocolConfig protocolConfig();
}
