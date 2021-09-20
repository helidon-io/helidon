/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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

package io.helidon.integrations.microstream.cdi;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import javax.inject.Qualifier;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Creates a cache based upon the Microstream JCache implementation.
 * <br>See <a href="https://manual.docs.microstream.one/cache/overview">Microstream JCache</a>
 * <p>
 * Specify the cache name by the name property.
 * <p>
 * The configNode property expects an existing Helidon config node providing configuration properties for the cache.
 * <br>See <a href="https://manual.docs.microstream.one/cache/configuration">Microstream JCache configuration</a>
 * <br>If not provided the properties below "one.microstream.cache.default" will be used if existing.
 * Otherwise the build in defaults are applied.
 *
 */
@Qualifier
@Retention(RUNTIME)
@Target({PARAMETER, FIELD})
public @interface MicrostreamCache {
    /**
     * Specifies the configuration node used to configure the EmbeddedStorageManager instance to be created.
     *
     * @return the configuration node
     */
    String configNode() default "one.microstream.cache.default";

    /**
     * Specifies the cache name.
     *
     * @return the cache name
     */
    String name();
}
