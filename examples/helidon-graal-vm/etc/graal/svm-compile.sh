#!/usr/bin/env bash

# run this script from project directory
source ./etc/graal/env.sh

GRAAL_OPTIONS="-H:ReflectionConfigurationResources=./etc/graal/reflection-config.json"

INCLUDE_RES="application.yaml"
INCLUDE_RES="${INCLUDE_RES}|META-INF/services/.*"
GRAAL_OPTIONS="${GRAAL_OPTIONS} -H:IncludeResources=${INCLUDE_RES}"

DELAY_INIT="io.netty.handler.codec.http.HttpObjectEncoder"
DELAY_INIT="${DELAY_INIT},io.netty.handler.ssl.ReferenceCountedOpenSslEngine"
#DELAY_INIT="${DELAY_INIT},io.netty.internal.tcnative.SSL"
GRAAL_OPTIONS="${GRAAL_OPTIONS} --delay-class-initialization-to-runtime=${DELAY_INIT}"

GRAAL_OPTIONS="${GRAAL_OPTIONS} -Djava.runtime.name=GraalVM"
GRAAL_OPTIONS="${GRAAL_OPTIONS} --report-unsupported-elements-at-runtime"

echo "Graal options: ${GRAAL_OPTIONS}"

native-image -jar target/helidon-graal-vm-full.jar ${GRAAL_OPTIONS}

# then run
# ./helidon-graal-vm-full