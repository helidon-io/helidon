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

package io.helidon.microprofile.testing;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Mark a static method that provides a {@link org.eclipse.microprofile.config.spi.ConfigSource ConfigSource}
 * to add to the {@link Configuration#useExisting() synthetic test configuration}.
 * <p>
 * E.g.
 * <pre>
 * &#064;AddConfigSource
 * static ConfigSource config() {
 *     return MpConfigSources.create(Map.of("foo", "bar"));
 * }</pre>
 *
 * @see io.helidon.config.mp.MpConfigSources
 * @see AddConfig
 * @see AddConfigs
 * @see AddConfigBlock
 * @see Configuration
 */
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
public @interface AddConfigSource {
}
