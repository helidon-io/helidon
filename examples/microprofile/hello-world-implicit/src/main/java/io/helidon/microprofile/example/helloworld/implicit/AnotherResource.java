/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.microprofile.example.helloworld.implicit;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.ws.rs.GET;
import javax.ws.rs.Path;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Resource showing all possible configuration injections.
 */
@Path("another")
@RequestScoped
public class AnotherResource {
    @Inject
    @ConfigProperty(name = "app.nonExistent", defaultValue = "145")
    private int defaultValue;

    @Inject
    @ConfigProperty(name = "app.nonExistent")
    private Optional<String> empty;

    @Inject
    @ConfigProperty(name = "app.uri")
    private Optional<URI> full;

    @Inject
    @ConfigProperty(name = "app.someInt")
    private Provider<Integer> provider;

    @Inject
    @ConfigProperty(name = "app")
    private Map<String, String> detached;

    @Inject
    private Config mpConfig;

    @Inject
    private io.helidon.config.Config helidonConfig;

    /**
     * Get method to validate that all injections worked.
     *
     * @return data from all fields of this class
     */
    @GET
    public String get() {
        return toString();
    }

    @Override
    public String toString() {
        return "AnotherResource{"
                + "defaultValue=" + defaultValue
                + ", empty=" + empty
                + ", full=" + full
                + ", provider=" + provider + "(" + provider.get() + ")"
                + ", detached=" + detached
                + ", microprofileConfig=" + mpConfig
                + ", helidonConfig=" + helidonConfig
                + '}';
    }
}
