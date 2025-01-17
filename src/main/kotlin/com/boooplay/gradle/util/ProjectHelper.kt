package com.boooplay.gradle.util

import com.boooplay.model.provider.Language
import com.boooplay.model.provider.ProviderData
import com.boooplay.model.provider.ProviderManifest
import com.boooplay.model.provider.ProviderType
import com.boooplay.gradle.getFlixclusive
import org.gradle.api.Project

internal fun Project.createProviderManifest(): ProviderManifest {
    val extension = this.extensions.getFlixclusive()
    val (versionCode, versionName) = extension.getVersionDetails()

    require(extension.providerClassName != null) {
        "No provider class found, make sure your provider class is annotated with @FlixclusiveProvider"
    }

    return ProviderManifest(
        providerClassName = extension.providerClassName!!,
        name = name,
        versionName = versionName,
        versionCode = versionCode,
        updateUrl = extension.updateUrl.orNull,
        requiresResources = extension.requiresResources.get(),
    )
}

internal fun Project.createProviderData(): ProviderData {
    val extension = extensions.getFlixclusive()
    val (versionCode, versionName) = extension.getVersionDetails()

    return ProviderData(
        buildUrl = extension.buildUrl.orNull?.let { String.format(it, name) },
        status = extension.status.get(),
        versionName = versionName,
        versionCode = versionCode,
        name = extension.providerName.get(),
        adult = extension.adult.get(),
        authors = extension.authors.getOrElse(emptyList()),
        description = extension.description.orNull,
        repositoryUrl = extension.repositoryUrl.orNull,
        language = extension.language.getOrElse(Language(languageCode = "en")),
        iconUrl = extension.iconUrl.orNull,
        providerType = extension.providerType.getOrElse(ProviderType(type = "Unknown")),
        changelog = extension.changelog.orNull
    )
}