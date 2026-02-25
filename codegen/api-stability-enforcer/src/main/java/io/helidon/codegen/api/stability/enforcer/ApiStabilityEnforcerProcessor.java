package io.helidon.codegen.api.stability.enforcer;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;

/**
 * Annotation processor enforcing stability annotation on public types in Helidon production modules.
 */
public class ApiStabilityEnforcerProcessor extends AbstractProcessor {
    private Messager messager;
    private Set<TypeElement> stabilityAnnotations;

    /**
     * Required by {@link java.util.ServiceLoader}.
     */
    public ApiStabilityEnforcerProcessor() {
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Set.of("*");
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.RELEASE_21;
    }

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);

        var elements = processingEnv.getElementUtils();

        var previewAnnotation = elements.getTypeElement("io.helidon.common.Api.Preview");
        var incubatingAnnotation = elements.getTypeElement("io.helidon.common.Api.Incubating");
        var internalAnnotation = elements.getTypeElement("io.helidon.common.Api.Internal");
        var deprecatedAnnotation = elements.getTypeElement("io.helidon.common.Api.Deprecated");
        var stableAnnotation = elements.getTypeElement("io.helidon.common.Api.Stable");

        this.stabilityAnnotations = Set.of(internalAnnotation,
                                           incubatingAnnotation,
                                           previewAnnotation,
                                           stableAnnotation,
                                           deprecatedAnnotation);

        this.messager = processingEnv.getMessager();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        Set<? extends Element> rootElements = roundEnv.getRootElements();
        for (Element rootElement : rootElements) {
            if (rootElement instanceof TypeElement type) {
                if (type.getModifiers().contains(Modifier.PUBLIC)) {
                    process(type);
                }
            }
        }
        return false;
    }

    private void process(TypeElement type) {
        List<Element> discovered = new ArrayList<>();

        for (var am : type.getAnnotationMirrors()) {
            var annotation = am.getAnnotationType()
                    .asElement();

            if (this.stabilityAnnotations.contains(annotation)) {
                discovered.add(annotation);
            }
        }

        if (discovered.isEmpty()) {
            messager.printError("Public API " + type + " is missing stability annotation (@Api.*)", type);
            return;
        }

        if (discovered.size() > 1) {
            messager.printError("Public API " + type + " has more than one stability annotation (@Api.*)", type);
            return;
        }
    }
}
