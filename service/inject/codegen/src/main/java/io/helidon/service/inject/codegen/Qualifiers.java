package io.helidon.service.inject.codegen;

import java.util.Iterator;
import java.util.Set;
import java.util.stream.Collectors;

import io.helidon.codegen.classmodel.ClassModel;
import io.helidon.codegen.classmodel.Field;
import io.helidon.common.types.Annotated;
import io.helidon.common.types.Annotation;

import static io.helidon.service.inject.codegen.InjectCodegenTypes.INJECTION_QUALIFIER;

class Qualifiers {
    static Set<Annotation> qualifiers(Annotated annotated) {
        return annotated.annotations()
                .stream()
                .filter(it -> it.hasMetaAnnotation(INJECTION_QUALIFIER))
                .collect(Collectors.toUnmodifiableSet());
    }

    static void generateQualifiersConstant(ClassModel.Builder classModel, Set<Annotation> qualifiers) {
        classModel.addField(qualifiersField -> qualifiersField
                .isStatic(true)
                .isFinal(true)
                .name("QUALIFIERS")
                .type(InjectionExtension.SET_OF_QUALIFIERS)
                .addContent(Set.class)
                .addContent(".of(")
                .update(it -> {
                    Iterator<Annotation> iterator = qualifiers.iterator();
                    while (iterator.hasNext()) {
                        codeGenQualifier(it, iterator.next());
                        if (iterator.hasNext()) {
                            it.addContent(", ");
                        }
                    }
                })
                .addContent(")"));
    }

    private static void codeGenQualifier(Field.Builder field, Annotation qualifier) {
        if (qualifier.value().isPresent()) {
            field.addContent(InjectCodegenTypes.INJECT_QUALIFIER)
                    .addContent(".create(")
                    .addContentCreate(qualifier.typeName())
                    .addContent(", \"" + qualifier.value().get() + "\")");
            return;
        }

        field.addContent(InjectCodegenTypes.INJECT_QUALIFIER)
                .addContent(".create(")
                .addContentCreate(qualifier.typeName())
                .addContent(")");
    }
}
