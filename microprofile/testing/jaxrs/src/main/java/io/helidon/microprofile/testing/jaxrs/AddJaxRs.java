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

package io.helidon.microprofile.testing.jaxrs;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import io.helidon.microprofile.server.JaxRsCdiExtension;
import io.helidon.microprofile.server.ServerCdiExtension;
import io.helidon.microprofile.testing.common.CommonAddBean;
import io.helidon.microprofile.testing.common.CommonCdiExtension;

import org.glassfish.jersey.ext.cdi1x.internal.CdiComponentProvider;

/**
 * JAX_RS Testing annotation.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
@Inherited
@CommonCdiExtension(ServerCdiExtension.class)
@CommonCdiExtension(JaxRsCdiExtension.class)
@CommonCdiExtension(CdiComponentProvider.class)
@CommonCdiExtension(org.glassfish.jersey.ext.cdi1x.internal.ProcessAllAnnotatedTypes.class)
@CommonAddBean(org.glassfish.jersey.weld.se.WeldRequestScope.class)
public @interface AddJaxRs {
}
