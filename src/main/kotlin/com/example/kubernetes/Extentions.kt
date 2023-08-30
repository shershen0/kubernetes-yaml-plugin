package com.example.kubernetes

import com.intellij.kubernetes.getYamlPsi
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.yaml.psi.YAMLDocument
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.YAMLMapping


internal fun String.toCamelCase(): String {
    val pattern = "_([a-zA-Z0-9])".toRegex()
    return replace(pattern) {
        it.groupValues[1].uppercase()
    }
}

internal fun <E : YAMLDocument> List<E>.filterConfigMapsForName(mapName: String): MutableCollection<E> {

    return this.filter { yamlDocument ->
        val topMapping = yamlDocument.topLevelValue as YAMLMapping
        val kindValue = topMapping.getKeyValueByKey("kind")

        val metadataMapping = topMapping.getKeyValueByKey("metadata")?.value as YAMLMapping
        val nameValue = metadataMapping.getKeyValueByKey("name")

        kindValue?.valueText.equals("ConfigMap") &&
                nameValue?.valueText.equals(mapName)
    }.toMutableList()
}


internal fun List<VirtualFile>.mapVirtualFileToYamlDocument(project: Project): List<YAMLDocument> {

    return this.map { yamlFile ->
        PsiTreeUtil.collectElementsOfType(yamlFile.getYamlPsi(project), YAMLDocument::class.java).first()
    }
}

internal fun YAMLMapping.putYamlKeyValue(vararg elements: YAMLKeyValue) {
    elements.toList().forEach { this.putKeyValue(it) }
}