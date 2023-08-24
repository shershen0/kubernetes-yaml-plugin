package com.example.kubernetes

import com.intellij.codeInspection.*
import com.intellij.kubernetes.vfsFile
import com.intellij.notification.NotificationDisplayType
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.idea.base.psi.copied
import org.jetbrains.kotlin.util.capitalizeDecapitalize.toLowerCaseAsciiOnly
import org.jetbrains.yaml.YAMLElementGenerator
import org.jetbrains.yaml.YAMLFileType
import org.jetbrains.yaml.psi.*


fun String.toCamelCase(): String {
    val pattern = "_([a-zA-Z0-9])".toRegex()
    return replace(pattern) {
        it.groupValues[1].uppercase()
    }
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

//                    println(possibleName.keyText)
//                    println(possibleValue.keyText)

                    holder.registerProblem(
                        sequenceItem,
                        NewEnvVariableYamlBundle.message("inspection.yaml.new.env.variable"),
                        ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                        NewEnvironmentVariableLocalFix()
                    )

                }

//                val valueFromKeyValue = sequenceItem.keysValues.find { it.keyText == "valueFrom" } as YAMLKeyValue
//                val configMapKeyRefMapping = valueFromKeyValue.value as YAMLMapping
//                val configMapKeyRefKeyValue = configMapKeyRefMapping.getKeyValueByKey("configMapKeyRef") as YAMLKeyValue
//                val innerConfigMapKeyRefMapping = configMapKeyRefKeyValue.value as YAMLMapping
//                val nameKeyValue = innerConfigMapKeyRefMapping.getKeyValueByKey("name")
//                println("nameKeyValue = ${nameKeyValue?.text}")
//                println(nameKeyValue?.reference)
//
//                println(nameKeyValue?.value?.reference) //null
//
//                val resolved = nameKeyValue?.value?.reference?.resolve()
//                println("resolved = $resolved")
//                println("file = ${resolved?.vfsFile?.name}")

//                nameKeyValue = name: the-map
//                null
//                com.intellij.kubernetes.references.KubernetesMapResourceReference(YAML plain scalar text:(0,7))
//                resolved = YAML plain scalar text
//                file = configMap.yaml

