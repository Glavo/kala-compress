Kala Compress
---

[![Gradle Check](https://github.com/Glavo/compress/actions/workflows/check.yml/badge.svg)](https://github.com/Glavo/compress/actions/workflows/check.yml)
[![](https://img.shields.io/maven-central/v/org.glavo.kala/kala-compress?label=Maven%20Central)](https://search.maven.org/artifact/org.glavo.kala/kala-compress)

This project is based on [Apache Commons Compress](https://github.com/apache/commons-compress).
Kala Compress has made some improvements on its basis: Modularization (JPMS Support), NIO2 Path API support, etc.

Another important improvement of Kala Compress is that it does not depend on libraries such as commons-io and commons-lang3.
Its core jar has no dependencies and is less than 90KiB in size, making it suitable for programs that have requirements on program size.

Its API is mostly consistent with Apache Commons Compress, with a few incompatibilities.
So I renamed the package (and the module name) from `org.apache.commons.compress` to `kala.compress`.
Therefore, it can coexist with Apache Commons Compress without conflict.

We assume that you already know about [Commons Compress](https://github.com/apache/commons-compress). 
If not, please refer to the [User Guide](https://commons.apache.org/proper/commons-compress/examples.html) first.

To add Kala Compress as a dependency, see section [Modules](#Modules).

## Different from Apache Commons Compress

### Modularization (JPMS Support)

Kala Compress has been fully modularized and now fully supports the the JPMS (Java Platform Module System).

Each compressor and archive is split into a separate artifact with a separate module name,
you can optionally add dependencies on some of them without importing the entire Kala Compress.
(The size of Kala Compress core jar is less than 90KB)

`ArchiveStreamFactory` and `CompressorStreamFactory` have been refactored internally 
so that they no longer have hard dependencies on all compressors and archivers,
but instead look them up dynamically at runtime.

Each module provides its `module-info.class`, so it can work well with `jlink`.

For more information about the Kala Compress modules, see [Modules](#Modules).

### Charset

Kala Compress has been completely refactored internally to use `java.nio.charset.Charset` to represent encoding.
All methods that accept an encoding represented `String` then accept `Charset`.
If you are using String to represent the encoding, use `kala.compress.utils.Charsets.toCharset(String)` to convert it to `Charset`.

`ZipEncoding` has been removed, please switch to `Charset`.

`CharsetNames` has been removed, please switch to `StandardCharsets`.

Kala Compress no longer uses `Charset.defaultCharset()`, but uses UTF-8 as an alternative.
Note that `file.encoding` defaults to UTF-8 since Java 18. When you want to use platform native encoding,
use the `kala.compress.utils.Charsets.nativeCharset()` explicitly as the alternative.

In addition, APIs that accept encoding represented by `String` now no longer fall back to the default character set when the encoding is not supported or invalid.
Now they throw exceptions just like `Charset.forName`. (The behavior when `null` is passed in is not affected, it will still fall back to the UTF-8)

### NIO2 Support

Most of the `java.io.File`-based APIs in commons-compress have been removed, please use the `java.nio.file.Path`-based APIs.

### Rename

`ZipFile` has been renamed to `ZipArchiveReader`.

`TarFile` has been renamed to `TarArchiveReader`.

`SevenZFile` and `SevenZOutputFile` has been renamed to `SevenZArchiveReader` and `SevenZArchiveWriter`.

The reason for this is that I want to reserve names like `[Archive]File` for a more full-featured support class in the future.
It should be able to support both reading and writing archives, adding or deleting entries, etc.

### Deprecation and removal

Most deprecated APIs in Apache Commons Compress have been removed. 

Unlike commons-compress, the constructors of `ZipArchiveReader`/`SevenZArchiveReader`  are not deprecated,
so there is no need to use lengthy builder syntax for simple requirements.

Additional support for OSGI is no longer provided, but this shouldn't make a big difference.

`ZipEncoding` and `CharsetNames` has been removed, please switch to `Charset` and `StandardCharsets`.

All methods that accept encoding represented by `String` have been removed, please use the `Charset` instead.

All methods that accept `java.util.Date` have been removed, please use the `java.nio.file.attribute.FileTime` instead.

Since Security Manager will be removed from JDK in the future, Kala Compress no longer use it.
For more details, see [JEP 411: Deprecate the Security Manager for Removal](https://openjdk.java.net/jeps/411).

Since `finalize` method will be removed from JDK in the future, Kala Compress no longer used to clean up resources.
For more details, see [JEP 421: Deprecate Finalization for Removal](https://openjdk.java.net/jeps/421).
The `archiveName` in the `ZipFile` constructor is only used for error reporting in `finalize`, so it is removed together.

Most methods that accept the `File` have been removed, please use the `Path` instead.

## Modules

**Note: Kala Compress is in beta phase. Although it is developed based on mature Apache Commons Compress and has passed all tests, it may still be unstable. I may need to make some adjustments to the API before releasing to production.**

The latest Kala Compress version is `1.21.0.1-beta3`.

You can add dependencies on Kala Compress modules as follows:

Maven:
```xml
<dependency>
  <groupId>org.glavo.kala</groupId>
  <artifactId>${kala-compress-module-name}</artifactId>
  <version>1.21.0.1-beta3</version>
</dependency>
```

Gradle:
```kotlin
dependencies {
  implementation("org.glavo.kala:${kala-compress-module-name}:1.21.0.1-beta3")
}
```

All Kala Compress modules are listed below.

### [`kala.compress`](https://search.maven.org/artifact/org.glavo.kala/kala-compress)

This is an empty module, which declares the transitivity dependency on all modules of Kala Compress.
You can use all the contents of Kala Compress only by adding dependencies on it.

### [`kala.compress.base`](https://search.maven.org/artifact/org.glavo.kala/kala-compress-base) 

It is the basic module of Kala Compress, and all other modules depend on it.

It contains the following packages:

* (package) `kala.compress`
* (package) `kala.compress.archivers`
* (package) `kala.compress.compressors`
* (package) `kala.compress.compressors.lz77support`
* (package) `kala.compress.compressors.lzw`
* (package) `kala.compress.compressors.parallel`
* (package) `kala.compress.compressors.utils`

### [`kala.compress.compressors`](https://search.maven.org/artifact/org.glavo.kala/kala-compress-compressors)

It is an empty module that contains transitive dependencies on all compressor modules.
You can include all compressors by adding a dependency on it.

In addition, each compressor in Kala Compress has a separate module, and you can add dependencies on one or all of them separately.
Here is a list of compressors:

* (module) [`kala.compress.compressors.brotli`](https://search.maven.org/artifact/org.glavo.kala/kala-compress-compressors-brotli)
* (module) [`kala.compress.compressors.bzip2`](https://search.maven.org/artifact/org.glavo.kala/kala-compress-compressors-bzip2)
* (module) [`kala.compress.compressors.deflate`](https://search.maven.org/artifact/org.glavo.kala/kala-compress-compressors-deflate)
* (module) [`kala.compress.compressors.deflate64`](https://search.maven.org/artifact/org.glavo.kala/kala-compress-compressors-deflate64)
* (module) [`kala.compress.compressors.gzip`](https://search.maven.org/artifact/org.glavo.kala/kala-compress-compressors-gzip)
* (module) [`kala.compress.compressors.lz4`](https://search.maven.org/artifact/org.glavo.kala/kala-compress-compressors-lz4)
* (module) [`kala.compress.compressors.lzma`](https://search.maven.org/artifact/org.glavo.kala/kala-compress-compressors-lzma)
* (module) [`kala.compress.compressors.pack200`](https://search.maven.org/artifact/org.glavo.kala/kala-compress-compressors-pack200)
* (module) [`kala.compress.compressors.snappy`](https://search.maven.org/artifact/org.glavo.kala/kala-compress-compressors-snappy)
* (module) [`kala.compress.compressors.xz`](https://search.maven.org/artifact/org.glavo.kala/kala-compress-compressors-xz)
* (module) [`kala.compress.compressors.z`](https://search.maven.org/artifact/org.glavo.kala/kala-compress-compressors-z)
* (module) [`kala.compress.compressors.zstandard`](https://search.maven.org/artifact/org.glavo.kala/kala-compress-compressors-zstandard)

Here are some notes:

* Different from Apache Commons Compress, the `brotli` compressor has no external dependencies.
  It copies the [Google Brotli](https://github.com/google/brotli) code into package `kala.compress.compressors.brotli.dec`,
  The reason for this is that Google Brotli does not support JPMS.
* The `lzma` compressor and the `xz` compressor needs [XZ for Java](https://tukaani.org/xz/java.html) to work.
* The `zstandard` compressor needs [Zstd JNI](https://github.com/luben/zstd-jni) to work.

### [`kala.compress.archivers`](https://search.maven.org/artifact/org.glavo.kala/kala-compress-archivers)

It is an empty module that contains transitive dependencies on all archiver modules.
You can include all archivers by adding a dependency on it.

In addition, each archiver in Kala Compress has a separate module, and you can add dependencies on one or all of them separately.
Here is a list of archivers:

* (module) [`kala.compress.archivers.ar`](https://search.maven.org/artifact/org.glavo.kala/kala-compress-archivers-ar)
* (module) [`kala.compress.archivers.arj`](https://search.maven.org/artifact/org.glavo.kala/kala-compress-archivers-arj)
* (module) [`kala.compress.archivers.cpio`](https://search.maven.org/artifact/org.glavo.kala/kala-compress-archivers-cpio)
* (module) [`kala.compress.archivers.dump`](https://search.maven.org/artifact/org.glavo.kala/kala-compress-archivers-dump)
* (module) [`kala.compress.archivers.sevenz`](https://search.maven.org/artifact/org.glavo.kala/kala-compress-archivers-sevenz)
* (module) [`kala.compress.archivers.tar`](https://search.maven.org/artifact/org.glavo.kala/kala-compress-archivers-tar)
* (module) [`kala.compress.archivers.zip`](https://search.maven.org/artifact/org.glavo.kala/kala-compress-archivers-zip)

Here are some notes:

* The `sevenz` archiver needs [XZ for Java](https://tukaani.org/xz/java.html) to work.
* The `sevenz` archiver and the `zip` archiver have optional dependencies on the `bzip2` compressor and the `deflate64` compressor.
  They can work without these compressors, but errors will occur when they are required.
* Support for `jar` (in package `kala.compress.archivers.jar`) is in the module `kala.compress.archivers.zip`.

### [`kala.compress.changes`](https://search.maven.org/artifact/org.glavo.kala/kala-compress-changes)

It contains the package `kala.compress.changes`.

### [`kala.compress.examples`](https://search.maven.org/artifact/org.glavo.kala/kala-compress-examples)

It contains the package `kala.compress.archivers.examples`.

## Bug Report

If you encounter problems using it, please [open an issue](https://github.com/Glavo/kala-compress/issues/new).

If it's an issue upstream of Apache Commons Compress, it's best to give feedback [here](https://commons.apache.org/proper/commons-compress/issue-tracking.html) and I'll port the upstream fix here.
