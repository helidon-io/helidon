package io.helidon.service.inject;

import java.util.List;
import java.util.Optional;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.common.configurable.LruCache;
import io.helidon.service.inject.api.Activator;
import io.helidon.service.inject.api.Lookup;
import io.helidon.service.inject.api.InjectServiceInfo;
import io.helidon.service.registry.ServiceRegistryConfig;

@Prototype.Blueprint
@Prototype.Configured
interface InjectConfigBlueprint extends ServiceRegistryConfig {
    @Option.Configured
    Optional<LruCache<Lookup, List<InjectServiceInfo>>> lookupCache();

    @Option.Configured
    boolean lookupCacheEnabled();

    @Option.Configured
    @Option.DefaultBoolean(true)
    boolean interceptionEnabled();

    @Option.Configured
    @Option.Default("ACTIVE")
    Activator.Phase limitRuntimePhase();

    @Option.Configured
    @Option.DefaultBoolean(true)
    boolean useApplication();
}
