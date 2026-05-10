group = "app.aimal"

patches {
    about {
        name = "Aimal Patches"
        description = "Custom Morphe patches for Crunchyroll - speed control"
        source = "https://github.com/hashtagbasit/aimal-patches"
        author = "Aimal"
        contact = ""
        website = ""
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
