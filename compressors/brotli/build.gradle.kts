tasks.withType<Jar> {
    metaInf {
        from(file("brotli-LICENSE"))
    }
}