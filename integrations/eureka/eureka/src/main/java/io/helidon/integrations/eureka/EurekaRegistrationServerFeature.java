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

import java.lang.System.Logger;
import java.util.Objects;
import java.util.function.Consumer;

import io.helidon.builder.api.RuntimeType;
import io.helidon.common.Weighted;
import io.helidon.webserver.spi.ServerFeature;
import io.helidon.webserver.spi.ServerFeature.ServerFeatureContext;

import static io.helidon.webserver.WebServer.DEFAULT_SOCKET_NAME;
import static java.lang.System.Logger.Level.INFO;
import static java.lang.System.getLogger;

/**
 * A {@link ServerFeature} that automatically and unobtrusively attempts to register the currently running microservice
 * as a Eureka Server <dfn>service instance</dfn>.
 *
 * <p>Most users will never need to programmatically interact with any of the classes in this package.</p>
 *
 * @see EurekaRegistrationServerFeatureProvider#create(io.helidon.common.Config, String)
 */
@RuntimeType.PrototypedBy(EurekaRegistrationConfig.class)
public final class EurekaRegistrationServerFeature implements RuntimeType.Api<EurekaRegistrationConfig>, ServerFeature, Weighted {

    static final String EUREKA_ID = "eureka";

    private static final Logger LOGGER = getLogger(EurekaRegistrationServerFeature.class.getName());

    private final EurekaRegistrationConfig prototype;

    private EurekaRegistrationServerFeature(EurekaRegistrationConfig prototype) {
        super();
        this.prototype = Objects.requireNonNull(prototype, "prototype");
    }

    @Override // ServerFeature
    public String name() {
        return this.prototype.name();
    }

    /**
     * Returns the prototype.
     *
     * @return the prototype
     */
    @Override // RuntimeType.Api<EurekaRegistrationConfig>
    public EurekaRegistrationConfig prototype() {
        return this.prototype;
    }

    @Override // ServerFeature
    public void setup(ServerFeatureContext featureContext) {
        if (!this.prototype().enabled()) {
            if (LOGGER.isLoggable(INFO)) {
                LOGGER.log(INFO,
                           "The " + this.type() + " ServerFeature implementation is disabled");
            }
            return;
        }
        // Just the default?
        featureContext.socket(DEFAULT_SOCKET_NAME)
            .httpRouting() // a builder
            .addFeature(new EurekaRegistrationHttpFeature(this.prototype()));
    }

    @Override // ServerFeature
    public String type() {
        return EUREKA_ID;
    }

    @Override // Weighted
    public double weight() {
        return this.prototype.weight();
    }


    /*
     * Static methods.
     */


    // This method is required by the @RuntimeType.PrototypedBy contract, which governs this class. Without this method,
    // the following compilation error occurs (by design):
    //
    //     As io.helidon.integrations.eureka.EurekaRegistrationServerFeature is annotated
    //     with @RuntimeType.PrototypedBy(EurekaRegistrationConfig), the type must implement the following method:
    //     static EurekaRegistrationServerFeature create(EurekaRegistrationConfig);
    /**
     * Creates a {@link EurekaRegistrationServerFeature}.
     *
     * <p>Most users will never need to programmatically interact with any of the classes in this package.</p>
     *
     * @param prototype a prototype
     *
     * @return a {@link EurekaRegistrationServerFeature}
     */
    public static EurekaRegistrationServerFeature create(EurekaRegistrationConfig prototype) {
        return new EurekaRegistrationServerFeature(prototype);
    }

    // This method is required by the @Prototype.Factory contract, which this class does not implement but must abide
    // by. The generated interface, EurekaRegistrationConfig, does not call this method, nor does the generated
    // implementation class that implements EurekaRegistrationConfig,
    // EurekaRegistrationConfig.BuilderBase.EurekaRegistrationConfigImpl. Without this method, the following compilation
    // error occurs (by design):
    //
    //     As io.helidon.integrations.eureka.EurekaRegistrationConfig implements
    //     Prototype.Factory<io.helidon.integrations.eureka.EurekaRegistrationServerFeature>, the runtime type must
    //     implement the following method: static EurekaRegistrationConfig.Builder builder() { return
    //     EurekaRegistrationConfig.builder(); }
    //
    /**
     * Returns a builder of the prototype that also knows how to build instances of this runtime type.
     *
     * <p>Most users will never need to programmatically interact with any of the classes in this package.</p>
     *
     * @return a builder of the prototype that also knows how to build instances of this runtime type
     */
    public static EurekaRegistrationConfig.Builder builder() {
        return EurekaRegistrationConfig.builder();
    }

    // This method is required by the @Prototype.Factory contract, which this class does not implement but must abide
    // by. The generated interface, EurekaRegistrationConfig, extends from EurekaRegistrationConfigBlueprint, which does
    // implement it.
    //
    // Although this method must exist, it can be private, since nothing calls it. Without this method, the following
    // compilation error occurs (by design):
    //
    //     As io.helidon.integrations.eureka.EurekaRegistrationConfig implements
    //     Prototype.Factory<io.helidon.integrations.eureka.EurekaRegistrationServerFeature>, the type
    //     EurekaRegistrationServerFeature must implement the following method: static EurekaRegistrationServerFeature
    //     create(java.util.function.Consumer<io.helidon.integrations.eureka.EurekaRegistrationConfig.Builder> consumer)
    //     { return builder().update(consumer).build();}
    //
    /**
     * Creates a {@link EurekaRegistrationServerFeature}.
     *
     * <p>Most users will never need to programmatically interact with any of the classes in this package.</p>
     *
     * @param builderConsumer a {@link Consumer} that updates a supplied {@link EurekaRegistrationConfig.Builder}; must
     * not be {@code null}
     *
     * @return a non-{@code null} {@link EurekaRegistrationServerFeature}
     *
     * @exception NullPointerException if {@code builderConsumer} is {@code null}
     *
     * @see #builder()
     */
    public static EurekaRegistrationServerFeature create(Consumer<EurekaRegistrationConfig.Builder> builderConsumer) {
        return builder()
            .update(builderConsumer)
            .build();
    }

}
