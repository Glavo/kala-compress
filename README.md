[![Gradle Check](https://github.com/Glavo/compress/actions/workflows/check.yml/badge.svg)](https://github.com/Glavo/compress/actions/workflows/check.yml)

Glavo Compress
---

This project is based on [Apache Commons Compress](https://github.com/apache/commons-compress).


In order to make it more modern, especially to better support NIO2 API and JPMS (Java Platform Module System),
I fork it out for maintenance. 

Most APIs in this project are compatible with Apache Commons Compress, but I gave up full compatibility for better modernization.
Therefore, I will modify its package name to ensure that it can coexist with Apache Commons Compress.

After I finish modularizing it, I will publish it to Maven Central. Please look forward to it.

Task list:

- [x] Deprecate `ZipEncoding` and `CharsetNames`, replace them with `Charset`, `StandardCharsets` and `Charsets`;
- [x] Full support for Java `Charset`, allows users to specify encoding without using `String` at all;
- [x] Use UTF-8 by default;
- [x] Clean up all deprecated features;
- [x] In preparation for [Valhalla](https://openjdk.java.net/projects/valhalla/), replace the constructor of a class that can become a [value class](https://openjdk.java.net/jeps/8277163) with a factory method;
- [x] Enhanced NIO2 Path API support, migration from `File` to `Path`;
- [ ] Provide `FileSystem` for each archiver;
- [ ] Split compressors and archivers into separate modules;
- [ ] Full support for JPMS;
- [ ] Rename the package;
- [ ] Publish it to Maven Central