package io.helidon.metadata.codegen.spotbugs;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import io.helidon.codegen.CodegenContext;
import io.helidon.codegen.CodegenException;
import io.helidon.codegen.CodegenUtil;
import io.helidon.codegen.RoundContext;
import io.helidon.codegen.spi.CodegenExtension;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.ElementKind;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypedElementInfo;

import static io.helidon.metadata.codegen.spotbugs.SpotbugsTypes.EXCLUDE;

class SpotbugsCodegen implements CodegenExtension {
    private static final TypeName GENERATOR = TypeName.create(SpotbugsCodegen.class);

    private final CodegenContext ctx;

    private final Set<Match> matches = new HashSet<>();

    SpotbugsCodegen(CodegenContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public void process(RoundContext roundContext) {
        Collection<TypeInfo> typeInfos = roundContext.annotatedTypes(EXCLUDE);
        for (TypeInfo typeInfo : typeInfos) {
            matches.add(Match.create(typeInfo));
        }

        Collection<TypedElementInfo> typedElementInfos = roundContext.annotatedElements(EXCLUDE);
        for (TypedElementInfo typedElementInfo : typedElementInfos) {
            matches.add(Match.create(typedElementInfo));
        }
    }

    @Override
    public void processingOver(RoundContext roundContext) {
        // this will always overwrite the file - we expect spotbugs to be run with clean in maven
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (PrintWriter pw = new PrintWriter(baos, true, StandardCharsets.UTF_8)) {
            // file header
            pw.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
            String copyright = CodegenUtil.copyright(GENERATOR, GENERATOR, GENERATOR);
            pw.println("<!--");
            pw.println(copyright);
            pw.println("-->");
            pw.println("<FindBugsFilter");
            pw.println("        xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"");
            pw.println("        xmlns=\"https://github.com/spotbugs/filter/3.0.0\"");
            pw.print("        xsi:schemaLocation=\"https://github.com/spotbugs/filter/3.0.0 ");
            pw.println("https://raw.githubusercontent.com/spotbugs/spotbugs/3.1.0/spotbugs/etc/findbugsfilter.xsd\">");

            for (Match match : matches) {
                pw.println("    <Match>");
                pw.print("        <!-- ");
                pw.print(match.description);
                pw.println(" -->");
                pw.print("        <Class name=\"");
                pw.print(match.type().packageName());
                pw.print(".");
                pw.print(match.type.classNameWithEnclosingNames().replace('.', '$'));
                pw.println("\"/>");
                switch (match.kind()) {
                case METHOD -> {
                    pw.print("        <Method name=\"");
                    pw.print(match.method.get());
                    pw.println("\"/>");
                }
                case CONSTRUCTOR -> {
                    pw.println("    <Method name=\"&lt;init&gt;\"/>");
                }
                case FIELD -> {
                    pw.print("    <Field name=\"");
                    pw.print(match.method.get());
                    pw.println("\"/>");
                }
                }
                for (String bugPattern : match.bugPatterns()) {
                    pw.print("        <Bug pattern=\"");
                    pw.print(bugPattern);
                    pw.println("\"/>");

                }
                pw.println("    </Match>");
            }

            pw.println("</FindBugsFilter>");
        }

        ctx.filer()
                .writeResource(baos.toByteArray(),
                               "META-INF/ignored/spotbugs-exclude.xml");

    }

    private record Match(TypeName type,
                         List<String> bugPatterns,
                         ElementKind kind,
                         String description,
                         Optional<String> method) {
        private Match(TypeName type,
                      List<String> bugPatterns,
                      String description) {
            this(type, bugPatterns, ElementKind.CLASS, description, Optional.empty());
        }

        private static Match create(TypeInfo typeInfo) {
            // class level match
            TypeName typeName = typeInfo.typeName();
            Annotation annotation = typeInfo.annotation(EXCLUDE);
            String reason = annotation.stringValue("reason").orElse(""); // required property
            return new Match(typeName,
                             pattern(annotation, typeName, null, reason),
                             reason);
        }

        private static Match create(TypedElementInfo element) {
            // method level match
            TypeName typeName = element.enclosingType().orElseGet(element::typeName);
            String methodName = element.elementName();
            Annotation annotation = element.annotation(EXCLUDE);
            String reason = annotation.stringValue("reason").orElse(""); // required property
            return new Match(typeName,
                             pattern(annotation, typeName, methodName, reason),
                             element.kind(),
                             reason,
                             Optional.of(methodName));
        }

        private static List<String> pattern(Annotation annotation, TypeName typeName, String methodName, String reason) {
            List<String> patterns = annotation.stringValues("pattern").orElseGet(List::of);
            if (patterns.isEmpty()) {
                StringBuilder message = new StringBuilder()
                        .append("The annotation @")
                        .append(EXCLUDE.fqName())
                        .append(" on type ")
                        .append(typeName);
                if (methodName != null) {
                    message.append(", method \"").append(methodName).append("\"");
                }
                message.append(" with reason: \"").append(reason).append("\" does not declare any patterns.");

                throw new CodegenException(message.toString());
            }
            return patterns;
        }
    }
}
