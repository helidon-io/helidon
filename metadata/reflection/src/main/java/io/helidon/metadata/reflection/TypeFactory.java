package io.helidon.metadata.reflection;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.helidon.common.types.TypeName;

/**
 * Reflection based support to get classes and types.
 */
public final class TypeFactory {
    private TypeFactory() {
    }

    public static Class<?> toClass(TypeName typeName) {
        try {
            return Class.forName(typeName.fqName());
        } catch (ClassNotFoundException e) {
            // try again with $ instead of . for compound types
            String className = typeName.packageName() + "." + typeName.classNameWithEnclosingNames().replace('.', '$');
            try {
                return Class.forName(className);
            } catch (ClassNotFoundException ex) {
                throw new IllegalArgumentException("Failed to convert type name to a class: " + typeName.resolvedName());
            }
        }
    }

    public static Type toType(TypeName typeName) {
        if (typeName.wildcard()) {
            return new WildcardTypeImpl(typeName);
        }

        if (typeName.typeArguments().isEmpty()) {
            // simple class
            return toClass(typeName);
        }

        return new ParameterizedTypeImpl(typeName, toClass(typeName));
    }

    private static class WildcardTypeImpl implements WildcardType {
        private final Type[] upperBounds;
        private final Type[] lowerBounds;

        private WildcardTypeImpl(TypeName typeName) {
            if (!typeName.upperBounds().isEmpty()) {
                this.lowerBounds = new Type[0];
                this.upperBounds = typeName.upperBounds()
                        .stream()
                        .map(TypeFactory::toType)
                        .toArray(Type[]::new);
            } else if (typeName.lowerBounds().isEmpty()) {
                this.lowerBounds = typeName.lowerBounds()
                        .stream()
                        .map(TypeFactory::toType)
                        .toArray(Type[]::new);
                this.upperBounds = new Type[0];
            } else {
                this.lowerBounds = new Type[0];
                this.upperBounds = new Type[0];
            }

        }

        @Override
        public Type[] getUpperBounds() {
            return new Type[0];
        }

        @Override
        public Type[] getLowerBounds() {
            return new Type[0];
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder("?");
            if (upperBounds.length > 0) {
                sb.append(" extends ");
                sb.append(Stream.of(upperBounds)
                                  .map(Type::getTypeName)
                                  .collect(Collectors.joining(" | ")));
            } else if (lowerBounds.length > 0) {
                sb.append(" super ");
                sb.append(lowerBounds[0].getTypeName());
            }

            return sb.toString();
        }
    }

    private static class ParameterizedTypeImpl implements ParameterizedType {
        private final TypeName typeName;
        private final Class<?> aClass;

        ParameterizedTypeImpl(TypeName typeName, Class<?> aClass) {
            this.typeName = typeName;
            this.aClass = aClass;
        }

        @Override
        public Type[] getActualTypeArguments() {
            return typeName.typeArguments()
                    .stream()
                    .map(TypeFactory::toType)
                    .toArray(Type[]::new);
        }

        @Override
        public Type getRawType() {
            return aClass;
        }

        @Override
        public Type getOwnerType() {
            return null;
        }

        @Override
        public String getTypeName() {
            return ParameterizedType.super.getTypeName();
        }
    }
}