//                val query = ReferencesSearch.search(nameKeyValue?.navigationElement!!)
//                query.forEach {
//                    val psi = it.resolve()
//                    println(psi)
//                }
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
//        return NewEnvVariableYamlBundle.message("inspection.yaml.new.env.variable.warning.message")
    }

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {


        val generator = project.service<YAMLElementGenerator>()

        val yamlSequenceItem: YAMLSequenceItem = descriptor.psiElement as YAMLSequenceItem


        val newDeclaredName: YAMLKeyValue = yamlSequenceItem.keysValues.toList().get(0)
        val newDeclaredValue: YAMLKeyValue = yamlSequenceItem.keysValues.toList().get(1) // declaration of the value

        val nameString = newDeclaredName.valueText
        val nameStringCamel = newDeclaredName.valueText.toLowerCaseAsciiOnly().toCamelCase()

        val valueString = newDeclaredValue.valueText


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

//        sequenceItem.keysValues.forEach {
//            println("SKV: ${it.text}")
//        }

        val nameKeyValue = sequenceItem.keysValues.find { it.keyText == "name" } as YAMLKeyValue
        val nameValueGenerated = generator.createYamlKeyValue("name", nameString)
        nameKeyValue.setValue(nameValueGenerated.value!!)


        val valueFromKeyValue = sequenceItem.keysValues.find { it.keyText == "valueFrom" } as YAMLKeyValue

        val configMapKeyRefMapping = valueFromKeyValue.value as YAMLMapping
//        configMapKeyRefMapping.keyValues.forEach {
//            println("Child = ${it.text}")
//        }

        val configMapKeyRefKeyValue = configMapKeyRefMapping.getKeyValueByKey("configMapKeyRef") as YAMLKeyValue
        val innerConfigMapKeyRefMapping = configMapKeyRefKeyValue.value as YAMLMapping

//        println("inner = ${configMapKeyRefMapping.text}")


        val nameElement = generator.createYamlKeyValue("name", "the-map")
        val keyElement = generator.createYamlKeyValue("key", nameStringCamel)

        innerConfigMapKeyRefMapping.putKeyValue(nameElement)
        innerConfigMapKeyRefMapping.putKeyValue(keyElement)

//        println("inner after = \n${configMapKeyRefMapping.text}")

        println("WHOLE = ${sequenceItem.text}")

        val newSequenceItem = yamlSequenceItem.replace(sequenceItem) as YAMLSequenceItem


        // БАГА ЕСТЬ НЕ ВОЗЩВРАЩАТЬ НОВЫЙ ЭЛЕМЕНТ ИБО НЕТ ВИРТУАЛ ФАЙЛА ХОТЯ ОН ОБЯЗАН БЫТЬ КАЖЕТСЯ В ЛЮБОБОМ СЛУЧАЕ

        val valueFromKeyValue_ = newSequenceItem.keysValues.find { it.keyText == "valueFrom" } as YAMLKeyValue
        val configMapKeyRefMapping_ = valueFromKeyValue_.value as YAMLMapping
        val configMapKeyRefKeyValue_ = configMapKeyRefMapping_.getKeyValueByKey("configMapKeyRef") as YAMLKeyValue
        val innerConfigMapKeyRefMapping_ = configMapKeyRefKeyValue_.value as YAMLMapping
        val nameKeyValue_ = innerConfigMapKeyRefMapping_.getKeyValueByKey("name")
        println("nameKeyValue = ${nameKeyValue_?.text}")
        println("reference = ${nameKeyValue_?.value?.reference}")
        println("references = ${nameKeyValue_?.value?.references?.toList()}")


        val resolved = nameKeyValue_?.value?.reference?.resolve()
        println("resolved = $resolved")

        resolved.let {
            println("fileName = ${it?.vfsFile?.name}")
            println("resolvedElement = ${it?.text}")

            it?.let { it1 -> addNewVariableToDataSectionInConfigMap(project, it1, nameStringCamel, valueString) }
        }

    }

    private fun addNewVariableToDataSectionInConfigMap(
        project:  Project,
        mapNamePsiElement: PsiElement,
        newVariableName: String,
        newVariableValue: String
    ) {
        println("addNewVariableToDataSectionInConfigMap")

        var parentMapping = PsiTreeUtil.getParentOfType<YAMLMapping>(
            mapNamePsiElement,
            YAMLMapping::class.java
        )

        println("parentMapping = ${parentMapping?.text}") // name: the-map

        parentMapping = PsiTreeUtil.getParentOfType<YAMLMapping>(
            parentMapping,
            YAMLMapping::class.java
        )

        println("parentMapping = ${parentMapping?.text}")

        val dataEntry = parentMapping?.keyValues?.filter {
            it.keyText.equals("data")
        }?.single()

        println("dataEntry = ${dataEntry?.text}")

        val mapping = dataEntry?.value as YAMLMapping

        val variableIfItExists = mapping.getKeyValueByKey(newVariableName)


        if (variableIfItExists?.isValid == true) {
            println("Element already exists with such name")



            // timed notification, will disappear ini 10s
            ApplicationManager.getApplication().invokeLater {
                MyNotifier.notifyInformationMessage(
                    project,
                    "value \"$newVariableName\" already exists",
                    "Aborting"
                )
            }


            // should i revert the changes?
            return
        }


        val generator = project.service<YAMLElementGenerator>()


        val newVariableValue = generator.createYamlKeyValue(newVariableName, "\"$newVariableValue\"")

//        ApplicationManager.getApplication().invokeLater {
//            WriteCommandAction.runWriteCommandAction(project) {
                mapping.putKeyValue(newVariableValue)
//            }
//        }
    }

    private fun getYamlFiles(module: Module): List<VirtualFile> {
        lateinit var files: Collection<VirtualFile>
        DumbService.getInstance(module.project).runReadActionInSmartMode {
            files = FileTypeIndex.getFiles(YAMLFileType.YML, GlobalSearchScope.moduleScope(module))
        }
        return files.toList()
    }
}

