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

package io.helidon.security.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import io.helidon.security.spi.AuthenticationProvider;

/**
 * An annotation used to specify server resources with enforced authentication and resources accessible without authentication.
 * <p>
 * The following sample shows examples of use of {@link Authenticated} annotation in a JAX-RS/Jersey application:
 *
 * <pre>
 *  &#64;Authenticated
 *  &#64;ApplicationPath("myApp")
 *  public class SecuredApplication extends javax.ws.rs.core.Application { ... }
 *
 *  &#64;Authenticated(false)
 *  &#64;Path("/")
 *  public class PublicResource {
 *    &#64;GET
 *    public String getResourceContent() { ... }
 *
 *    // Only authenticated users can update the content of the public resource
 *    &#64;Authenticated
 *    &#64;PUT
 *    public Response setNewResourceContent(String content) { ... }
 *  }
 * </pre>
 * <p>
 * Authenticated annotation is not cumulative - e.g. if you define this annotation on a resource method, it will take
 * ALL values from this instance of Authenticated (so if you want to use a custom authentication provider, you must define it
 * again in each Authenticated instance).
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE, ElementType.FIELD})
@Documented
@Inherited
public @interface Authenticated {
    /**
     * Determine whether authentication should be enabled. Defaults to {@code true}
     *
     * @return {@code true} if authentication should be enabled.
     */
    boolean value() default true;

    /**
     * If set to optional, authentication will be attempted, yet if it fails, we would still be called
     * without authenticated user/service.
     * For fine-grained control use configuration of provider flags (e.g. if a service is optional and user is mandatory)
     *
     * @return true if authentication should be optional
     */
    boolean optional() default false;

    /**
     * Explicit authentication provider to use when processing this Authorized.
     * Setting this value will ignore security provider configured globally.
     * Value is the name of a configured {@link AuthenticationProvider}.
     *
     * @return name of a configured provider
     */
    String provider() default "";
}
