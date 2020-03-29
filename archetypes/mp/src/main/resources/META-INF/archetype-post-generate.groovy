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
def testJavaPkgDir = new File(rootDir, "src/test/java/" + javaPackage.replace('.','/'))
def resourcesDir = new File(rootDir, "src/main/resources")
def testResourcesDir = new File(rootDir, "src/test/resources")

def optionalFiles = [
  mainClass: [ "${javaPkgDir}/Main.java.vm" ],
  restResource: [ "${javaPkgDir}/__applicationName__.java.vm", "${javaPkgDir}/__restResourceName__.java.vm" ],
  unitTest: [ "${testJavaPkgDir}/MainTest.java.vm", "${testResourcesDir}/microprofile-config.properties.vm" ],
  loggingConfig: [ "${resourcesDir}/logging.properties.vm" ],
  applicationYaml: [ "${resourcesDir}/application.yaml.vm" ]
]

// remove optional files that should not be included
optionalFiles.each{ prop, fnames ->
  if (request.getProperties().get(prop).matches("n|no|false")) {
    fnames.each{ fname ->
      request.getProperties().each { entry ->
        fname = fname.replaceAll("__${entry.key}__","${entry.value}")
      }
      new File(fname).delete()
    }
  }
}

// rename .vm files
rootDir.traverse(type: groovy.io.FileType.FILES, maxDepth: -1) { file ->
  if (file.path.endsWith(".vm")) {
    file.renameTo file.path.substring(0, file.path.length() - 3)
  }
}

// delete empty dirs
rootDir.traverse(type: groovy.io.FileType.DIRECTORIES, maxDepth: -1, postDir: { dir ->
  if (dir.list().length == 0) {
    dir.delete()
  }
})