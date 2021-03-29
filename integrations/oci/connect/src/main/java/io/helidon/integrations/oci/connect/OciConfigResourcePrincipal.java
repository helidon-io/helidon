package io.helidon.integrations.oci.connect;

import java.util.Map;

import io.helidon.common.reactive.Single;

public class OciConfigResourcePrincipal implements OciConfigProvider {
    private static final String OCI_RESOURCE_PRINCIPAL_VERSION = "OCI_RESOURCE_PRINCIPAL_VERSION";
    private static final String RP_VERSION_2_2 = "2.2";
    private static final String OCI_RESOURCE_PRINCIPAL_RPST = "OCI_RESOURCE_PRINCIPAL_RPST";
    private static final String OCI_RESOURCE_PRINCIPAL_PRIVATE_PEM = "OCI_RESOURCE_PRINCIPAL_PRIVATE_PEM";
    private static final String OCI_RESOURCE_PRINCIPAL_PRIVATE_PEM_PASSPHRASE =
            "OCI_RESOURCE_PRINCIPAL_PRIVATE_PEM_PASSPHRASE";
    private static final String OCI_RESOURCE_PRINCIPAL_REGION_ENV_VAR_NAME =
            "OCI_RESOURCE_PRINCIPAL_REGION";

    /**
     * Return true if the current environment seems to support resource principal.
     *
     * @return {@code true} if this environment can use resource principal
     */
    public static boolean isAvailable() {
        Map<String, String> env = System.getenv();

        // this is not complete, but it is a fair guess at the availability
        // the variables may be wrong - that will fail the process when we try to set everything up
        return env.containsKey(OCI_RESOURCE_PRINCIPAL_PRIVATE_PEM)
                && env.containsKey(OCI_RESOURCE_PRINCIPAL_RPST)
                && env.containsKey(OCI_RESOURCE_PRINCIPAL_REGION_ENV_VAR_NAME);
    }

    public static OciConfigResourcePrincipal create() {
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
    public String tenancyOcid() {
        return null;
    }

    @Override
    public Single<OciSignatureData> refresh() {
        return OciConfigProvider.super.refresh();
    }
}
