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

import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

import io.helidon.pico.ElementInfo;
import io.helidon.pico.spi.impl.DefaultElementInfo;
import io.helidon.pico.tools.creator.MethodInfo;

import lombok.EqualsAndHashCode;
import lombok.Getter;

/**
 * Default implementation for {@link io.helidon.pico.tools.creator.MethodInfo}.
 */
//@SuperBuilder(toBuilder = true)
@Getter
@EqualsAndHashCode(callSuper = true)
@SuppressWarnings("unchecked")
public class DefaultMethodInfo extends DefaultElementInfo implements MethodInfo {
    /*@Singular("throwableTypeName")*/ private final List<String> throwableTypeNames;
    /*@Singular("parameter")*/ private final List<ElementInfo> parameterInfo;

    protected DefaultMethodInfo(DefaultMethodInfoBuilder builder) {
        super(builder);
        this.throwableTypeNames = Objects.isNull(builder.throwableTypeNames)
                ? Collections.emptyList() : Collections.unmodifiableList(builder.throwableTypeNames);
        this.parameterInfo = Objects.isNull(builder.parameterInfo)
                ? Collections.emptyList() : Collections.unmodifiableList(builder.parameterInfo);
    }

    /**
     * @return A builder for {@link io.helidon.pico.spi.impl.DefaultElementInfo}.
     */
    public static DefaultMethodInfoBuilder<? extends DefaultElementInfo, ? extends DefaultElementInfoBuilder<?, ?>>
                    builder() {
        return new DefaultMethodInfoBuilder() {};
    }

    @Override
    public DefaultMethodInfoBuilder<? extends DefaultElementInfo, ? extends DefaultElementInfoBuilder<?, ?>>
                    toBuilder() {
        return new DefaultMethodInfoBuilder(this) {};
    }

    @Override
    public boolean hasAnnotation(Class<? extends Annotation> annoType) {
        return annotations().stream()
                .anyMatch((a) -> a.typeName().name().equals(annoType.getName()));
    }


    /**
     * Builder for {@link DefaultMethodInfo}.
     *
     * @param <B> the builder type
     * @param <C> the concrete type being build
     */
    public abstract static class DefaultMethodInfoBuilder<C extends DefaultMethodInfo, B extends DefaultMethodInfoBuilder<C, B>>
                            extends DefaultElementInfoBuilder<C, B> {
        /*@Singular("throwableTypeName")*/ private List<String> throwableTypeNames;
        /*@Singular("parameter")*/ private List<ElementInfo> parameterInfo;

        protected DefaultMethodInfoBuilder() {
        }

        protected DefaultMethodInfoBuilder(C c) {
            this.throwableTypeNames = c.getThrowableTypeNames();
            this.parameterInfo = c.getParameterInfo();
        }

        /**
         * Builds the {@link DefaultMethodInfo}.
         *
         * @return the fluent builder instance
         */
        public C build() {
            return (C) new DefaultMethodInfo(this);
        }

        /**
         * Sets the collection of throwable type names.
         *
         * @param throwableTypeNames the collection of throwable type names to set
         * @return this fluent builder
         */
        public B throwableTypeNames(Collection<String> throwableTypeNames) {
            this.throwableTypeNames = Objects.isNull(throwableTypeNames) ? null : new LinkedList<>(throwableTypeNames);
            return (B) this;
        }

        /**
         * Sets the collection of params info names.
         *
         * @param parameterInfo the collection of parameter infos to set
         * @return this fluent builder
         */
        public B parameterInfo(Collection<ElementInfo> parameterInfo) {
            this.parameterInfo = Objects.isNull(parameterInfo) ? null : new LinkedList<>(parameterInfo);
            return (B) this;
        }

    }

}
