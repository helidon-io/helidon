FROM helidon/java-native:0.1.13 as build-native-image

WORKDIR /helidon

ADD *.jar target/
ADD ${libs.classpath.prefix}/*.jar target/

RUN echo "$GRAALVM_HOME/bin/native-image -H:Name=helidon-native -H:IncludeResources='${native.resources}' -H:+ReportExceptionStackTraces -classpath target/* ${main.class}"
RUN $GRAALVM_HOME/bin/native-image \
        --static \
        -H:Name=helidon-native \
        -H:IncludeResources="${native.resources}" \
        --no-server \
        -H:+ReportExceptionStackTraces \
        -classpath "target/*" \
        ${main.class}

# Now we should have a native image built, let us create the image
FROM scratch
WORKDIR /helidon
COPY --from=build-native-image /helidon/helidon-native helidon-native

ENTRYPOINT ["./helidon-native"]

EXPOSE ${main.port}
