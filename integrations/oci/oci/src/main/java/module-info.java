module io.helidon.integrations.oci {
    requires io.helidon.common.configurable;
    requires io.helidon.service.registry;
    requires oci.java.sdk.common;
    requires io.helidon.common.config;
    requires org.bouncycastle.util;

    exports io.helidon.integrations.oci;
    exports io.helidon.integrations.oci.spi;
}
