package com.example.kubernetes

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlin.util.capitalizeDecapitalize.toLowerCaseAsciiOnly
import org.jetbrains.yaml.YAMLElementGenerator
import org.jetbrains.yaml.YAMLFileType
import org.jetbrains.yaml.psi.*


internal class NewEnvironmentVariableLocalFix : LocalQuickFix {

    override fun getFamilyName(): String =
        NewEnvVariableYamlBundle.message("inspection.yaml.new.env.variable.warning.message")

    private fun mapContainsVariable(map: YAMLMapping, value: YAMLKeyValue): Boolean =
        map.getKeyValueByKey(value.keyText) != null


    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val yamlSequenceItem: YAMLSequenceItem = descriptor.psiElement as YAMLSequenceItem

        if (yamlSequenceItem.keysValues.size != 2) return

        val newDeclaredName: YAMLKeyValue = yamlSequenceItem.keysValues.toList().get(0)
        val newDeclaredValue: YAMLKeyValue = yamlSequenceItem.keysValues.toList().get(1) // declaration of the value

        val nameString = newDeclaredName.valueText
        val nameStringCamel = newDeclaredName.valueText.toLowerCaseAsciiOnly().toCamelCase()
        val valueString = newDeclaredValue.valueText

        val generator = project.service<YAMLElementGenerator>()
        val newKeyValue = generator.createYamlKeyValue(nameStringCamel, "\"$valueString\"")

        val yamlFiles: List<VirtualFile> = getYamlFiles(project, YAMLFileType.YML)
        val yamlMaps = getConfigMapsFromYamlFiles(project, yamlFiles, "the-map")

        val sameVariableExists = checkMapsContainYamlKeyValue(yamlMaps, newKeyValue)
        if (sameVariableExists) {
            notifyAndAbort(project, nameStringCamel)
            return
        }

        addNewVariableToConfigMaps(yamlMaps, newKeyValue)
        replaceSequenceElement(generator, yamlSequenceItem, nameString, nameStringCamel)
    }

    private fun replaceSequenceElement(
        generator: YAMLElementGenerator,
        yamlSequenceItem: YAMLSequenceItem,
        nameSequenceItem: String,
        keyValue: String
    ) {
        val tempYamlFile: YAMLFile = getTempYamlFileTemplateForNewSequenceItem(generator)

        val sequence = tempYamlFile.documents[0].topLevelValue as YAMLSequence
        val sequenceItem = sequence.items.first() as YAMLSequenceItem

        setNameValueForSequenceItem(sequenceItem, generator, nameSequenceItem)
        setValuesForConfigMapKeyRef(sequenceItem, generator, keyValue)

        yamlSequenceItem.replace(sequenceItem) as YAMLSequenceItem
    }

    private fun setNameValueForSequenceItem(
        sequenceItem: YAMLSequenceItem,
        generator: YAMLElementGenerator,
        nameSequenceItem: String
    ) {
        val nameKeyValue = sequenceItem.keysValues.find { it.keyText == "name" } as YAMLKeyValue
        val nameValueGenerated = generator.createYamlKeyValue("name", nameSequenceItem)
        nameKeyValue.setValue(nameValueGenerated.value!!)
    }

    private fun setValuesForConfigMapKeyRef(
        sequenceItem: YAMLSequenceItem,
        generator: YAMLElementGenerator,
        keyValue: String
    ) {
        val valueFromKeyValue = sequenceItem.keysValues.find { it.keyText == "valueFrom" } as YAMLKeyValue

        val innerConfigMapKeyRefMapping =
            getInnerMappingFromMapping(valueFromKeyValue.value as YAMLMapping, "configMapKeyRef")

        val nameElement = generator.createYamlKeyValue("name", "the-map")
        val keyElement = generator.createYamlKeyValue("key", keyValue)

        innerConfigMapKeyRefMapping.putYamlKeyValue(nameElement, keyElement)
    }

    private fun getInnerMappingFromMapping(valueFromMapping: YAMLMapping, innerName: String): YAMLMapping {
        val configMapKeyRefKeyValue = valueFromMapping.getKeyValueByKey(innerName) as YAMLKeyValue
        return configMapKeyRefKeyValue.value as YAMLMapping
    }

    private fun getTempYamlFileTemplateForNewSequenceItem(generator: YAMLElementGenerator): YAMLFile =
        generator.createDummyYamlWithText(
            """
            - name: 
              valueFrom:
                configMapKeyRef:
                  name: 
                  key:
            """.trimIndent()
        )


    private fun notifyAndAbort(project: Project, variableName: String) {
        MyNotifier.notifyInformationMessage(
            project,
            "Value \"$variableName\" already exists",
            "Aborting"
        )
    }

    private fun checkMapsContainYamlKeyValue(yamlMaps: List<YAMLMapping>, newKeyValue: YAMLKeyValue): Boolean =
        yamlMaps.all { topMapping ->
            val dataMapping = topMapping.getKeyValueByKey("data")?.value as YAMLMapping
            val contains = mapContainsVariable(dataMapping, newKeyValue)

            contains
        }


    private fun addNewVariableToConfigMaps(yamlMaps: List<YAMLMapping>, newKeyValue: YAMLKeyValue) {
        yamlMaps.forEach { topMapping ->
            val dataMapping = topMapping.getKeyValueByKey("data")?.value as YAMLMapping

            ApplicationManager.getApplication().invokeLater {
                dataMapping.putKeyValue(newKeyValue)
            }
        }
    }


    private fun getConfigMapsFromYamlFiles(
        project: Project,
        yamlFiles: List<VirtualFile>,
        mapName: String
    ): List<YAMLMapping> = yamlFiles
        .mapVirtualFileToYamlDocument(project)
        .filterConfigMapsForName(mapName)
        .map { yamlDocument -> yamlDocument.topLevelValue as YAMLMapping }

}

