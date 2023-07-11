package io.helidon.nima.webclient.spi;

import java.util.Optional;

import io.helidon.common.http.ClientRequestHeaders;
import io.helidon.common.uri.UriFragment;
import io.helidon.common.uri.UriQueryWriteable;
import io.helidon.nima.webclient.api.ClientRequest;
import io.helidon.nima.webclient.api.ClientRequestConfig;
import io.helidon.nima.webclient.api.ClientUri;

public interface HttpClientSpi {
    Optional<ClientRequest<?>> clientRequest(ClientRequestConfig clientRequestConfig,
                                             ClientUri clientUri,
                                             ClientRequestHeaders headers,
                                             UriQueryWriteable query,
                                             UriFragment fragment);
}
