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
package io.helidon.lra;
import org.eclipse.microprofile.lra.annotation.ws.rs.LRA;

import javax.ws.rs.container.DynamicFeature;
import javax.ws.rs.container.ResourceInfo;
import javax.ws.rs.core.FeatureContext;
import javax.ws.rs.ext.Provider;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

@Provider
public class FilterRegistration implements DynamicFeature {
    private boolean isRegistered;

    @Override
    public void configure(ResourceInfo resourceInfo, FeatureContext ctx) {
        if (!isRegistered) {
            Method method = resourceInfo.getResourceMethod();
            Annotation transactional = method.getDeclaredAnnotation(LRA.class);

            if (transactional != null || method.getDeclaringClass().getDeclaredAnnotation(LRA.class) != null) {
//                ctx.register(ServerLRAFilter.class);
                isRegistered = true;
            }
        }
    }
}