/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.boooplay.gradle.task

import com.android.build.gradle.BaseExtension
import com.android.build.gradle.tasks.ProcessLibraryManifest
import com.boooplay.gradle.FLX_PROVIDER_EXTENSION_NAME
import com.boooplay.gradle.FlixclusiveProviderExtension
import com.boooplay.gradle.configuration.FAT_IMPLEMENTATION
import com.boooplay.gradle.getFlixclusive
import com.boooplay.gradle.util.createProviderManifest
import com.boooplay.gradle.util.isValidFilename
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import groovy.json.JsonBuilder
import groovy.json.JsonGenerator
import org.gradle.api.Project
import org.gradle.api.tasks.AbstractCopyTask
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.bundling.Zip
import org.gradle.api.tasks.compile.AbstractCompile
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

private const val TASK_GROUP = "flixclusive"

internal fun registerTasks(project: Project) {
    val extension = project.extensions.getFlixclusive()
    val intermediates = project.buildDir.resolve("intermediates")
    val compiledDependenciesDir = intermediates.resolve("compiled_dependencies/")

    if (project.rootProject.tasks.findByName("generateUpdaterJson") == null) {
        project.rootProject.tasks.register("generateUpdaterJson", GenerateUpdaterJsonTask::class.java) {
            group = TASK_GROUP

            outputs.upToDateWhen { false }

            outputFile.set(this.project.buildDir.resolve("updater.json"))
        }
    }

    val providerClassFile = intermediates.resolve("providerClass")

    val compileRequiredDependencies = project.tasks.register("compileRequiredDependencies", ShadowJar::class.java) {
        doFirst {
            logger.lifecycle("Compiling required dependencies...")
        }

        group = TASK_GROUP

        exclude("*.aar")
        exclude("*.bin")
        exclude("META-INF/**")
        dependencies {
            exclude(dependency("com.github.flixclusive.*:provider:.*"))
            exclude("META-INF/**")
        }

        configurations = listOf(
            project.configurations.getByName(FAT_IMPLEMENTATION)
        )

        archiveBaseName.set("dependencies")
        archiveClassifier.set("")
        archiveVersion.set("")
    }

    val extractRequiredDependencies = project.tasks.register("extractRequiredDependencies", Copy::class.java) {
        group = TASK_GROUP

        val compileRequiredDependenciesTask = compileRequiredDependencies.get()
        dependsOn(compileRequiredDependenciesTask)

        if (!compiledDependenciesDir.exists()) {
            compiledDependenciesDir.mkdirs()
        }

        val jarFile = compileRequiredDependenciesTask.outputs.files.singleFile
        from(project.zipTree(jarFile))
        into(compiledDependenciesDir)
    }

    val compileDex = project.tasks.register("compileDex", CompileDexTask::class.java) {
        group = TASK_GROUP

        this@register.providerClassFile.set(providerClassFile)

        // Doing this since KotlinCompile does not inherit AbstractCompile no more.
        val compileKotlinTask = project.tasks.findByName("compileDebugKotlin") as KotlinCompile?
        if (compileKotlinTask != null) {
            dependsOn(compileKotlinTask)
            input.from(compileKotlinTask.destinationDirectory)
        }

        val compileJavaWithJavac = project.tasks.findByName("compileDebugJavaWithJavac") as AbstractCompile?
        if (compileJavaWithJavac != null) {
            dependsOn(compileJavaWithJavac)
            input.from(compileJavaWithJavac.destinationDirectory)
        }

        val extractRequiredDependenciesTask = extractRequiredDependencies.get()
        dependsOn(extractRequiredDependenciesTask)
        input.from(compiledDependenciesDir)

        outputFile.set(intermediates.resolve("classes.dex"))

        doLast {
            compiledDependenciesDir.deleteRecursively()
        }
    }

    val compileResources = project.tasks.register("compileResources", CompileResourcesTask::class.java) {
        group = TASK_GROUP

        val processManifestTask = project.tasks.getByName("processDebugManifest") as ProcessLibraryManifest
        dependsOn(processManifestTask)

        val android = project.extensions.getByName("android") as BaseExtension
        input.set(android.sourceSets.getByName("main").res.srcDirs.single())
        manifestFile.set(processManifestTask.manifestOutputFile)

        outputFile.set(intermediates.resolve("res.apk"))

        doLast {
            val resApkFile = outputFile.asFile.get()

            if (resApkFile.exists()) {
                project.tasks.named("make", AbstractCopyTask::class.java) {
                    from(project.zipTree(resApkFile)) {
                        exclude("AndroidManifest.xml")
                    }
                }
            }
        }
    }

    project.afterEvaluate {
        val make = project.tasks.register("make", Zip::class.java) {
            group = TASK_GROUP

            val manifestFile = intermediates.resolve("manifest.json")
            from(manifestFile)
            doFirst {
                val (versionCode, _) = extension.getVersionDetails()
                require(versionCode > 0L) {
                    "No version is set"
                }

                if (extension.providerClassName == null) {
                    if (providerClassFile.exists()) {
                        extension.providerClassName = providerClassFile.readText()
                    }
                }

                require(extension.providerClassName != null) {
                    "No provider class found, make sure your provider class is annotated with @FlixclusiveProvider"
                }

                manifestFile.writeText(
                    JsonBuilder(
                        project.createProviderManifest(),
                        JsonGenerator.Options()
                            .excludeNulls()
                            .build()
                    ).toPrettyString()
                )
            }

            val compileDexTask = compileDex.get()
            dependsOn(compileDexTask)
            from(compileDexTask.outputFile)

            if (extension.requiresResources.get()) {
                dependsOn(compileResources.get())
            }

            val flxProvider = project.extensions.getByName(FLX_PROVIDER_EXTENSION_NAME) as FlixclusiveProviderExtension
            val projectName = flxProvider.providerName.get()

            if (!isValidFilename(projectName)) {
                throw IllegalStateException("Invalid project name: $projectName")
            }

            isPreserveFileTimestamps = false
            archiveBaseName.set(projectName)
            archiveExtension.set("flx")
            archiveVersion.set("")
            destinationDirectory.set(project.buildDir)

            doLast {
                logger.lifecycle("Provider package ${projectName}.flx created at ${outputs.files.singleFile}")
            }
        }

        project.rootProject.tasks.getByName("generateUpdaterJson").dependsOn(make)
        project.tasks.register("deployWithAdb", DeployWithAdbTask::class.java) {
            group = TASK_GROUP
            dependsOn("make")
            dependsOn(":generateUpdaterJson")
        }
    }
}