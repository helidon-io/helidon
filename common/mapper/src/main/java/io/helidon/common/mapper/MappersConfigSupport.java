package io.helidon.common.mapper;

import java.util.List;
import java.util.Set;

import io.helidon.builder.api.Prototype;
import io.helidon.common.GenericType;
import io.helidon.common.Weighted;
import io.helidon.common.Weights;
import io.helidon.common.mapper.spi.MapperProvider;

final class MappersConfigSupport {
    private MappersConfigSupport() {
    }

    static final class CustomMethods {
        private CustomMethods() {
        }

        /**
         * Add a mapper to the list of mapper.
         *
         * @param builder    ignored
         * @param mapper     the mapper to map source instances to target instances
         * @param sourceType class of the source instance
         * @param targetType class of the target instance
         * @param qualifiers supported qualifiers of this mapper (if none provided, will return a compatible response)
         * @param <S>        type of source
         * @param <T>        type of target
         */
        @Prototype.BuilderMethod
        static <S, T> void addMapper(MappersConfig.BuilderBase<?, ?> builder,
                                     Mapper<S, T> mapper,
                                     Class<S> sourceType,
                                     Class<T> targetType,
                                     String... qualifiers) {
            addMapper(builder, mapper, sourceType, targetType, Weighted.DEFAULT_WEIGHT, qualifiers);
        }

        /**
         * Add a mapper to the list of mapper.
         *
         * @param builder    ignored
         * @param mapper     the mapper to map source instances to target instances
         * @param sourceType generic type of the source instance
         * @param targetType generic type of the target instance
         * @param qualifiers qualifiers of this mapper, if empty, will be a compatible mapper
         * @param <S>        type of source
         * @param <T>        type of target
         */
        @Prototype.BuilderMethod
        static <S, T> void addMapper(MappersConfig.BuilderBase<?, ?> builder,
                                     Mapper<S, T> mapper,
                                     GenericType<S> sourceType,
                                     GenericType<T> targetType,
                                     String... qualifiers) {

            addMapper(builder, mapper, sourceType, targetType, Weighted.DEFAULT_WEIGHT, qualifiers);
        }

        /**
         * Add a mapper to the list of mapper with a custom priority.
         *
         * @param builder    ignored
         * @param mapper     the mapper to map source instances to target instances
         * @param sourceType class of the source instance
         * @param targetType class of the target instance
         * @param weight     weight of the mapper
         * @param qualifiers supported qualifiers of this mapper (if none provided, will return a compatible response)
         * @param <S>        type of source
         * @param <T>        type of target
         */
        @Prototype.BuilderMethod
        static <S, T> void addMapper(MappersConfig.BuilderBase<?, ?> builder,
                                     Mapper<S, T> mapper,
                                     Class<S> sourceType,
                                     Class<T> targetType,
                                     double weight,
                                     String... qualifiers) {
            builder.addMapperProvider(new ClassMapperProvider(mapper,
                                                              sourceType,
                                                              targetType,
                                                              weight,
                                                              Set.of(qualifiers)));
        }

        /**
         * Add a mapper to the list of mapper with custom priority.
         *
         * @param builder    ignored
         * @param mapper     the mapper to map source instances to target instances
         * @param sourceType generic type of the source instance
         * @param targetType generic type of the target instance
         * @param weight     weight of the mapper
         * @param qualifiers qualifiers of this mapper, if empty, will be a compatible mapper
         * @param <S>        type of source
         * @param <T>        type of target
         */
        @Prototype.BuilderMethod
        static <S, T> void addMapper(MappersConfig.BuilderBase<?, ?> builder,
                                     Mapper<S, T> mapper,
                                     GenericType<S> sourceType,
                                     GenericType<T> targetType,
                                     double weight,
                                     String... qualifiers) {
            if (sourceType.isClass() && targetType.isClass()) {
                builder.addMapperProvider(new ClassMapperProvider(mapper,
                                                                  sourceType.rawType(),
                                                                  targetType.rawType(),
                                                                  weight,
                                                                  Set.of(qualifiers)));
            } else {
                builder.addMapperProvider(new GenericMapperProvider(mapper,
                                                                    sourceType,
                                                                    targetType,
                                                                    weight,
                                                                    Set.of(qualifiers)));
            }
        }
    }

    @SuppressWarnings("rawtypes")
    private static final class GenericMapperProvider implements MapperProvider, Weighted {

        private final Mapper mapper;
        private final GenericType sourceType;
        private final GenericType targetType;
        private final double weight;
        private final Set<String> qualifiers;

        private GenericMapperProvider(Mapper mapper,
                                      GenericType sourceType,
                                      GenericType targetType,
                                      double weight,
                                      Set<String> qualifiers) {
            this.mapper = mapper;
            this.sourceType = sourceType;
            this.targetType = targetType;
            this.weight = weight;
            this.qualifiers = qualifiers;
        }

        @Override
        public ProviderResponse mapper(Class<?> sourceClass, Class<?> targetClass, String qualifier) {
            return ProviderResponse.unsupported();
        }

        @Override
        public ProviderResponse mapper(GenericType<?> sourceType, GenericType<?> targetType, String qualifier) {
            if ((sourceType.equals(this.sourceType)) && (targetType.equals(this.targetType))) {
                if (this.qualifiers.contains(qualifier)) {
                    return new ProviderResponse(Support.SUPPORTED, mapper);
                } else {
                    return new ProviderResponse(Support.COMPATIBLE, mapper);
                }

            }
            return ProviderResponse.unsupported();
        }

        @Override
        public double weight() {
            return weight;
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static final class ClassMapperProvider implements MapperProvider, Weighted {
        private final Mapper<Object, Object> mapper;
        private final Class<Object> sourceType;
        private final Class<Object> targetType;
        private final double weight;
        private final Set<String> qualifiers;

        private ClassMapperProvider(Mapper mapper,
                                    Class sourceType,
                                    Class targetType,
                                    double weight,
                                    Set<String> qualifiers) {
            this.mapper = mapper;
            this.sourceType = sourceType;
            this.targetType = targetType;
            this.weight = weight;
            this.qualifiers = qualifiers;
        }

        @Override
        public ProviderResponse mapper(Class<?> sourceClass, Class<?> targetClass, String qualifier) {
            if ((this.sourceType == sourceClass) && (this.targetType == targetClass)) {
                if (this.qualifiers.contains(qualifier)) {
                    return new MapperProvider.ProviderResponse(MapperProvider.Support.SUPPORTED, mapper);
                } else {
                    return new MapperProvider.ProviderResponse(MapperProvider.Support.COMPATIBLE, mapper);
                }
            }
            return MapperProvider.ProviderResponse.unsupported();
        }

        @Override
        public double weight() {
            return weight;
        }
    }

    static class BuilderDecorator implements Prototype.BuilderDecorator<MappersConfig.BuilderBase<?, ?>> {
        BuilderDecorator() {
        }

        @Override
        public void decorate(MappersConfig.BuilderBase<?, ?> target) {
            if (target.useBuiltInMappers()) {
                target.addMapperProvider(new BuiltInMappers());
            }

            List<Mapper<?, ?>> mappers =  target.mappers();
            for (Mapper<?, ?> mapper : mappers) {
                target.addMapperProvider(new GenericMapperProvider(mapper,
                                                                   mapper.sourceType(),
                                                                   mapper.targetType(),
                                                                   Weights.find(mapper, Weighted.DEFAULT_WEIGHT),
                                                                   mapper.qualifiers()));
            }
        }
    }
}
