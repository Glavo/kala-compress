tasks.withType<Jar> {
    metaInf {
        from(file("GLOB-LICENSE")) // https://github.com/hrakaroo/glob-library-java/blob/master/LICENSE.txt
    }
}