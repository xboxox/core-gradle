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
import org.gradle.api.attributes.Attribute

internal fun registerConfigurations(project: Project) {
    with(project) {
        val fatImplementationProvider = FatImplementationProvider()

        configurations.register(fatImplementationProvider.name) {
            /* Temporary workaround:
            * https://github.com/JetBrains/compose-multiplatform/issues/1404#issuecomment-1146894731
            * */
            attributes {
                attribute(Attribute.of("ui", String::class.java), "awt")
            }

            isCanBeResolved = true
        }

        afterEvaluate {
            val fatImplementation = configurations.getByName(fatImplementationProvider.name)

            fatImplementation.dependencies.forEach { dependency ->
                fatImplementationProvider.provide(project, dependency)
            }
        }
    }
}