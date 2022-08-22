#!/bin/bash

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

plaintext () {
  name=$1
  threads=$2
  connections=$3
  time=$4
  pipeline=$5
  echo "Running plaintext benchmark $name $threads $connections $time $pipeline"
  wrk -d ${time} \
      -c ${connections} \
      -t ${threads} \
      --latency \
      -H 'Accept: text/plain,text/html;q=0.9,application/xhtml+xml;q=0.9,application/xml;q=0.8,*/*;q=0.7' \
      -H 'Connection: keep-alive' \
      http://localhost:8080/plaintext \
      -s pipeline.lua \
      -- ${pipeline} > results/$name/plaintext-${threads}-${connections}-${pipeline}.out
}

json () {
  name=$1
  threads=$2
  connections=$3
  time=$4
  echo "Running json benchmark $name $threads $connections $time"
  wrk -d ${time} \
      -c ${connections} \
      -t ${threads} \
      --latency \
      -H 'Host: tfb-server' \
      -H 'Accept: application/json,text/html;q=0.9,application/xhtml+xml;q=0.9,application/xml;q=0.8,*/*;q=0.7' \
      -H 'Connection: keep-alive' \
      "http://tfb-server:8080/json" > results/$name/json-${threads}-${connections}.out
}

name=${1:default}

long_test=300
short_test=120

mkdir -p results/$name

echo Running warm-up
plaintext ${name} 8 1024 $long_test 16

echo Wait 20 seconds
sleep 20

plaintext ${name} 8 1024 $long_test 16

echo Wait 20 seconds
sleep 20

plaintext ${name} 8 128 $short_test 16

echo Wait 20 seconds
sleep 20

plaintext ${name} 8 512 $short_test 16

echo Wait 20 seconds
sleep 20

plaintext ${name} 8 128 $short_test 1

echo Wait 20 seconds
sleep 20

echo Running JSON warm-up
json ${name} 4 128 $short_test

echo Wait 20 seconds
sleep 20

json ${name} 4 128 $long_test

