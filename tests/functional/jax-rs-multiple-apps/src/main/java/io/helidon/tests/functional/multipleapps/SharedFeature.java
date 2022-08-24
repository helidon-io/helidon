/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.tests.functional.multipleapps;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.ConstrainedTo;
import jakarta.ws.rs.RuntimeType;
import jakarta.ws.rs.container.DynamicFeature;
import jakarta.ws.rs.container.ResourceInfo;
import jakarta.ws.rs.core.Application;
import jakarta.ws.rs.core.FeatureContext;
import java.util.HashSet;
import java.util.Set;

import io.helidon.common.context.Contexts;

@ConstrainedTo(RuntimeType.SERVER)
@ApplicationScoped
public class SharedFeature implements DynamicFeature {

    private static final Set<Class<? extends Application>> applications = new HashSet<>();

    static Set<Class<? extends Application>> applications() {
        return applications;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void configure(ResourceInfo resourceInfo, FeatureContext featureContext) {
        // Collect all application subclasses started
        Application app = Contexts.context().flatMap(c -> c.get(Application.class)).orElse(null);
        assert app != null;
        applications.add(!app.getClass().isSynthetic() ? app.getClass()
                : (Class<? extends Application>) app.getClass().getSuperclass());
    }
}