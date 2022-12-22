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

package io.helidon.pico.services;

/**
 * Models all dependencies on a particular {@link io.helidon.pico.ServiceInfo}.
 */
class Dependencies {

//    /**
//     * Combine the dependency info from the two sources to create a merged set of dependencies.
//     *
//     * @param parentDeps    the parent set of dependencies
//     * @param deps          the child set of dependencies
//     * @return              the combined set
//     */
//    public static DependenciesInfo combine(DependenciesInfo parentDeps,
//                                           DependenciesInfo deps) {
//        Map<ServiceInfo, Set<DependencyInfo>> serviceInfoDependencies =
//                new LinkedHashMap<>(parentDeps.serviceInfoDependencies());
//        Set<DependencyInfo> allDeps =
//                new LinkedHashSet<>(parentDeps.allDependencies());
//        deps.serviceInfoDependencies().forEach((depTo, depSet) -> {
//            Set<DependencyInfo> set = serviceInfoDependencies.get(depTo);
//            if (Objects.isNull(set)) {
//                Object prev = serviceInfoDependencies.put(depTo, depSet);
//                assert (Objects.isNull(prev) || prev.equals(depSet));
//                set = depSet;
//            } else {
//                set.addAll(depSet);
//            }
//
//            allDeps.addAll(set);
//        });
//
//        Dependencies newDeps = new Dependencies(deps.getForServiceTypeName(), serviceInfoDependencies,
//                new LinkedList<>(allDeps));
////        assert (newDeps.getDependencies().size() == deps.getDependencies().size() + parentDeps.getDependencies().size());
//        return newDeps;
//    }
//
//    /**
//     * Remove a dependency from the list.
//     *
//     * @param serviceTypeName the service type to dependency to remove
//     * @return the new set of dependencies
//     */
//    public Dependencies removeDependency(String serviceTypeName) {
//        Map<ServiceInfo, Set<io.helidon.pico.spi.ext.Dependency<Object>>> serviceInfoDependencies = new LinkedHashMap<>(this.serviceInfoDependencies);
//        List<io.helidon.pico.spi.ext.Dependency<Object>> dependencies = new LinkedList<>(this.dependencies);
//
//        this.serviceInfoDependencies.forEach((serviceInfo, value) -> {
//            if (serviceInfo.matchesContract(serviceTypeName)) {
//                Object removed = serviceInfoDependencies.remove(serviceInfo);
//                assert (Objects.nonNull(removed));
//            }
//        });
//
//        this.dependencies.forEach(dep -> {
//            ServiceInfo serviceInfo = dep.getDependencyTo();
//            if (serviceInfo.matchesContract(serviceTypeName)) {
//                boolean removed = dependencies.remove(dep);
//                assert (removed);
//            }
//        });
//
//        return new Dependencies(getForServiceTypeName(),
//                                                new LinkedHashMap<>(serviceInfoDependencies),
//                                                new LinkedList<>(dependencies));
//    }
//
//    /**
//     * {@inheritDoc}
//     */
//    @Override
//    public String toString() {
//        return getForServiceTypeName() + " : " + getServiceInfoDependencies();
//    }
//
//    /**
//     * {@inheritDoc}
//     */
//    @JsonIgnore
//    @Override
//    @SuppressWarnings("unchecked")
//    public Map<ServiceInfo, Set<DependencyInfo>> getServiceInfoDependencyMap() {
//        return (Map) getServiceInfoDependencies();
//    }
//
//    /**
//     * {@inheritDoc}
//     */
//    @JsonIgnore
//    @Override
//    @SuppressWarnings("unchecked")
//    public List<DependencyInfo> getAllDependencies() {
//        return (List) getDependencies();
//    }
//
//    /**
//     * Creates a builder.
//     *
//     * @return the fluent builder
//     */
//    public static Builder builder() {
//        return new Builder();
//    }
//
//    @Override
//    public Map<ServiceInfo, Set<DependencyInfo>> serviceInfoDependencies() {
//        return null;
//    }
//
//    @Override
//    public Set<DependencyInfo> allDependencies() {
//        return null;
//    }
//
//    @Override
//    public Optional<TypeName> fromServiceTypeName() {
//        return Optional.empty();
//    }
//
//    /**
//     * The Builder.
//     */
//    public static class Builder {
//        private String forServiceTypeName;
//        private final Map<ServiceInfo, Set<io.helidon.pico.spi.ext.Dependency<Object>>> serviceInfoDependencies = new LinkedHashMap<>();
//        private final List<io.helidon.pico.spi.ext.Dependency<Object>> allDependencies = new LinkedList<>();
//        private BuilderContinuation continuation;
//
//        private Builder() {
//        }
//
//        public String getForServiceTypeName() {
//            return forServiceTypeName;
//        }
//
//        /**
//         * Sets the service type name.
//         *
//         * @param forServiceTypeName service type name
//         * @return the continuation builder
//         */
//        public BuilderContinuation forServiceTypeName(String forServiceTypeName) {
//            assert (Objects.isNull(this.forServiceTypeName) || this.forServiceTypeName.equals(forServiceTypeName));
//            this.forServiceTypeName = Objects.requireNonNull(forServiceTypeName);
//            if (Objects.isNull(continuation)) {
//                continuation = new BuilderContinuation(this);
//            }
//            return continuation;
//        }
//
//        /**
//         * Builds the dependencies.
//         *
//         * @return dependencies
//         */
//        public Dependencies build() {
//            continuation = null;
//            return new Dependencies(forServiceTypeName, serviceInfoDependencies, allDependencies);
//        }
//    }
//
//    /**
//     * The continuation builder.
//     */
//    @SuppressWarnings("unchecked")
//    public static class BuilderContinuation {
//        private Builder builder;
//        private DefaultInjectionPointInfo.DefaultInjectionPointInfoBuilder ipInfoBuilder;
//
//        private BuilderContinuation(Builder builder) {
//            assert (Objects.nonNull(builder));
//            this.builder = builder;
//        }
//
//        /**
//         * Adds a new item.
//         *
//         * @param elemName the element name
//         * @param elemType the element type
//         * @param kind the element kind
//         * @param access the element access
//         * @return the builder
//         */
//        public BuilderContinuation add(String elemName,
//                                       Class<?> elemType,
//                                       InjectionPointInfo.ElementKind kind,
//                                       InjectionPointInfo.Access access) {
//            if (InjectionPointInfo.ElementKind.FIELD != kind && Void.class != elemType) {
//                throw new AssertionError("should not use this method for method types");
//            }
//            return add(builder.forServiceTypeName, elemName, elemType.getName(), kind, 0, access);
//        }
//
//        /**
//         * Adds a new item.
//         *
//         * @param elemName the element name
//         * @param elemType the element type
//         * @param kind the element kind
//         * @param elemArgs for methods, the number of arguments the method takes
//         * @param access the element access
//         * @return the builder
//         */
//        public BuilderContinuation add(String elemName,
//                                       Class<?> elemType,
//                                       InjectionPointInfo.ElementKind kind,
//                                       int elemArgs,
//                                       InjectionPointInfo.Access access) {
//            if (InjectionPointInfo.ElementKind.FIELD == kind && 0 != elemArgs) {
//                throw new AssertionError("should not have args for field: " + elemName);
//            }
//            return add(builder.forServiceTypeName, elemName, elemType.getName(), kind, elemArgs, access);
//        }
//
//        /**
//         * Adds a new item.
//         *
//         * @param serviceType the service type
//         * @param elemName the element name
//         * @param elemType the element type
//         * @param kind the element kind
//         * @param access the element access
//         * @return the builder
//         */
//        public BuilderContinuation add(Class<?> serviceType,
//                                       String elemName,
//                                       Class<?> elemType,
//                                       InjectionPointInfo.ElementKind kind,
//                                       InjectionPointInfo.Access access) {
//            if (InjectionPointInfo.ElementKind.FIELD != kind) {
//                throw new AssertionError("should not use this method for method types");
//            }
//            return add(serviceType.getName(), elemName, elemType.getName(), kind, 0, access);
//        }
//
//        /**
//         * Adds a new item.
//         *
//         * @param serviceType the service type
//         * @param elemName the element name
//         * @param elemType the element type
//         * @param kind the element kind
//         * @param elemArgs for methods, the number of arguments the method accepts
//         * @param access the element access
//         * @return the builder
//         */
//        public BuilderContinuation add(Class<?> serviceType,
//                                       String elemName,
//                                       Class<?> elemType,
//                                       InjectionPointInfo.ElementKind kind,
//                                       int elemArgs,
//                                       InjectionPointInfo.Access access) {
//            return add(serviceType.getName(), elemName, elemType.getName(), kind, elemArgs, access);
//        }
//
//        /**
//         * Adds a new item.
//         *
//         * @param serviceTypeName the service type
//         * @param elemName the element name
//         * @param elemTypeName the element type
//         * @param kind the element kind
//         * @param elemArgs for methods, this is the number of arguments the method accepts
//         * @param access the element access
//         * @return the builder
//         */
//        public BuilderContinuation add(String serviceTypeName,
//                                       String elemName,
//                                       String elemTypeName,
//                                       InjectionPointInfo.ElementKind kind,
//                                       int elemArgs,
//                                       InjectionPointInfo.Access access) {
//            commitLastDependency();
//
//            ipInfoBuilder = DefaultInjectionPointInfo.builder()
//                    .serviceTypeName(serviceTypeName)
//                    .access(access)
//                    .elementKind(kind)
//                    .elementTypeName(elemTypeName)
//                    .elementName(elemName)
//                    .elementArgs(elemArgs);
//
//            return this;
//        }
//
//        /**
//         * Adds a new item.
//         *
//         * @param ipInfo the injection point info already built
//         * @return the builder
//         */
//        public BuilderContinuation add(DefaultInjectionPointInfo ipInfo) {
//            commitLastDependency();
//
//            ipInfoBuilder = ipInfo.toBuilder();
//            return this;
//        }
//
//        /**
//         * Sets the element offset.
//         *
//         * @param offset the offset
//         * @return the builder
//         */
//        public BuilderContinuation elemOffset(Integer offset) {
//            ipInfoBuilder.elementOffset(offset);
//            return this;
//        }
//
//        /**
//         * Sets the flag indicating the injection point is a list.
//         *
//         * @return the builder
//         */
//        public BuilderContinuation setIsListWrapped() {
//            return setIsListWrapped(true);
//        }
//
//        /**
//         * Sets the flag indicating the injection point is a list.
//         *
//         * @param val true if list type
//         * @return the builder
//         */
//        public BuilderContinuation setIsListWrapped(boolean val) {
//            ipInfoBuilder.listWrapped(val);
//            return this;
//        }
//
//        /**
//         * Sets the flag indicating the injection point is a provider.
//         *
//         * @return the builder
//         */
//        public BuilderContinuation setIsProviderWrapped() {
//            return setIsProviderWrapped(true);
//        }
//
//        /**
//         * Sets the flag indicating the injection point is a provider.
//         *
//         * @param val true if provider type
//         * @return the builder
//         */
//        public BuilderContinuation setIsProviderWrapped(boolean val) {
//            ipInfoBuilder.providerWrapped(val);
//            return this;
//        }
//
//        /**
//         * Sets the flag indicating the injection point is an {@link java.util.Optional} type.
//         *
//         * @return the builder
//         */
//        public BuilderContinuation setIsOptionalWrapped() {
//            return setIsOptionalWrapped(true);
//        }
//
//        /**
//         * Sets the flag indicating the injection point is an {@link java.util.Optional} type.
//         *
//         * @param val true if list type
//         * @return the builder
//         */
//        public BuilderContinuation setIsOptionalWrapped(boolean val) {
//            ipInfoBuilder.optionalWrapped(val);
//            return this;
//        }
//
//        /**
//         * Sets the optional qualified name of the injection point.
//         *
//         * @param val the name
//         * @return the builder
//         */
//        public BuilderContinuation named(String val) {
//            ipInfoBuilder.qualifier(DefaultQualifierAndValue.createNamed(val));
//            return this;
//        }
//
//        /**
//         * Sets the optional qualifier of the injection point.
//         *
//         * @param val the qualifier
//         * @return the builder
//         */
//        public BuilderContinuation qualifier(Class<? extends Annotation> val) {
//            ipInfoBuilder.qualifier(DefaultQualifierAndValue.create(val));
//            return this;
//        }
//
//        /**
//         * Sets the optional qualifier of the injection point.
//         *
//         * @param val the qualifier
//         * @return the builder
//         */
//        public BuilderContinuation qualifier(QualifierAndValue val) {
//            ipInfoBuilder.qualifier(val);
//            return this;
//        }
//
//        /**
//         * Sets the optional qualifier of the injection point.
//         *
//         * @param val the qualifier
//         * @return the builder
//         */
//        public BuilderContinuation qualifiers(Collection<QualifierAndValue> val) {
//            if (Objects.nonNull(val)) {
//                ipInfoBuilder.qualifiers(val);
//            }
//            return this;
//        }
//
//        /**
//         * Sets the flag indicating that the injection point is static.
//         *
//         * @return the builder
//         */
//        public BuilderContinuation setIsStatic() {
//            return setIsStatic(true);
//        }
//
//        /**
//         * Sets the flag indicating that the injection point is static.
//         *
//         * @param val flag indicating if static
//         * @return the builder
//         */
//        public BuilderContinuation setIsStatic(boolean val) {
//            ipInfoBuilder.staticDecl(val);
//            return this;
//        }
//
//        /**
//         * Commits the last dependency item to complete the builder.
//         *
//         * @return the built dependencies
//         */
//        public io.helidon.pico.spi.ext.Dependency<?> commitLastDependency() {
//            assert (Objects.nonNull(builder));
//
//            if (Objects.isNull(ipInfoBuilder)) {
//                return null;
//            }
//
//            final DefaultInjectionPointInfo ipInfo = ipInfoBuilder.build();
//            ipInfoBuilder = null;
//
//            AtomicReference<io.helidon.pico.spi.ext.Dependency<Object>> depRef = new AtomicReference<>();
//            ServiceInfo depServiceInfo = ipInfo.toDependencyToServiceInfo();
//            builder.serviceInfoDependencies.compute(depServiceInfo, (key, val) -> {
//                if (Objects.isNull(val)) {
//                    val = new LinkedHashSet<>();
//                    depRef.set(new io.helidon.pico.spi.ext.Dependency<>(key, ipInfo));
//                } else {
//                    io.helidon.pico.spi.ext.Dependency<Object> firstDep = val.iterator().next();
//
//                    // needed to avoid heap proliferation of serviceInfo's - we pick the 1st created serviceInfo...
//                    key = firstDep.getDependencyTo();
//                    ipInfo.setDependencyToServiceInfo(key);
//                    depRef.set(new io.helidon.pico.spi.ext.Dependency<>(key, ipInfo));
//                }
//
//                val.add(depRef.get());
//
//                return val;
//            });
//            builder.allDependencies.add(depRef.get());
//            return depRef.get();
//        }
//
//        /**
//         * Creates a builder.
//         *
//         * @return the builder
//         */
//        public Builder build() {
//            commitLastDependency();
//            Builder last = Objects.requireNonNull(builder);
//            builder = null;
//            return last;
//        }
//    }
//
}
