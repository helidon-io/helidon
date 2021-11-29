/*
 * Copyright (c) 2020, 2021 Oracle and/or its affiliates.
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
package io.helidon.tests.functional.mp.syntheticapp;

import io.helidon.microprofile.cdi.Main;
import io.helidon.microprofile.server.ServerCdiExtension;

import jakarta.enterprise.inject.spi.CDI;

/**
 * Main class to start this test.
 */
public class MpSyntheticAppMain {
    public static void main(String[] args) {
        Main.main(args);
        System.out.println("Started on port: " + CDI.current().getBeanManager().getExtension(ServerCdiExtension.class)
                .port());
    }
}
