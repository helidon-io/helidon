/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.security.jersey;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.server.ClientBinding;

/**
 * Annotation to inject clients that have security feature configured.
 * Just send security context as request parameter using {@link ClientSecurityFeature#PROPERTY_CONTEXT} and
 * security will be handled for outgoing request(s) on this client.
 *
 * <p>
 * <pre>
 * &#064;SecureClient
 * &#064;Uri("http://service-name:8787/base_path")
 * private WebTarget target;
 *
 * &#064;GET
 * public Response getIt(@Context SecurityContext context) {
 *  return target.request()
 *      .property(SecureClient.PROPERTY_CONTEXT, context)
 *      .get();
 * }
 *
 *
 * </pre>
 */
@ClientBinding(configClass = SecureClient.SecureClientConfig.class, inheritServerProviders = false)
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.PARAMETER})
public @interface SecureClient {
    /**
     * Configuration class for client security.
     */
    class SecureClientConfig extends ClientConfig {
        @SuppressWarnings("checkstyle:RedundantModifier") // public modifier required by Jersey
        public SecureClientConfig() {
            this.register(new ClientSecurityFeature());
        }
    }
}
