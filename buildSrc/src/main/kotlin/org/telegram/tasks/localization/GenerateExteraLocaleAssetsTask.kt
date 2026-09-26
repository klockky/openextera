package org.telegram.tasks.localization

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * Mirrors every res/<values dir>/strings_extera.xml into assets/extera_locales/<values dir>/extera.xml.
 *
 * LocaleController reads these assets at runtime to overlay exteraGram strings on top of
 * server-provided language packs, which never contain them.
 */
@CacheableTask
abstract class GenerateExteraLocaleAssetsTask : DefaultTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val localeFiles: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val assetsOutputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val outputDir = assetsOutputDir.get().asFile
        val localesDir = outputDir.resolve("extera_locales")

        localesDir.deleteRecursively()

        for (file in localeFiles.files) {
            val target = localesDir.resolve(file.parentFile.name).resolve("extera.xml")
            target.parentFile.mkdirs()
            file.copyTo(target, overwrite = true)
        }
    }
}
