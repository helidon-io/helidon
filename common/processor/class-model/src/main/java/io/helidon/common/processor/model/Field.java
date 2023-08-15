package io.helidon.common.processor.model;

import java.io.IOException;
import java.util.Set;

import io.helidon.common.types.TypeName;

/**
 * Field model representation.
 */
public final class Field extends AnnotatedComponent implements Comparable<Field> {

    private final Content defaultValue;
    private final boolean isFinal;
    private final boolean isStatic;

    private Field(Builder builder) {
        super(builder);
        this.defaultValue = builder.defaultValueBuilder.build();
        this.isFinal = builder.isFinal;
        this.isStatic = builder.isStatic;
    }

    public static Builder builder() {
        return new Builder().accessModifier(AccessModifier.PRIVATE);
    }

    @Override
    void writeComponent(ModelWriter writer, Set<String> declaredTokens, ImportOrganizer imports, ClassType classType)
            throws IOException {
        if (javadoc().generate()) {
            javadoc().writeComponent(writer, declaredTokens, imports, classType);
            writer.write("\n");
        }
        for (Annotation annotation : annotations()) {
            annotation.writeComponent(writer, declaredTokens, imports, classType);
            writer.write("\n");
        }
        if (classType != ClassType.INTERFACE) {
            if (AccessModifier.PACKAGE_PRIVATE != accessModifier()) {
                writer.write(accessModifier().modifierName());
                writer.write(" ");
            }
            if (isStatic) {
                writer.write("static ");
            }
            if (isFinal) {
                writer.write("final ");
            }
        }
        type().writeComponent(writer, declaredTokens, imports, classType);
        writer.write(" ");
        writer.write(name());
        if (defaultValue.hasBody()) {
            writer.write(" = ");
            defaultValue.writeBody(writer, imports);
            writer.write(";");
        } else {
            writer.write(";");
        }
    }

    @Override
    void addImports(ImportOrganizer.Builder imports) {
        super.addImports(imports);
        type().addImports(imports);
        defaultValue.addImports(imports);
    }

    boolean isStatic() {
        return isStatic;
    }

    @Override
    public int compareTo(Field other) {
        if (accessModifier() == other.accessModifier()) {
            if (isFinal == other.isFinal) {
                if (type().simpleTypeName().equals(other.type().simpleTypeName())) {
                    return name().compareTo(other.name());
                }
                return type().simpleTypeName().compareTo(other.type().simpleTypeName());
            }
            //final fields should be before non-final
            return Boolean.compare(other.isFinal, isFinal);
        } else {
            return accessModifier().compareTo(other.accessModifier());
        }
    }

    @Override
    public String toString() {
        if (defaultValue.hasBody()) {
            return accessModifier().modifierName() + " " + type().fqTypeName() + " " + name() + " = " + defaultValue;
        }
        return accessModifier().modifierName() + " " + type().fqTypeName() + " " + name();
    }

    /**
     * Fluent API builder for {@link Field}.
     */
    public static final class Builder extends AnnotatedComponent.Builder<Builder, Field> {

        private final Content.Builder defaultValueBuilder = Content.builder();
        private boolean isFinal = false;
        private boolean isStatic = false;

        private Builder() {
        }

        @Override
        public Field build() {
            return new Field(this);
        }

        /**
         * Set default value this field should be initialized with.
         *
         * @param defaultValue default value
         * @return updated builder instance
         */
        public Builder defaultValue(String defaultValue) {
            if (defaultValue != null
                    && type().fqTypeName().equals(String.class.getName())
                    && !type().isArray()
                    && !defaultValue.startsWith("\"")
                    && !defaultValue.endsWith("\"")) {
                defaultValueBuilder.content("\"" + defaultValue + "\"");
            } else {
                defaultValueBuilder.content(defaultValue);
            }
            return this;
        }

        /**
         * Whether this field is final.
         *
         * @param isFinal final field
         * @return updated builder instance
         */
        public Builder isFinal(boolean isFinal) {
            this.isFinal = isFinal;
            return this;
        }

        /**
         * Whether this field is static.
         *
         * @param isStatic static field
         * @return updated builder instance
         */
        public Builder isStatic(boolean isStatic) {
            this.isStatic = isStatic;
            return this;
        }

        @Override
        public Builder type(TypeName type) {
            return super.type(type);
        }

        @Override
        public Builder type(String type) {
            return super.type(type);
        }

        @Override
        public Builder type(Class<?> type) {
            return super.type(type);
        }

        @Override
        public Builder accessModifier(AccessModifier accessModifier) {
            return super.accessModifier(accessModifier);
        }
    }
}
