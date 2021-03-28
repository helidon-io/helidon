package io.helidon.integrations.oci.connect;

import java.util.Optional;

import io.helidon.common.reactive.Single;

public interface OciConfigProvider {
    OciSignatureData signatureData();
    String region();

    default Optional<String> domain() {
        return Optional.empty();
    }
    /**
     * Refresh may be used for providers that can be reloaded.
     * The method is called when an invocation fails (for the first time) with a 401 exception.
     *
     * @return future with true if the refresh was successful (e.g. data changed)
     */
    default Single<Boolean> refresh() {
        return Single.just(false);
    }
}
