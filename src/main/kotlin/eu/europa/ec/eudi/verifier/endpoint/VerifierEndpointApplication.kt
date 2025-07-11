/*
 * Copyright (c) 2023 European Commission
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package eu.europa.ec.eudi.verifier.endpoint

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.support.BeanDefinitionDsl
import org.springframework.context.support.GenericApplicationContext
import java.time.Clock

@SpringBootApplication
@EnableConfigurationProperties(TypeMetadataResolutionProperties::class)
class VerifierApplication

internal fun BeanDefinitionDsl.initializer(): ApplicationContextInitializer<GenericApplicationContext> =
    ApplicationContextInitializer<GenericApplicationContext> { initialize(it) }

fun main(args: Array<String>) {
    runApplication<VerifierApplication>(*args) {
        addInitializers(beans(Clock.systemDefaultZone()).initializer())
    }
}
