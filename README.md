<p align="center">
    <img src="./etc/images/helidon_cloud_sticker.png"
        height="250">
</p>

# Helidon: Java Libraries for Writing Microservices

Project Helidon is a set of Java Libraries for writing microservices.
Helidon supports two programming models:

* Helidon MicroProfile: MicroProfile 1.1 plus Health Check and Metrics
* Helidon Core: a smaller more functional style API

## Documentation

Helidon documentation and javadocs at <http://helidon.io>.

## Get Started

See Getting Started at <http://helidon.io>.

## Bugs and Feedback

Issues are currently tracked in JIRA at <https://github.com/oracle/helidon/issues>

## Communication

* Slack: [j4c-users] TBD
* Twitter: TBD
* http://helidon.io

## Build

You will need Java 9 and Maven 3.5 or newer.

**Full build**
```bash
$ cd helidon-main
$ mvn install
```

**Checkstyle**
```bash
# Cd to the component you want to check
$ mvn validate  -Pcheckstyle
```

**Copyright**

The copyright plugin does not fail the build so you will
need to look for "Wrong copyright" or "No copyright" messages.
```bash
# Cd to the component you want to check
$ mvn validate  -Pcopyright
```

**Spotbugs**
```bash
# Cd to the component you want to check
$ mvn verify  -Pspotbugs
```

## License

Copyright (c) 2017, 2018 Oracle and/or its affiliates. All rights reserved.

Licensed under the Apache License, Version 2.0 (the "License"); you may
not use this file except in compliance with the License. You may obtain
a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
License for the specific language governing permissions and limitations
under the License.
