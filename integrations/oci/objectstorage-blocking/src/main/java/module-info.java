module io.helidon.integrations.oci.objectstorage.blocking {
    requires java.json;

    requires io.helidon.config;
    requires io.helidon.common.http;
    requires io.helidon.integrations.oci.connect;
    requires io.helidon.integrations.oci.objectstorage;
    requires io.helidon.integrations.common.rest;

    exports io.helidon.integrations.oci.objectstorage.blocking;

    provides io.helidon.integrations.oci.connect.spi.InjectionProvider
        with io.helidon.integrations.oci.objectstorage.blocking.OciBlockingObjectStorageInjectionProvider;
}