/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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
 *
 */
package io.helidon.microprofile.metrics;

import javax.enterprise.event.Observes;
import javax.enterprise.inject.spi.Extension;
import javax.enterprise.inject.spi.ProcessAnnotatedType;
import javax.enterprise.inject.spi.WithAnnotations;
import javax.ws.rs.Path;

/**
 * Vetoes a single resource which should suppress the registration of its annotation-defined metrics.
 */
public class VetoCdiExtension implements Extension {

    private void vetoResourceClass(@Observes @WithAnnotations(Path.class) ProcessAnnotatedType<?> resourceType) {
        Class<?> resourceClass = resourceType.getAnnotatedType().getJavaClass();
        if (resourceClass == VetoedResource.class) {
            resourceType.veto();
        }
    }
}
