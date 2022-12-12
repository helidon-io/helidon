#!/bin/bash -e
#
# Copyright (c) 2022 Oracle and/or its affiliates.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
eval $(minikube docker-env)
NAMESPACE=crac-helloworld

mvn package -DskipTests
docker build -t crac-helloworld . -f Dockerfile.crac
# First time ran, checkpoint is created, stop with Ctrl-C
#docker run --privileged -p 7001:7001 --name crac-helloworld crac-helloworld
# Second time starting from checkpoint, stop with Ctrl-C
#docker start -i crac-helloworld

kubectl delete namespace ${NAMESPACE}
kubectl create namespace ${NAMESPACE}
kubectl config set-context --current --namespace=${NAMESPACE}
kubectl apply -f . --namespace ${NAMESPACE}