package io.helidon.webserver.observe.config;

import java.util.Set;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.webserver.observe.ObserverConfigBase;

@Prototype.Blueprint
@Prototype.Configured
interface ConfigObserverConfigBlueprint extends ObserverConfigBase, Prototype.Factory<ConfigObserver> {
    @Option.Configured
    @Option.Default("config")
    @Override
    String endpoint();

    @Override
    @Option.Default("config")
    String name();

    /**
     * Permit all access, even when not authorized.
     *
     * @return whether to permit access for anybody
     */
    @Option.Configured
    boolean permitAll();

    /**
     * Secret patterns (regular expressions) to exclude from output.
     * Any pattern that matches a key will cause the output to be obfuscated and not contain the value.
     * <p>
     * Patterns always added:
     * <ul>
     *     <li>{@code .*password}</li>
     *     <li>{@code .*passphrase}</li>
     *     <li>{@code .*secret}</li>
     * </ul>
     *
     * @return set of regular expression patterns for keys, where values should be excluded from output
     */
    @Option.Configured
    @Option.Singular
    @Option.Default({".*password", ".*passphrase", ".*secret"})
    Set<String> secrets();
}
