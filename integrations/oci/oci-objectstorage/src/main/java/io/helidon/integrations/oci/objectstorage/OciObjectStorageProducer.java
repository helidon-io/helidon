package io.helidon.integrations.oci.objectstorage;

import com.oracle.bmc.auth.AuthenticationDetailsProvider;
import com.oracle.bmc.objectstorage.ObjectStorage;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;
import javax.inject.Inject;

/**
 * Class OciObjectStorageProduced.
 */
@ApplicationScoped
public class OciObjectStorageProducer
{

    private ObjectStorage objectStorage;

    /**
     * Constructor for Object Storage.
     *
     * @param authenticationDetailsProvider from CDI environment
     */
    @Inject
    public OciObjectStorageProducer(AuthenticationDetailsProvider authenticationDetailsProvider) {
        this.objectStorage = OciObjectStorage.builder()
                .authenticationDetailsProvider(authenticationDetailsProvider)
                .build()
                .getObjectStorage();
    }

    /**
     * Produces Object storage in CDI environment.
     *
     * @return the ObjectStorage.
     */
    @Produces
    public ObjectStorage getObjectStorage() {
        return objectStorage;
    }
}
