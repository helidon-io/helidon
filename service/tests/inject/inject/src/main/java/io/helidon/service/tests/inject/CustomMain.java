/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

package io.helidon.service.tests.inject;

import io.helidon.service.inject.InjectConfig;
import io.helidon.service.inject.api.InjectRegistry;
import io.helidon.service.inject.api.Injection;

/**
 * Example of a custom main class.
 * This class is here to make sure this does not get broken.
 */
@Injection.Main
public class CustomMain extends ApplicationMain {
    /*
    Important note:
    DO NOT change the signature of methods in this class, as that would cause a backward incompatible change
    for our users.
    The super type is code generated, and its super type is part of Helidon APIs. Any changes in the
    Helidon APIs would cause older generated code to stop working.
    The only exception is major version updates, but it would still be better if this stays compatible.
     */

    /**
     * Start the application.
     *
     * @param args command line arguments
     */
    public static void main(String[] args) {
        new CustomMain().start(args);
    }

    @Override
    protected void beforeServiceDescriptors(InjectConfig.Builder configBuilder) {
        System.out.println("Before service descriptors");
    }

    @Override
    protected void serviceDescriptors(InjectConfig.Builder config) {
        System.out.println("Service descriptors with config (before): " + config.discoverServices());
        super.serviceDescriptors(config);
        System.out.println("Service descriptors with config (after): " + config.discoverServices());
    }

    @Override
    protected void afterServiceDescriptors(InjectConfig.Builder configBuilder) {
        System.out.println("After service descriptors");
    }

    @Override
    protected InjectRegistry init(InjectConfig config) {
        System.out.println("Before init method");
        try {
            return super.init(config);
        } finally {
            System.out.println("After init method");
        }
    }

    @Override
    protected boolean discoverServices() {
        return super.discoverServices();
    }

    @Override
    protected void start(String[] arguments) {
        super.start(arguments);
    }

    @Override
    protected InjectConfig.Builder configBuilder(String[] arguments) {
        return super.configBuilder(arguments);
    }
}
