# Site and documentation guidelines

## Naming

Each component is always referred to with the prefix `Helidon`:
- Helidon Webserver
- Helidon Config
- Helidon Security

This naming convention should be used in both Javadocs and site documentation.

## Sections

Each Component shall have a common set of "standard" sections:
- Introduction
- Bill Of Materials
- Basics
- Summary
- Examples

The introduction section should be used to answer the following questions:
- What is this component about ?
- Why you need this component for?
- When to use this component ?

The bill of materials section should list of Maven dependencies available for a
 component and describe how the user can compose with the dependencies. Each 
 dependency should have a link to corresponding documentation that explains how
 to use the feature.

The basics section should walk through a minimalist example and define the
terminology for the component.

The summary should conclude the documentation for a component. Giving further
 pointers or points to the reader. This can also serve as a placeholder for
 a cheat sheet or table of useful links.

The examples section should list all the example available for the component with
 links to Github or Gitlab and a brief description of what the example
 demonstrates.

## Preamble

Use the Preamble as a short optional introduction to a document. It must be
 kept as short as possible.

## Requirements

The requirements that are global to Helidon shall not be repeated in component
 specific pages unless that requirement is specific to a component. E.g. 
 Requirements such as Java version or Maven version should be stated once at the
 very beginning of the documentation.

## Maven coordinates

If possible, avoid having a page/document describe more than one Maven coordinate.
 Obviously the documentation does not need to dictate the code modularity but this
 has an impact on the end user.

A document/page describing features related to a Maven artifact should provide
 a pom.xml snippet in the first section of the document. A user should be able
 to click the copy button and paste this straight into his quickstart project.

## Navigation Layout

The documentation is organized into groups and subgroups of pages. Each page is a
 separate document with a set of sections. Only one level of subgroups is supported.
Groups subgroups pages and are visible in the main navigation.

We need to keep a balance between the size of pages and the number of top level
 items per group to provide a good navigation experience.

This means break down a page when it is too long, or the content shall have
 a top level navigation item.

Use sub-groups to organize top level items accordingly.

## Focus on end-user content

A typical end-user will go through the documentation very quickly. It is important
 that the documentation does not elaborate on the design too much, but rather 
 elaborate on the features and how to use them. The documentation is not supposed
 to supplement the Javadoc, it is rather like a small cookbook.

## Titles

Titles should have the first letter of each word capitalized. The length of a
 title should be as small as possible, remember a title has a corresponding anchor
 and there will generate a specific URL.

## Formatting

Use 80 characters limit when possible: do not break line on inline formatting,
 links or any block where formatting matters (e.g. code snippets).

When breaking line, put the space on the new line, not on the threadContext line.

## Bibliography

Maintain a bibliography of all links at the end of each documentation. This will
 serve as a reference and also as alias to reduce the length required to insert
 long links.