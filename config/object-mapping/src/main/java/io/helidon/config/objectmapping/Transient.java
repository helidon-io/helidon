/*
 * Copyright (c) 2018, 2021 Oracle and/or its affiliates.
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
package io.helidon.config.objectmapping;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.CONSTRUCTOR;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Annotation used to exclude JavaBean property, method or constructor from JavaBean deserialization support.
 * The annotation can be used on JavaBean property public setter, on public property field,
 * on public constructor or on public {@code builder} and {@code build} method.
 * <p>
 * The annotation cannot be applied on same JavaBean property together with {@link Value}.
 * <p>
 * In following example, property {@code timestamp} is not set even {@code timestamp} config value is available.
 * Property {@code timestamp} is completely ignored by deserialization process.
 * <pre><code>
 * public class AppConfig {
 *     private Instant timestamp;
 *     private String greeting;
 *
 *     {@literal @}Transient
 *     public void setTimestamp(Instant timestamp) { // {@literal <1>}
 *         this.timestamp = timestamp;
 *     }
 *
 *     public void setGreeting(String greeting) {    // {@literal <2>}
 *         this.greeting = greeting;
 *     }
 *
 *     //...
 * }
 * </code></pre>
 * <ol>
 * <li>The {@code setTimestamp(Instant)} method is never called during deserialization.</li>
 * <li>While {@code setGreeting(String)} can be called if {@code greeting} config value is available.</li>
 * </ol>
 * Configuration example:
 * <pre>{@code
 * {
 *     "app" : {
 *         "greeting" : "Hello",
 *         "timestamp" : "2007-12-03T10:15:30.00Z"
 *     }
 * }
 * }</pre>
 * Getting {@code app} config node as {@code AppConfig} instance:
 * <pre>{@code
 * AppConfig appConfig = config.get("app").as(AppConfig.class);
 * assert appConfig.getTimestamp() == null;
 * }</pre>
 *
 * @see Value
 */
@Retention(RUNTIME)
@Target({METHOD, FIELD, CONSTRUCTOR})
public @interface Transient {
}
