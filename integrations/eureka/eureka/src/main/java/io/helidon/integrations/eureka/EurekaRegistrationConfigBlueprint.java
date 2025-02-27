/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.helidon.integrations.eureka;

import java.util.function.Supplier;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.common.Weighted;
import io.helidon.webclient.http1.Http1ClientConfig;
import io.helidon.webserver.spi.ServerFeatureProvider;

import jakarta.json.JsonBuilderFactory;

import static io.helidon.integrations.eureka.EurekaRegistrationServerFeature.EUREKA_ID;

/*
 * Note that Javadoc for this interface is actually Javadoc for the prototype interface that is generated from it.
 *
 * Note as well that @see references to the prototype's methods cannot be used because they will reference the (publicly
 * inaccessible) blueprint interface.
 */

/**
 * A {@linkplain Prototype.Api prototype} for {@link EurekaRegistrationServerFeature} {@linkplain
 * io.helidon.builder.api.RuntimeType.Api runtime type} instances.
 *
 * <p>Most users will never need to programmatically interact with any of the classes in this package.</p>
 *
 * @see EurekaRegistrationServerFeature
 */
@Prototype.Blueprint(decorator = EurekaRegistrationConfigSupport.BuilderDecorator.class)
@Prototype.Configured(value = EUREKA_ID, root = false)
@Prototype.Provides(ServerFeatureProvider.class)
interface EurekaRegistrationConfigBlueprint extends Prototype.Factory<EurekaRegistrationServerFeature> {

    /*
     * Javadoc present here is used both on generated prototype methods (some of which may return an Optional) and on
     * the builder's mutator methods (none of which will accept an Optional and may have a different return type
     * (e.g. the builder type itself)). Return type documentation is also used verbatim as parameter documentation for
     * the associated builder method parameter. Wording throughout must therefore be terse and careful in order to apply
     * in all possible use sites to which it is copied.
     */

    /**
     * An {@link Http1ClientConfig.Builder} used to build an internal client for communicating with a Eureka server.
     *
     * <p>An {@link Http1ClientConfig} built from the supplied builder must have its {@link Http1ClientConfig#baseUri()}
     * property set to address a Eureka Server instance.</p>
     *
     * @return an {@link Http1ClientConfig.Builder}
     */
    // Ideally the signature would be Supplier<? extends Http1ClientConfig.Builder>, but generated code in that case
    // does not compile.
    Supplier<Http1ClientConfig.Builder> clientBuilderSupplier();

    /**
     * An {@link InstanceInfoConfig} describing the service instance to be registered.
     *
     * @return the {@link InstanceInfoConfig} describing the service instance to be registered
     *
     * @see InstanceInfoConfig
     */
    @Option.Configured("instance")
    @Option.DefaultMethod(value = "create")
    InstanceInfoConfig instanceInfo();

    /**
     * A {@link JsonBuilderFactory} used for working with JSON internally; the default value is normally entirely
     * suitable.
     *
     * @return a {@link JsonBuilderFactory}
     */
    @Option.DefaultCode("@jakarta.json.Json@.createBuilderFactory(@java.util.Map@.of())")
    JsonBuilderFactory jsonBuilderFactory();

    /**
     * Whether this feature will be enabled.
     *
     * @return whether this feature will be enabled
     */
    @Option.Configured
    @Option.DefaultBoolean(true)
    boolean enabled();

    /**
     * The non-{@code null} name of this instance; {@value EurekaRegistrationServerFeature#EUREKA_ID} is a default
     * value.
     *
     * @return the non-{@code null} name of this instance; {@value EurekaRegistrationServerFeature#EUREKA_ID} is a
     * default value
     */
    @Option.Default(EUREKA_ID)
    String name();

    /**
     * The (zero or positive) {@linkplain Weighted weight} of this instance.
     *
     * @return the (zero or positive) weight of this instance
     */
    @Option.Configured
    @Option.DefaultDouble(Weighted.DEFAULT_WEIGHT)
    double weight();

}
