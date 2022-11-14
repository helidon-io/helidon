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

package io.helidon.pico.tools.creator.impl;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import io.helidon.pico.types.AnnotationAndValue;
import io.helidon.pico.ServiceInfoBasics;
import io.helidon.pico.tools.creator.InterceptedElement;
import io.helidon.pico.tools.creator.InterceptionPlan;

import lombok.Getter;

/**
 * The default implementation for {@link io.helidon.pico.tools.creator.InterceptionPlan}.
 */
//@SuperBuilder
@Getter
@SuppressWarnings("unchecked")
public class DefaultInterceptionPlan implements InterceptionPlan {
    private final ServiceInfoBasics interceptedService;
    /*@Singular("serviceLevelAnnotation")*/ private final Set<AnnotationAndValue> serviceLevelAnnotations;
    /*@Singular("annotationTriggerTypeName")*/ private final Set<String> annotationTriggerTypeNames;
    /*@Singular("interceptedElement")*/ private final List<InterceptedElement> interceptedElements;

    protected DefaultInterceptionPlan(DefaultInterceptionPlanBuilder builder) {
        this.interceptedService = builder.interceptedService;
        this.serviceLevelAnnotations = Objects.isNull(builder.serviceLevelAnnotations)
                ? Collections.emptySet() : Collections.unmodifiableSet(builder.serviceLevelAnnotations);
        this.annotationTriggerTypeNames = Objects.isNull(builder.annotationTriggerTypeNames)
                ? Collections.emptySet() : Collections.unmodifiableSet(builder.annotationTriggerTypeNames);
        this.interceptedElements = Objects.isNull(builder.interceptedElements)
                ? Collections.emptyList() : Collections.unmodifiableList(builder.interceptedElements);
    }

    public static DefaultInterceptionPlanBuilder builder() {
        return new DefaultInterceptionPlanBuilder() {};
    }


    public abstract static class DefaultInterceptionPlanBuilder {
        private ServiceInfoBasics interceptedService;
        private Set<AnnotationAndValue> serviceLevelAnnotations;
        private Set<String> annotationTriggerTypeNames;
        private List<InterceptedElement> interceptedElements;

        public DefaultInterceptionPlan build() {
            return new DefaultInterceptionPlan(this);
        }

        public DefaultInterceptionPlanBuilder interceptedService(ServiceInfoBasics val) {
            this.interceptedService = val;
            return this;
        }

        public DefaultInterceptionPlanBuilder serviceLevelAnnotations(Collection<AnnotationAndValue> val) {
            this.serviceLevelAnnotations = Objects.isNull(val) ? null : new LinkedHashSet<>(val);
            return this;
        }

        public DefaultInterceptionPlanBuilder annotationTriggerTypeNames(Collection<String> val) {
            this.annotationTriggerTypeNames = Objects.isNull(val) ? null : new LinkedHashSet<>(val);
            return this;
        }

        public DefaultInterceptionPlanBuilder interceptedElements(Collection<InterceptedElement> val) {
            this.interceptedElements = Objects.isNull(val) ? null : new LinkedList<>(val);
            return this;
        }
    }

}
