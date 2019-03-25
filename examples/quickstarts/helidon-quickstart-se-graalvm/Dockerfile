FROM helidon/java-native:0.1.13 as build-native-image
ARG NATIVE_RESOURCES
ARG NATIVE_ENTRY_POINT
ARG PORT

WORKDIR /helidon

# Expects maven package to run before this step
ADD target/*.jar target/
ADD target/dependency/*.jar target/
RUN echo "$GRAALVM_HOME/bin/native-image -H:Name=helidon-native -H:IncludeResources=$NATIVE_RESOURCES -H:+ReportExceptionStackTraces -classpath target/* $NATIVE_ENTRY_POINT"
RUN $GRAALVM_HOME/bin/native-image \
        --static \
        -H:Name=helidon-native \
        -H:IncludeResources=$NATIVE_RESOURCES \
        --no-server \
        -H:+ReportExceptionStackTraces \
        -classpath "target/*" \
        $NATIVE_ENTRY_POINT

# Now we should have a native image built, let us create the image
FROM scratch
WORKDIR /helidon
COPY --from=build-native-image /helidon/helidon-native helidon-native

ENTRYPOINT ["./helidon-native"]

EXPOSE $PORT

