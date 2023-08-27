package com.example.kubernetes

import com.intellij.codeInspection.*
import com.intellij.kubernetes.getYamlPsi
import com.intellij.kubernetes.vfsFile
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.util.capitalizeDecapitalize.toLowerCaseAsciiOnly
import org.jetbrains.yaml.YAMLElementGenerator
import org.jetbrains.yaml.YAMLFileType
import org.jetbrains.yaml.psi.*


private fun String.toCamelCase(): String {
    val pattern = "_([a-zA-Z0-9])".toRegex()
    return replace(pattern) {
        it.groupValues[1].uppercase()
    }
}

private fun <E : YAMLDocument> List<E>.filterConfigMaps(): MutableCollection<E> {

    return this.filter { yamlDocument ->
        yamlDocument as YAMLDocument

        val topMapping = yamlDocument.topLevelValue as YAMLMapping
        val kindValue = topMapping.getKeyValueByKey("kind")

        val metadataMapping = topMapping.getKeyValueByKey("metadata")?.value as YAMLMapping
        val nameValue = metadataMapping.getKeyValueByKey("name")

        kindValue?.valueText.equals("ConfigMap") &&
                nameValue?.valueText.equals("the-map")
    }.toMutableList()
}


class CheckingNewVariableInspection : LocalInspectionTool() {


    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : YamlPsiElementVisitor() {

            override fun visitSequenceItem(sequenceItem: YAMLSequenceItem) {

                val keyValues: List<YAMLKeyValue> = sequenceItem.keysValues.toList()
                if (keyValues.size != 2) return

                val possibleName = keyValues[0]
                val possibleValue = keyValues[1]

                if (possibleName.keyText == "name" && possibleValue.keyText == "value") {
                    if (possibleName.value == null || possibleValue.value == null) return

                    holder.registerProblem(
                        sequenceItem,
                        NewEnvVariableYamlBundle.message("inspection.yaml.new.env.variable"),
                        ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                        NewEnvironmentVariableLocalFix()
                    )

                }
            }

            override fun visitSequence(sequence: YAMLSequence) {
                val parent = sequence.parent
                if (parent.text.equals("env")) {
                    sequence.items.forEach {
                        visitSequenceItem(it)
                    }
                }
            }

        }
    }
}

private class NewEnvironmentVariableLocalFix : LocalQuickFix {

    override fun getFamilyName(): String {
        return NewEnvVariableYamlBundle.message("inspection.yaml.new.env.variable.warning.message")
    }

    private fun mapContainsVariable(map: YAMLMapping, value: YAMLKeyValue): Boolean {
        println(value.keyText)
        println(value.valueText)
        println(map.text)

        return map.getKeyValueByKey(value.keyText) != null
    }

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {

        val generator = project.service<YAMLElementGenerator>()

        val yamlSequenceItem: YAMLSequenceItem = descriptor.psiElement as YAMLSequenceItem

        val newDeclaredName: YAMLKeyValue = yamlSequenceItem.keysValues.toList().get(0)
        val newDeclaredValue: YAMLKeyValue = yamlSequenceItem.keysValues.toList().get(1) // declaration of the value

        val nameString = newDeclaredName.valueText
        val nameStringCamel = newDeclaredName.valueText.toLowerCaseAsciiOnly().toCamelCase()

        val valueString = newDeclaredValue.valueText

        val newKeyValue = generator.createYamlKeyValue(nameStringCamel, "\"$valueString\"")


        // checking configMaps, if everything is fine -- adding new variable

        val yamlFiles = getYamlFiles(project)
        yamlFiles.forEach {
            println(it.canonicalPath)
        }


        val yamlMaps = yamlFiles.map { yamlFile ->
            PsiTreeUtil.collectElementsOfType(yamlFile.getYamlPsi(project), YAMLDocument::class.java).first()
        }
            .filterConfigMaps()
            .map { yamlDocument ->
                yamlDocument as YAMLDocument
                yamlDocument.topLevelValue as YAMLMapping
            }


        val variableContainingYaml = mutableListOf<VirtualFile>()

        val sameVariableExists = yamlMaps.all { topMapping ->
            val dataMapping = topMapping.getKeyValueByKey("data")?.value as YAMLMapping
            val contains = mapContainsVariable(dataMapping, newKeyValue)
            if (contains)
                topMapping.vfsFile?.let { variableContainingYaml.add(it) }

            !contains
        }

        if (!sameVariableExists) {
            MyNotifier.notifyInformationMessage(
                project,
                "value \"$nameStringCamel\" already exists: ${
                    variableContainingYaml.joinToString(separator = ", ") {
                        it.canonicalPath.toString()
                    }
                }",
                "Aborting"
            )

            return
        }

        yamlMaps.forEach { topMapping ->
            println("mapping before adding variable = " + topMapping.text)
            val dataMapping = topMapping.getKeyValueByKey("data")?.value as YAMLMapping

            WriteCommandAction.runWriteCommandAction(project) {
                dataMapping.putKeyValue(newKeyValue)
            }
        }


        // doing refactoring

        val tempYamlFile = generator.createDummyYamlWithText(
            """
            - name: 
              valueFrom:
                configMapKeyRef:
                  name: 
                  key:
            """.trimIndent()
        )

        val sequence = tempYamlFile.documents[0].topLevelValue as YAMLSequence
        val sequenceItem = sequence.items.first() as YAMLSequenceItem

        val nameKeyValue = sequenceItem.keysValues.find { it.keyText == "name" } as YAMLKeyValue
        val nameValueGenerated = generator.createYamlKeyValue("name", nameString)
        nameKeyValue.setValue(nameValueGenerated.value!!)

        val valueFromKeyValue = sequenceItem.keysValues.find { it.keyText == "valueFrom" } as YAMLKeyValue

        val configMapKeyRefMapping = valueFromKeyValue.value as YAMLMapping
        val configMapKeyRefKeyValue = configMapKeyRefMapping.getKeyValueByKey("configMapKeyRef") as YAMLKeyValue
        val innerConfigMapKeyRefMapping = configMapKeyRefKeyValue.value as YAMLMapping

        val nameElement = generator.createYamlKeyValue("name", "the-map")
        val keyElement = generator.createYamlKeyValue("key", nameStringCamel)

        innerConfigMapKeyRefMapping.putKeyValue(nameElement)
        innerConfigMapKeyRefMapping.putKeyValue(keyElement)

        val newSequenceItem = yamlSequenceItem.replace(sequenceItem) as YAMLSequenceItem
    }


    private fun getYamlFiles(project: Project): List<VirtualFile> {
        lateinit var files: Collection<VirtualFile>
        DumbService.getInstance(project).runReadActionInSmartMode {
            files = FileTypeIndex.getFiles(YAMLFileType.YML, GlobalSearchScope.allScope(project))
        }
        return files.toList()
    }
}

