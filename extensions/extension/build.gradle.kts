extension {
    name = "extensions/extension.mpe"
}

android {
    namespace = "app.aimal.extension"
}

dependencies {
    compileOnly("androidx.media3:media3-common:1.8.0")
    compileOnly("androidx.media3:media3-database:1.8.0")
    compileOnly("androidx.media3:media3-datasource:1.8.0")
    compileOnly("androidx.media3:media3-exoplayer:1.8.0")
    compileOnly("androidx.media3:media3-exoplayer-hls:1.8.0")
    compileOnly("androidx.media3:media3-exoplayer-dash:1.8.0")
    compileOnly("androidx.fragment:fragment:1.8.5")
    compileOnly("androidx.recyclerview:recyclerview:1.3.2")
    compileOnly("androidx.annotation:annotation:1.9.1")
    compileOnly("com.github.bumptech.glide:glide:4.16.0")
}
