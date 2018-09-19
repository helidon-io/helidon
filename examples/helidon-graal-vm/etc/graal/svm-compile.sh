#!/usr/bin/env bash

# run this script from project directory
source ./etc/graal/env.sh

# Configuration of reflection, needed for custom classes that should be instantiated or access by reflection
GRAAL_OPTIONS="-H:ReflectionConfigurationResources=./etc/graal/reflection-config.json"

# Configure all resources that should be available in runtime (except for META-INF/services - those are added
# by Helidon SVM Extension)
INCLUDE_RES="application.yaml"
INCLUDE_RES="${INCLUDE_RES}|logging.properties"
GRAAL_OPTIONS="${GRAAL_OPTIONS} -H:IncludeResources=${INCLUDE_RES}"

# This should be "set in stone" - this is to prevent compilation errors due to incomplete classpath for optional features of
# Netty.
DELAY_INIT="io.netty.handler.codec.http.HttpObjectEncoder"
DELAY_INIT="${DELAY_INIT},io.netty.handler.ssl.ReferenceCountedOpenSslEngine"
GRAAL_OPTIONS="${GRAAL_OPTIONS} --delay-class-initialization-to-runtime=${DELAY_INIT}"

# And this is to prevent compilation errors that are caused by some specific Netty classes (io/netty/internal/tcnative/SSL)
GRAAL_OPTIONS="${GRAAL_OPTIONS} --report-unsupported-elements-at-runtime"

echo "Graal options: ${GRAAL_OPTIONS}"

native-image -jar target/helidon-graal-vm-full.jar ${GRAAL_OPTIONS}

# then run
# ./helidon-graal-vm-full