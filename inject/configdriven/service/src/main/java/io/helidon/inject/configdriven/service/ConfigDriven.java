package io.helidon.inject.configdriven.service;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import io.helidon.inject.service.Injection;

public final class ConfigDriven {
    private ConfigDriven() {
    }

    /**
     * This configured prototype should be acting as a config bean. This means that if appropriate configuration
     * exists (must be a root configured type with a defined prefix), an instance will be created from that configuration.
     * Additional setup is possible to ensure an instance even if not present in config, and to create default (unnamed) instance.
     * <p>
     * Placing this annotation on any type makes that type configurable from the root of configuration, even if the prototype
     * configuration says otherwise (there is no validation for this).
     */
    @Documented
    @Retention(RetentionPolicy.CLASS)
    @Target(java.lang.annotation.ElementType.TYPE)
    @Injection.Qualifier
    public @interface ConfigBean {
        /**
         * Configuration key to be used to discover location in config.
         * We expect this annotation to be placed on a Blueprint of a configured prototype (see Helidon Builder), in which
         * case we use the key defined on the configured annotation.
         * If a non-empty string value is defined on this annotation, it will ALWAYS override any other key configuration
         * <p>
         * If the annotated type is not a Blueprint, the key must be defined on this annotation.
         * In such a case the type must provide an accessible factory method (at least package local)
         * "{@code static MyConfigBean create(io.helidon.common.config.Config)}", if the type is also annotated
         * with either of {@link io.helidon.inject.configdriven.service.ConfigDriven.AtLeastOne} or
         * {@link io.helidon.inject.configdriven.service.ConfigDriven.WantDefault}, an additional factory method must exist:
         * "{@code static MyConfigBean create()}".
         * <p>
         * If places we look for yield an empty string key, we actually use the root configuration.
         *
         * @return configuration key to discover configuration of this bean
         */
        String value() default "";
    }

    /**
     * At least one instance is required.
     * If {@link io.helidon.inject.configdriven.service.ConfigDriven.WantDefault} is not configured,
     * the instance must be configured in config.
     */
    @Documented
    @Retention(RetentionPolicy.CLASS)
    @Target(java.lang.annotation.ElementType.TYPE)
    @Injection.Qualifier
    public @interface AtLeastOne {
    }

    /**
     * Determines whether there can be more than one bean instance of this type.
     * <p>
     * If not defined on a config bean, there can be only up to one instance.
     * If defined, one instance will be created for each list value, or child node of the config tree.
     * <p>
     * Note: this is dynamic in nature, and therefore cannot be validated at compile time. All violations found to this
     * policy will be observed during <i>Services</i> activation.
     */
    @Documented
    @Retention(RetentionPolicy.CLASS)
    @Target(java.lang.annotation.ElementType.TYPE)
    @Injection.Qualifier
    public @interface Repeatable {
    }

    /**
     * There will always be an instance created with defaults.
     * <p>
     * If combined with {@link io.helidon.inject.configdriven.service.ConfigDriven.AtLeastOne}:
     * <ul>
     *     <li>If there is no configuration in config, default instance is created</li>
     *     <li>If there is a configuration in config with {@code @default} name, that instance is created</li>
     *     <li>If there is one ore more configurations in config, all of those would be create, plus the default instance;
     *          in case this is not {@link io.helidon.inject.configdriven.service.ConfigDriven.Repeatable}, default instance
     *          WOULD NOT be created</li>
     * </ul>
     */
    @Documented
    @Retention(RetentionPolicy.CLASS)
    @Target(java.lang.annotation.ElementType.TYPE)
    @Injection.Qualifier
    public @interface WantDefault {
    }
}
