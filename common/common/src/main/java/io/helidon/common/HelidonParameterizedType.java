package io.helidon.common;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Objects;

class HelidonParameterizedType implements ParameterizedType {

    private final Class<?> type;
    private final Type[] typeArgs;

    HelidonParameterizedType(Class<?> type, Type[] typeArgs) {
        this.type = type;
        this.typeArgs = Arrays.copyOf(typeArgs, typeArgs.length);
    }

    @Override
    public Type[] getActualTypeArguments() {
        return typeArgs;
    }

    @Override
    public Type getRawType() {
        return type;
    }

    @Override
    public Type getOwnerType() {
        return null;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof ParameterizedType) {
            // Check that information is equivalent
            ParameterizedType that = (ParameterizedType) o;

            if (this == that)
                return true;

            Type thatRawType = that.getRawType();

            return Objects.equals(type, thatRawType) &&
                    Arrays.equals(typeArgs,
                                  that.getActualTypeArguments());
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(typeArgs) ^ Objects.hashCode(type);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(type.getName());
        if (typeArgs.length > 0) {
            sb.append("<");
            for (Type typeArg : typeArgs) {
                sb.append(typeArg.getTypeName());
            }
            sb.append(">");
        }
        return sb.toString();
    }

}
