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
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.helidon.pico.tools.creator.ActivatorCodeGenDetail;
import io.helidon.pico.tools.creator.ActivatorCreatorConfigOptions;
import io.helidon.pico.tools.creator.ActivatorCreatorResponse;
import io.helidon.pico.tools.creator.InterceptionPlan;
import io.helidon.pico.tools.creator.ModuleDetail;
import io.helidon.pico.types.TypeName;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.ToString;

/**
 * Default implementation for {@link io.helidon.pico.tools.creator.ActivatorCreatorResponse}.
 */
//@Builder
@ToString
@Getter
@SuppressWarnings("unchecked")
public class DefaultActivatorCreatorResponse extends DefaultGeneralResponse implements ActivatorCreatorResponse {
    @JsonIgnore private final ActivatorCreatorConfigOptions configOptions;

    // source related ...
    private final List<TypeName> serviceTypeNames;
    @JsonIgnore /*@Singular("serviceTypeDetail")*/ private final Map<TypeName, ActivatorCodeGenDetail> serviceTypeDetails;
    @JsonIgnore /*@Singular("serviceTypeInterceptorPlan")*/ private final Map<TypeName, InterceptionPlan> serviceTypeInterceptorPlans;

    // module related ...
    @JsonIgnore private final ModuleDetail moduleDetail;

    // resource related ...
    @JsonIgnore /*@Singular("metaInfServiceList")*/ private final Map<String, List<String>> metaInfServices;

    private final TypeName applicationTypeName;

    protected DefaultActivatorCreatorResponse(DefaultActivatorCreatorResponseBuilder builder) {
        super(builder);
        this.configOptions = builder.configOptions;
        this.serviceTypeNames = Objects.nonNull(builder.serviceTypeNames)
                ? Collections.unmodifiableList(builder.serviceTypeNames) : Collections.emptyList();
        this.serviceTypeDetails = Objects.nonNull(builder.serviceTypeDetails)
                ? Collections.unmodifiableMap(builder.serviceTypeDetails) : Collections.emptyMap();
        this.serviceTypeInterceptorPlans = Objects.nonNull(builder.serviceTypeInterceptorPlans)
                ? Collections.unmodifiableMap(builder.serviceTypeInterceptorPlans) : Collections.emptyMap();
        this.moduleDetail = builder.moduleDetail;
        this.metaInfServices = Objects.nonNull(builder.metaInfServices)
                ? Collections.unmodifiableMap(builder.metaInfServices) : Collections.emptyMap();
        this.applicationTypeName = builder.applicationTypeName;
    }

    public static DefaultActivatorCreatorResponseBuilder
                        <? extends DefaultGeneralCodeGenNames, ? extends DefaultActivatorCreatorResponseBuilder<?, ?>>
                        builder() {
        return new DefaultActivatorCreatorResponseBuilder() { };
    }

    public abstract static class DefaultActivatorCreatorResponseBuilder
            <C extends DefaultActivatorCreatorResponse, B extends DefaultActivatorCreatorResponseBuilder<C, B>>
            extends DefaultGeneralResponseBuilder<C, B> {
        private ActivatorCreatorConfigOptions configOptions;
        private List<TypeName> serviceTypeNames;
        private Map<TypeName, ActivatorCodeGenDetail> serviceTypeDetails;
        private Map<TypeName, InterceptionPlan> serviceTypeInterceptorPlans;
        private ModuleDetail moduleDetail;
        private Map<String, List<String>> metaInfServices;
        private TypeName applicationTypeName;

        public C build() {
            return (C) new DefaultActivatorCreatorResponse(this);
        }

        public B configOptions(ActivatorCreatorConfigOptions val) {
            this.configOptions = val;
            return (B) this;
        }

        public B serviceTypeNames(Collection<TypeName> val) {
            this.serviceTypeNames = Objects.isNull(val) ? null : new LinkedList<>(val);
            return (B) this;
        }

        public B serviceTypeDetails(Map<TypeName, ActivatorCodeGenDetail> val) {
            this.serviceTypeDetails = Objects.isNull(val) ? null : new LinkedHashMap<>(val);
            return (B) this;
        }

        public B serviceTypeInterceptorPlan(TypeName interceptorTypeName, InterceptionPlan interceptionPlan) {
            if (Objects.isNull(this.serviceTypeInterceptorPlans)) {
                this.serviceTypeInterceptorPlans = new LinkedHashMap<>();
            }
            this.serviceTypeInterceptorPlans.put(interceptorTypeName, Objects.requireNonNull(interceptionPlan));
            return (B) this;
        }

        public B serviceTypeInterceptorPlans(Map<TypeName, InterceptionPlan> val) {
            this.serviceTypeInterceptorPlans = Objects.isNull(val) ? null : new LinkedHashMap<>(val);
            return (B) this;
        }

        public B moduleDetail(ModuleDetail val) {
            this.moduleDetail = val;
            return (B) this;
        }

        public B metaInfServices(Map<String, List<String>> val) {
            this.metaInfServices = Objects.isNull(val) ? null : new LinkedHashMap<>(val);
            return (B) this;
        }

        public B applicationTypeName(TypeName val) {
            this.applicationTypeName = val;
            return (B) this;
        }
    }

}
