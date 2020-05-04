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

package org.gradle.jvm.toolchain.internal;

import org.gradle.api.internal.AbstractNamedDomainObjectContainer;
import org.gradle.api.internal.CollectionCallbackActionDecorator;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.jvm.toolchain.JavaInstallation;
import org.gradle.jvm.toolchain.JavaInstallationContainer;
import org.gradle.jvm.toolchain.JavaInstallationDefinition;
import org.gradle.jvm.toolchain.JavaInstallationRegistry;

import javax.inject.Inject;
import java.io.File;

public class DefaultJavaInstallationContainer extends AbstractNamedDomainObjectContainer<JavaInstallationDefinition> implements JavaInstallationContainer {

    @Inject
    public DefaultJavaInstallationContainer(Instantiator instantiator, CollectionCallbackActionDecorator callbackDecorator, JavaInstallationRegistry installationRegistry) {
        super(JavaInstallationDefinition.class, instantiator, i -> i.getName(), callbackDecorator);

        createCurrentInstallation(installationRegistry);
    }

    private void createCurrentInstallation(JavaInstallationRegistry installationRegistry) {
        final JavaInstallation currentInstallation = installationRegistry.getInstallationForCurrentVirtualMachine().get();
        final File currentInstallationDirectory = currentInstallation.getInstallationDirectory().getAsFile();
        create("current").setPath(currentInstallationDirectory.getAbsolutePath());
    }

    @Override
    protected JavaInstallationDefinition doCreate(String name) {
        return new DefaultJavaInstallationDefinition(name);
    }

}
