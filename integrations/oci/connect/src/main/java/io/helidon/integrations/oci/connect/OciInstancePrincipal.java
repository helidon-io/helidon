package io.helidon.integrations.oci.connect;

import java.util.concurrent.TimeUnit;

import io.helidon.common.http.Http;
import io.helidon.common.reactive.Single;
import io.helidon.webclient.WebClient;

public class OciInstancePrincipal implements OciConfigProvider {
    private static final String DEFAULT_METADATA_SERVICE_URL = "http://169.254.169.254/opc/v2/";

    // this method blocks when trying to connect to a remote IP address
    public static boolean isAvailable() {
        return WebClient.builder()
                .connectTimeout(1, TimeUnit.SECONDS)
                .readTimeout(1, TimeUnit.SECONDS)
                .baseUri(DEFAULT_METADATA_SERVICE_URL)
                .build()
                .get()
                .request()
                .map(response -> response.status() == Http.Status.FORBIDDEN_403)
                .onErrorResume(it -> false)
                .await();
    }
    public static OciInstancePrincipal create() {
        return null;
    }

    @Override
    public OciSignatureData signatureData() {
        return null;
    }

    @Override
    public String region() {
        return null;
    }

    @Override
    public Single<Boolean> refresh() {
        return OciConfigProvider.super.refresh();
    }
}
