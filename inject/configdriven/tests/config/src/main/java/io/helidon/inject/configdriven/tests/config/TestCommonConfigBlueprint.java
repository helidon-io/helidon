/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.inject.configdriven.tests.config;

import java.util.List;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;

/**
 * For testing purpose.
 */
@Prototype.Configured
@Prototype.Blueprint
interface TestCommonConfigBlueprint {

    /**
     * For testing purpose.
     *
     * @return for testing purposes
     */
    @Option.Configured
    String name();

    /**
     * For testing purpose.
     *
     * @return for testing purposes
     */
    @Option.Configured
    @Option.Required
    int port();

    /**
     * For testing purpose.
     *
     * @return for testing purposes
     */
    @Option.Configured
    List<String> cipherSuites();

    /**
     * For testing purpose.
     *
     * @return for testing purposes
     */
    @Option.Configured
    @Option.Default("")
    char[] pswd();

}
