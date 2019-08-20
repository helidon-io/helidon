/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

def rootDir = new File(request.getOutputDirectory() + "/" + request.getArtifactId())
def javaPackage = request.getProperties().get("package")
def javaPkgDir = new File(rootDir, "src/main/java/" + javaPackage.replace('.','/'))
def resourcesDir = new File(rootDir, "src/main/resources")

def optionalFiles = [
  mainClass: [ "${javaPkgDir}/Main.java" ],
  restResource: [ "${javaPkgDir}/__applicationName__.java", "${javaPkgDir}/__restResourceName__.java" ],
  loggingConfig: [ "${resourcesDir}/logging.properties" ],
  applicationYaml: [ "${resourcesDir}/application.yaml" ]
]

def processOptionalFiles(prop, fnames) {
  if (request.getProperties().get(prop).matches("n|no|false")) {
    fnames.each{ fname -> new File(fname).delete() }
  }
}

optionalFiles.each{ key, value -> processOptionalFiles(key, value) }