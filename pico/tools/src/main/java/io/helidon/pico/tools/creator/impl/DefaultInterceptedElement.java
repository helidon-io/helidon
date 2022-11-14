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
import java.util.Objects;
import java.util.Set;

import io.helidon.pico.tools.creator.InterceptedElement;
import io.helidon.pico.tools.creator.MethodInfo;

import lombok.EqualsAndHashCode;
import lombok.Getter;

/**
 * The default implementation for {@link io.helidon.pico.tools.creator.InterceptedElement}.
 */
//@SuperBuilder
@Getter
@EqualsAndHashCode
@SuppressWarnings("unchecked")
public class DefaultInterceptedElement implements InterceptedElement {
    /*@Singular("interceptedTriggerTypeName")*/ private final Set<String> interceptedTriggerTypeNames;
    private final MethodInfo elementInfo;

    @Override
    public String toString() {
        return String.valueOf(elementInfo);
    }

    protected DefaultInterceptedElement(DefaultInterceptedElementBuilder builder) {
        this.interceptedTriggerTypeNames = Objects.isNull(builder)
                ? Collections.emptySet() : Collections.unmodifiableSet(builder.interceptedTriggerTypeNames);
        this.elementInfo = builder.elementInfo;
    }

    public static DefaultInterceptedElementBuilder builder() {
        return new DefaultInterceptedElementBuilder() {};
    }

    public abstract static class DefaultInterceptedElementBuilder {
        private Set<String> interceptedTriggerTypeNames;
        private MethodInfo elementInfo;

        public DefaultInterceptedElement build() {
            return new DefaultInterceptedElement(this);
        }

        public DefaultInterceptedElementBuilder interceptedTriggerTypeNames(Collection<String> val) {
            this.interceptedTriggerTypeNames = Objects.isNull(val) ? null : new LinkedHashSet<>(val);
            return this;
        }

        public DefaultInterceptedElementBuilder elementInfo(MethodInfo val) {
            this.elementInfo = val;
            return this;
        }
    }
}
