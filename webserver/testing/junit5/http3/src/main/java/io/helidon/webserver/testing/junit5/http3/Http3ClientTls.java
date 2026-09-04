/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.webserver.testing.junit5.http3;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import io.helidon.common.Api;

/**
 * Configures TLS trust material for injected {@link io.helidon.webclient.http3.Http3Client}
 * and {@link io.helidon.webserver.testing.junit5.http3.Http3LowLevelClient} test clients.
 */
@Api.Incubating
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.PARAMETER})
public @interface Http3ClientTls {
    /**
     * Classpath resource of the keystore or truststore to use.
     *
     * @return resource path
     */
    String resource();

    /**
     * Store passphrase.
     *
     * @return passphrase
     */
    String passphrase() default "changeit";

    /**
     * Store type.
     *
     * @return keystore type
     */
    String type() default "PKCS12";
}
