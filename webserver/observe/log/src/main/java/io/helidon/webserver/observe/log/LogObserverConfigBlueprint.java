package io.helidon.webserver.observe.log;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.webserver.observe.ObserverConfigBase;

/**
 * Log Observer configuration.
 */
@Prototype.Blueprint
@Prototype.Configured
interface LogObserverConfigBlueprint extends ObserverConfigBase, Prototype.Factory<LogObserver> {
    @Option.Configured
    @Option.Default("log")
    @Override
    String endpoint();

    @Override
    @Option.Default("log")
    String name();

    /**
     * Permit all access, even when not authorized.
     *
     * @return whether to permit access for anybody
     */
    @Option.Configured
    boolean permitAll();

    /**
     * Configuration of log stream.
     *
     * @return log stream configuration
     */
    @Option.Configured
    @Option.DefaultCode("@io.helidon.webserver.observe.log.LogStreamConfig@.create()")
    LogStreamConfig stream();
}
