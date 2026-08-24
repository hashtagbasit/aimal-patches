group = "app.aimal"

patches {
    about {
        name = "Aimal Patches"
        description = "Playback speed and aspect ratio controls for Crunchyroll, HBO Max and Disney+"
        source = "https://github.com/hashtagbasit/aimal-patches"
        author = "hashtagbasit"
        contact = "https://github.com/hashtagbasit/aimal-patches/issues"
        website = "https://github.com/hashtagbasit/aimal-patches"
        license = "GPLv3"
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xcontext-parameters")
    }
}

dependencies {
    implementation(libs.gson)
}
