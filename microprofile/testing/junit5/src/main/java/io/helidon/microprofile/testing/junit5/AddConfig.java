/*
 * Copyright (c) 2022, 2025 Oracle and/or its affiliates.
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
package io.helidon.microprofile.testing.junit5;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Add a configuration key/value pair to the {@link Configuration#useExisting() synthetic test configuration}.
 * <p>
 * This annotation can be repeated.
 * <p>
 * If used on a method, the container will be reset regardless of the test lifecycle.
 *
 * @see AddConfigs
 * @see AddConfigBlock
 * @see Configuration
 * @deprecated Use {@link io.helidon.microprofile.testing.AddConfig} instead
 */
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Repeatable(AddConfigs.class)
@Deprecated(since = "4.2.0")
public @interface AddConfig {
    /**
     * Configuration property key.
     *
     * @return key
     */
    String key();

    /**
     * Configuration property value.
     *
     * @return value
     */
    String value();
}
