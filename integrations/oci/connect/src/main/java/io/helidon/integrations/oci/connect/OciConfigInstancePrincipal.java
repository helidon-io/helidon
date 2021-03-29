package io.helidon.integrations.oci.connect;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.common.http.Http;
import io.helidon.common.reactive.Single;
import io.helidon.webclient.WebClient;

public class OciConfigInstancePrincipal extends OciConfigPrincipalBase implements OciConfigProvider {
    private static final String DEFAULT_METADATA_SERVICE_URL = "http://169.254.169.254/opc/v2/";

    private final AtomicReference<OciSignatureData> currentSignatureData = new AtomicReference<>();
    private final String region;
    private final String tenancyId;

    private OciConfigInstancePrincipal(Builder builder) {
        super(builder);

        this.region = null;
        this.tenancyId = null;
    }

    // this method blocks when trying to connect to a remote IP address
    public static boolean isAvailable() {
        return WebClient.builder()
                .connectTimeout(1, TimeUnit.SECONDS)
                .readTimeout(1, TimeUnit.SECONDS)
                .baseUri(DEFAULT_METADATA_SERVICE_URL)
                .followRedirects(true)
                .keepAlive(false)
                .build()
                .get()
                .request()
                .map(response -> response.status() == Http.Status.FORBIDDEN_403)
                .onErrorResume(it -> false)
                .await();
    }

    public static OciConfigInstancePrincipal create() {
        return null;
    }

    @Override
    public OciSignatureData signatureData() {
        return currentSignatureData.get();
    }

    @Override
    public String region() {
        return region;
    }

    @Override
    public String tenancyOcid() {
        return tenancyId;
    }

    @Override
    public Single<OciSignatureData> refresh() {
        return OciConfigProvider.super.refresh();
    }

    public static class Builder extends OciConfigPrincipalBase.Builder<Builder>
            implements io.helidon.common.Builder<OciConfigInstancePrincipal> {
        @Override
        public OciConfigInstancePrincipal build() {
            return new OciConfigInstancePrincipal(this);
        }
    }
}
