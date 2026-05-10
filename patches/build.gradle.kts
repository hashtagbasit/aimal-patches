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
        freeCompilerArgs.add("-Xcontext-receivers")
    }
}

dependencies {
    implementation(libs.gson)
}

tasks {
    register<JavaExec>("generatePatchesList") {
        description = "Build patch with patch list"
        dependsOn(build)
        classpath = sourceSets["main"].runtimeClasspath
        mainClass.set("app.morphe.util.PatchListGeneratorKt")
    }

    publish {
        dependsOn("generatePatchesList")
    }
}
