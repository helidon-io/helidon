# Helidon's Application Pom

PR [1022](https://github.com/oracle/helidon/pull/1022) introduced Helidon application parent
poms. These are poms to be used by Helidon applications. This PR also re-structured the Helidon project poms. This document gives an 
overview of those changes.

## The Problem

One of our core project principles is transparency. We prefer transparency
over magic black boxes. Because of this, the pom files used in our quickstart sample
applications (and maven archetypes) used self contained pom files. No parent. Everything
was there for all to see.

We liked this, and customers that were well versed in Maven liked this. But beginners,
and those people less knowledgeable of the intricacies of Maven, often tripped over
details in the pom file when they had to inevitably modify it. All the details of
plugin configuration and dependency management tended to obscure the most important
part of the pom -- the list of dependencies. 

This led to mistakes, like adding a dependency to the
dependency management section (not the dependencies section). And once a mistake was
made in the pom, errors tended to compound leading to frustration.

## The Solution

To address this we decided to bite the bullet and introduce Helidon application poms
for SE and MP. This is a trade-off. It helps newbies by simplifying the pom they
deal with and reducing the chance of making a mistake. But it obscures
the details of what's going on and makes things more complicated for customers
 that want to use their own parent poms.

We decided this was a reasonable tradeoff because it's important for
the initial experience with Helidon to be frictionless and pleasant. Also 
customers that want their own parent pom are usually knowledgeable about Maven
and can navigate our new pom structure.

One other benefit: this simplifies our examples. Now they all inherit an
application pom parent. And we can manage plugins and versions in one place and
ensure consistency across all examples.

## The Design

The poms and their relationship are:

```
io.helidon:helidon-parent (parent/pom.xml)
└── io.helidon:helidon-bom (bom/pom.xml)
    └── io.helidon:helidon-dependencies (dependencies/pom.xml)
        └── io.helidon.applications:helidon-applications-project (applications/pom.xml)
            ├── io.helidon.applications:helidon-se (applications/mp/pom.xml)
            └── io.helidon.applications:helidon-mp (applications/se/pom.xml)

```

And what they do (starting from the bottom):

* **helidon-mp**: the parent pom used by Helidon MP applications. 
  It contains plugin configuration specific to Helidon MP applications, like
  jandex. It inherits from *helidon-applications-project*.
* **helidon-se**: the parent pom used by Helidon SE applications.
  It contains plugin configuration specific to Helidon SE applications, like
  native image supportin the helidon-maven-plugin.
  It also inherits from *helidon-applications-project*.
* **helidon-applications-project**: contains
  all the boiler plate for Helidon applications. For example, this configures
  the maven-dependency-plugin for copying runtime dependencies into the target/lib
  directory, and the maven-jar-plugin to create the application jar and
  set the classPath and mainClass attributes in the jar manifest.
  It inherits from *helidon-dependencies*
* **helidon-dependencies**: dependency management for all third party
  dependencies used by both Helidon applications and the Helidon project
  itself. This helps ensure the same versions are used by Helidon applications
  and the Helidon project.
  Inherits from *helidon-bom*.
* **helidon-bom**: dependency management for Helidon jars. This is our 
  project bom. Inherits from *helidon-parent*
* **helidon-parent**: basic Maven stuff like plugin management for basic
  plugins, repository configuration, etc.
  
## Use by Helidon Project
  
All Helidon examples and stand-alone test apps use either *helidon-se* or *helidon-mp*
as their parent. This ensure consistency across all our Helidon applications.

The Helidon archetypes generate projects that use either *helidon-se* or *helidon-mp*
as their parent. So customer applications will typically use one of these
as their parent pom.

The helidon-project pom (top level pom.xml), uses *helidon-dependenices* as 
its parent. This ensures common dependency and plugin management across
the Helidon project and applications.

## What about Stand Alone Poms?

Customers that want to build Helidon applications as part of their Maven project
might not want to use our parent pom. The assumption is that these customers
will be knowledgeable about Maven. To help these customers we have included
a couple stand-alone examples.  You can find those under 
`examples/quickstarts/helidon-standalone-quickstart-*`
  
