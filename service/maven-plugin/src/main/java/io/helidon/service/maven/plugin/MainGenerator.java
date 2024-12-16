package io.helidon.service.maven.plugin;

import java.nio.file.Path;
import java.util.List;

import io.helidon.codegen.CodegenUtil;
import io.helidon.codegen.classmodel.ClassModel;
import io.helidon.codegen.classmodel.Method;
import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;

import static io.helidon.service.codegen.ServiceCodegenTypes.REGISTRY_STARTUP_PROVIDER;
import static io.helidon.service.codegen.ServiceCodegenTypes.SERVICE_CONFIG;
import static io.helidon.service.codegen.ServiceCodegenTypes.SERVICE_REGISTRY_MANAGER;

/**
 * Generates the application main class.
 */
class MainGenerator {
    private static final TypeName GENERATOR = TypeName.create(MainGenerator.class);

    private final MavenCodegenContext ctx;

    MainGenerator(MavenCodegenContext ctx) {
        this.ctx = ctx;
    }

    void createMain(WrappedServices services,
                    boolean generateBinding,
                    TypeName bindingTypeName,
                    TypeName generatedType,
                    List<Path> sourcesToCompile) {

        ClassModel.Builder classModel = ClassModel.builder()
                .type(generatedType)
                .accessModifier(AccessModifier.PUBLIC)
                .copyright(CodegenUtil.copyright(GENERATOR,
                                                 GENERATOR,
                                                 generatedType))
                .addAnnotation(CodegenUtil.generatedAnnotation(GENERATOR,
                                                               generatedType,
                                                               generatedType,
                                                               "1",
                                                               ""))
                .addDescriptionLine("Main class generated for Helidon Service Registry Application.")
                .isFinal(true);

        classModel.addConstructor(ctr -> ctr
                .accessModifier(AccessModifier.PRIVATE));

        classModel.addMethod(main -> main
                .accessModifier(AccessModifier.PUBLIC)
                .isStatic(true)
                .returnType(TypeNames.PRIMITIVE_VOID)
                .description("Start the application.")
                .name("main")
                .addParameter(args -> args
                        .type(String[].class)
                        .description("Command line arguments.")
                        .name("args"))
                .update(it -> mainMethodBody(services, generateBinding, bindingTypeName, classModel, it)));

        Path generated = ctx.filer()
                .writeSourceFile(classModel.build());
        sourcesToCompile.add(generated);
    }

    private void mainMethodBody(WrappedServices services,
                                boolean generateBinding,
                                TypeName bindingTypeName,
                                ClassModel.Builder classModel,
                                Method.Builder method) {

        if (!generateBinding) {
            BindingGenerator.runLevelsConstantField(classModel, services);
            method.addContent("var config = ")
                    .addContent(SERVICE_CONFIG)
                    .addContentLine(".builder().runLevels(RUN_LEVELS).build();");
        }

        method.addContent("var manager = ")
                .addContent(SERVICE_REGISTRY_MANAGER)
                .addContent(".start(");

        if (generateBinding) {
            method.addContent(bindingTypeName)
                    .addContentLine(".create());");
        } else {
            method.addContentLine("config);");
        }

        method.addContent(REGISTRY_STARTUP_PROVIDER)
                .addContentLine(".registerShutdownHandler(manager);");
    }

}
