/*
 * Copyright 2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.jvm.toolchain;

import org.gradle.api.JavaVersion;
import org.gradle.api.internal.file.FileFactory;
import org.gradle.api.internal.provider.Providers;
import org.gradle.api.provider.Provider;
import org.gradle.internal.service.ServiceRegistry;

import javax.inject.Inject;
import java.io.File;

public class JavaToolchains {

    private JavaInstallationContainer installations;
    private JavaInstallationRegistry registry;
    private FileFactory fileFactory;
    private final ServiceRegistry services;

    @Inject
    public JavaToolchains(JavaInstallationContainer installations, JavaInstallationRegistry registry, FileFactory fileFactory, ServiceRegistry services) {
        this.installations = installations;
        this.registry = registry;
        this.fileFactory = fileFactory;
        this.services = services;
    }

    Provider<ResolvedToolchain> query(ToolchainRequirements requirements) {
//        installations.forEach(i -> System.out.println("-" + i.getName() + ":" + i.getPath()));
        for (JavaInstallationDefinition installation : installations) {
            final JavaInstallation javaInstallation = registry.installationForDirectory(fileFactory.dir(new File(installation.getPath()))).get();
            if (javaInstallation.getJavaVersion() == JavaVersion.VERSION_14) {
                return Providers.of(new ResolvedToolchain(javaInstallation, services.get(NewJavaCompilerFactory.class)));
            }
        }
        throw new IllegalStateException("cannot found a toolchain");
//
//        final String path = installations.iterator().next().getPath();
//        final Provider<JavaInstallation> installationProvider = registry.installationForDirectory(fileFactory.dir(new File(path)));
    }

}
