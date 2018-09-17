#!/usr/bin/env bash

# run this script from project directory
source ./etc/graal/env.sh

mvn clean install

native-image -jar target/helidon-graal-vm-full.jar -H:ReflectionConfigurationResources=./etc/graal/reflection-config.json -H:IncludeResources=application.yaml --delay-class-initialization-to-runtime=io.netty.handler.codec.http.HttpObjectEncoder,io.netty.handler.ssl.ReferenceCountedOpenSslEngine,io.netty.internal.tcnative.SSL --report-unsupported-elements-at-runtime

# then run
# ./helidon-graal-vm-full