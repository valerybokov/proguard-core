/*
 * ProGuardCORE -- library to process Java bytecode.
 *
 * Copyright (c) 2002-2022 Guardsquare NV
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

package proguard.util.kotlin.asserter;

import java.util.Arrays;
import java.util.List;
import proguard.classfile.ClassPool;
import proguard.classfile.Clazz;
import proguard.classfile.kotlin.KotlinMetadata;
import proguard.classfile.kotlin.visitor.KotlinMetadataRemover;
import proguard.classfile.kotlin.visitor.KotlinMetadataVisitor;
import proguard.classfile.kotlin.visitor.ReferencedKotlinMetadataVisitor;
import proguard.classfile.util.WarningLogger;
import proguard.resources.file.ResourceFile;
import proguard.resources.file.ResourceFilePool;
import proguard.resources.file.visitor.ResourceFileProcessingFlagFilter;
import proguard.resources.file.visitor.ResourceFileVisitor;
import proguard.resources.kotlinmodule.KotlinModule;
import proguard.resources.kotlinmodule.visitor.KotlinModuleVisitor;
import proguard.util.ProcessingFlagSetter;
import proguard.util.ProcessingFlags;
import proguard.util.kotlin.asserter.constraint.ClassIntegrity;
import proguard.util.kotlin.asserter.constraint.ConstructorIntegrity;
import proguard.util.kotlin.asserter.constraint.DeclarationContainerIntegrity;
import proguard.util.kotlin.asserter.constraint.FileFacadeIntegrity;
import proguard.util.kotlin.asserter.constraint.FunctionIntegrity;
import proguard.util.kotlin.asserter.constraint.KmAnnotationIntegrity;
import proguard.util.kotlin.asserter.constraint.KotlinAsserterConstraint;
import proguard.util.kotlin.asserter.constraint.KotlinModuleIntegrity;
import proguard.util.kotlin.asserter.constraint.MultiFilePartIntegrity;
import proguard.util.kotlin.asserter.constraint.PropertyIntegrity;
import proguard.util.kotlin.asserter.constraint.SyntheticClassIntegrity;
import proguard.util.kotlin.asserter.constraint.TypeIntegrity;
import proguard.util.kotlin.asserter.constraint.ValueParameterIntegrity;

/**
 * Performs a series of checks to see whether the kotlin metadata is intact.
 */
public class KotlinMetadataAsserter
{

    // This is the list of constraints that will be checked using this asserter.
    private static final List<KotlinAsserterConstraint> DEFAULT_CONSTRAINTS = Arrays.asList(
        new FunctionIntegrity(),
        new ConstructorIntegrity(),
        new PropertyIntegrity(),
        new ClassIntegrity(),
        new TypeIntegrity(),
        new KmAnnotationIntegrity(),
        new ValueParameterIntegrity(),
        new SyntheticClassIntegrity(),
        new FileFacadeIntegrity(),
        new MultiFilePartIntegrity(),
        new DeclarationContainerIntegrity(),
        new KotlinModuleIntegrity()
    );

    public void execute(WarningLogger    warningLogger,
                        ClassPool        programClassPool,
                        ClassPool        libraryClassPool,
                        ResourceFilePool resourceFilePool)
    {
        Reporter reporter = new DefaultReporter(warningLogger);
        MyKotlinMetadataAsserter kotlinMetadataAsserter = new MyKotlinMetadataAsserter(reporter,
                                                                                       DEFAULT_CONSTRAINTS,
                                                                                       programClassPool,
                                                                                       libraryClassPool);

        reporter.setErrorMessage("Warning: Kotlin metadata errors encountered in %s. Not processing the metadata for this class.");
        programClassPool.classesAccept(new ReferencedKotlinMetadataVisitor(kotlinMetadataAsserter));
        libraryClassPool.classesAccept(new ReferencedKotlinMetadataVisitor(kotlinMetadataAsserter));

        reporter.setErrorMessage("Warning: Kotlin module errors encountered in module %s. Not processing the metadata for this module.");
        resourceFilePool.resourceFilesAccept(new ResourceFileProcessingFlagFilter(0,
                                                                                  ProcessingFlags.DONT_PROCESS_KOTLIN_MODULE,
                                                                                  kotlinMetadataAsserter));
    }

    /**
     * This class performs a series of checks to see whether the kotlin metadata is intact
     */
    public static class MyKotlinMetadataAsserter
        implements          KotlinMetadataVisitor,
                            ResourceFileVisitor,
                            KotlinModuleVisitor
    {
        private final List<? extends KotlinAsserterConstraint> constraints;
        private final Reporter                                 reporter;
        private final ClassPool                                programClassPool;
        private final ClassPool                                libraryClassPool;

        MyKotlinMetadataAsserter(Reporter                       reporter,
            List<KotlinAsserterConstraint> constraints,
            ClassPool                      programClassPool,
            ClassPool                      libraryClassPool)
        {
            this.constraints      = constraints;
            this.reporter         = reporter;
            this.programClassPool = programClassPool;
            this.libraryClassPool = libraryClassPool;
        }

        @Override
        public void visitAnyKotlinMetadata(Clazz clazz, KotlinMetadata kotlinMetadata)
        {
            reporter.resetCounter(clazz.getName());

            constraints.forEach(constraint -> constraint.check(reporter, programClassPool, libraryClassPool, clazz, kotlinMetadata));

            if (reporter.getCount() > 0)
            {
                clazz.accept(new KotlinMetadataRemover());
            }
        }

        @Override
        public void visitKotlinModule(KotlinModule kotlinModule)
        {
            reporter.resetCounter(kotlinModule.name);

            constraints.forEach(constraint -> constraint.check(reporter, kotlinModule));

            if (reporter.getCount() > 0)
            {
                kotlinModule.accept(new ProcessingFlagSetter(ProcessingFlags.DONT_PROCESS_KOTLIN_MODULE));
            }
        }

        @Override
        public void visitResourceFile(ResourceFile resourceFile) {}
    }
}
