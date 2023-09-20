package io.helidon.webserver.observe.info;

import java.util.Map;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.webserver.observe.ObserverConfigBase;

/**
 * Info Observer configuration.
 */
@Prototype.Blueprint
@Prototype.Configured
interface InfoObserverConfigBlueprint extends ObserverConfigBase, Prototype.Factory<InfoObserver> {
    @Option.Configured
    @Option.Default("info")
    @Override
    String endpoint();

    @Override
    @Option.Default("info")
    String name();

    /**
     * Values to be exposed using this observability endpoint.
     *
     * @return value map
     */
    @Option.Configured
    @Option.Singular
    Map<String, Object> values();
}
