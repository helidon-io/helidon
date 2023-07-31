/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

package io.helidon.builder.test.testsubjects;

import io.helidon.builder.api.Prototype;
import io.helidon.config.metadata.ConfiguredOption;

/**
 * Demonstrates interception of builders.
 */
@Prototype.Blueprint(decorator = BeanBuilderInterceptor.class)
interface InterceptedBeanBlueprint {

    /**
     * The name to say hello to.
     *
     * @return the name
     */
    @ConfiguredOption(required = true)
    String name();

    /**
     * The hello message will be explicitly overridden to say "hello {@link #name()}".
     *
     * @return the hello message
     */
    String helloMessage();

}
