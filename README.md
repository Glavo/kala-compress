Glavo Compress
---

This is a fork of [Apache Commons Compress](https://github.com/apache/commons-compress),
In order to adapt jpms, I fork it out for maintenance, And I'll remove some legacy code that is reserved for compatibility.
After I finish modularizing it, I will publish it to Maven Central. Please look forward to it.

Task list:

- [x] Deprecate `ZipEncoding`, replace it with Charset
- [x] Better support for Java `Charset`
- [x] Use UTF-8 by default
- [x] Clean up all deprecated features
- [x] Deprecate OSGi support
- [ ] Enhanced NIO2 Path API support
- [ ] Split compressors and archivers into separate modules
- [ ] Full support for JPMS