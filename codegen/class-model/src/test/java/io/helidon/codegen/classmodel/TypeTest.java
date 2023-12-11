package io.helidon.codegen.classmodel;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Set;

import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class TypeTest {
    @Test
    void testPlainType() throws IOException {
        assertThat(write(TypeNames.STRING), is("java.lang.String"));
    }

    @Test
    void testGenericType() throws IOException {
        assertThat(write(TypeName.builder(TypeNames.LIST)
                                 .addTypeArgument(TypeNames.STRING)
                                 .build()), is("java.util.List<java.lang.String>"));
    }

    @Test
    void testNestedGenericType() throws IOException {
        assertThat(write(TypeName.builder(TypeNames.LIST)
                                 .addTypeArgument(TypeName.builder(TypeNames.SUPPLIER)
                                                          .addTypeArgument(TypeNames.STRING)
                                                          .build())
                                 .build()), is("java.util.List<java.util.function.Supplier<java.lang.String>>"));
    }

    @Test
    void testWildcardType() throws IOException {
        assertThat(write(TypeName.builder(TypeNames.SUPPLIER)
                                 .addTypeArgument(TypeName.builder(TypeName.create(
                                                 "io.helidon.inject.api.InjectionPointInfo"))
                                                          .wildcard(true)
                                                          .build())
                                 .build()),
                   is("java.util.function.Supplier<? extends io.helidon.inject.api.InjectionPointInfo>"));
    }

    private String write(TypeName typeName) throws IOException {
        Type classModelType = Type.fromTypeName(typeName);
        StringWriter stringWriter = new StringWriter();
        ModelWriter modelWriter = new ModelWriter(stringWriter, "");
        classModelType.writeComponent(modelWriter, Set.of(), ImportOrganizer.builder()
                .packageName("io.helidon.tests")
                .typeName("MyType")
                .build(), ClassType.CLASS);

        return stringWriter.toString();
    }
}
