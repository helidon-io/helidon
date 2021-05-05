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
 * An annotation used to specify server resources with enforced authorization.
 * <p>
 * The following sample shows examples of use of &#64;Authorized annotation in a JAX-RS/Jersey application:
 *
 * <pre>
 *  &#64;Authorized
 *  &#64;Authenticated
 *  &#64;ApplicationPath("myApp")
 *  public class SecuredApplication extends javax.ws.rs.core.Application { ... }
 *
 *  &#64;Authorized(false)
 *  &#64;Path("/")
 *  public class PublicResource {
 *    &#64;GET
 *    public String getResourceContent() { ... }
 *
 *    // Only authenticated users can update the content of the public resource
 *    &#64;Authorized
 *    &#64;PUT
 *    public Response setNewResourceContent(String content) { ... }
 *  }
 * </pre>
 * <p>
 * Authorized annotation is not cumulative - e.g. if you define this annotation on a resource method, it will take
 * ALL values from this instance of Authorized (so if you want to use a custom authorization provider, you must define it
 * again in each Authorized instance).
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE, ElementType.FIELD})
@Documented
@Inherited
public @interface Authorized {
    /**
     * Determine whether authorization should be enabled. Defaults to {@code true}
     *
     * @return {@code true} if authorization should be enabled.
     */
    boolean value() default true;

    /**
     * Explicit authorization provider to use when processing this Authorized.
     * Setting this value will ignore security provider configured globally.
     * Value is the name of a configured {@link AuthenticationProvider}.
     *
     * @return name of a configured provider
     */
    String provider() default "";

    /**
     * By default, authorization is implicit and all annotations are processed before method invocation to authorize access.
     * In case this is set to true, authorization MUST be invoked manually, calling {@link
     * io.helidon.security.SecurityContext#authorize(Object...)}.
     * If set to true the security module will not check authorization; security module still
     * checks that authorization was called. If not, an exception is generated post-processing.
     * For example the Jersey integration will return an internal server error in such a case.
     *
     * @return true if explicit authorization will be invoked in the code, false for implicit (handled by security module)
     */
    boolean explicit() default false;
}
