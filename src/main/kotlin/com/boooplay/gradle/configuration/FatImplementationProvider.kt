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

package com.boooplay.gradle.configuration

import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency

internal const val FAT_IMPLEMENTATION = "fatImplementation"

internal class FatImplementationProvider : IConfigurationProvider {
    override val name: String
        get() = FAT_IMPLEMENTATION

    override fun provide(project: Project, dependency: Dependency) {
        project.dependencies.add("compileOnly", dependency)
    }
}