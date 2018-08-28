/*
 * Copyright (c) 2017, 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.webserver.examples.translator.frontend;

import io.helidon.webserver.opentracing.OpentracingContainerFilter;

import org.glassfish.hk2.utilities.binding.AbstractBinder;
import org.glassfish.jersey.server.ResourceConfig;

/**
 * @author Pavel Bucek (pavel.bucek at oracle.com)
 */
public class Application extends ResourceConfig {


    /**
     * Translator Frontend application.
     *
     * @param backendHostname the translator backend hostname
     * @param backendPort the translator backend port
     */
    public Application(String backendHostname, int backendPort) {
        register(new AbstractBinder() {
            @Override
            protected void configure() {
                bind(backendPort).to(Integer.class).named(TranslatorResource.BACKEND_PORT);
                bind(backendHostname).to(String.class).named(TranslatorResource.BACKEND_HOSTNAME);

            }
        });
        register(OpentracingContainerFilter.class);
        packages(TranslatorResource.class.getPackage().getName());
    }
}
