package io.helidon.nima.webclient.api;

/**
 * A resource that can be released or closed.
 * This is used to handle HTTP/1.1 connection, for example. In other cases (such as HTTP/2 streams),
 * the release call also closes the resource.
 */
public interface ReleasableResource {
    /**
     * Releases the resource, and if this resource is re-usable, enabled reuse.
     */
    default void releaseResource() {
        closeResource();
    }

    /**
     * Closes the resource, we cannot use name {@code close}, as that would conflict with {@link java.lang.AutoCloseable},
     * as we do not want to have a checked exception thrown.
     */
    void closeResource();
}
