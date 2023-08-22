package com.example.kubernetes

import com.intellij.codeInspection.*
import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
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
                println("new")
                sequenceItem.keysValues.forEach {
//            println("value = ${it.value?.text}, key = ${it.key?.text}")
                }

                val keyValues: List<YAMLKeyValue> = sequenceItem.keysValues.toList()
                if (keyValues.size != 2) return

                val possibleName = keyValues[0]
                val possibleValue = keyValues[1]

                if (possibleName.keyText == "name" && possibleValue.keyText == "value") {
                    if (possibleName.value == null || possibleValue.value == null) return

                    println(possibleName.keyText)
                    println(possibleValue.keyText)

//                    holder.registerProblem(
//                        sequenceItem,
//                        NewEnvVariableYamlBundle.message("inspection.yaml.new.env.variable"),
//                        ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
//                        NewEnvironmentVariableLocalFix()
//                    )

//                    val valueElement = sequenceItem.keysValues.toList()[1]

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


//        tempYamlFile.documents.forEach {
//            println("doc = ${it.text}")
//        }

        val sequence = tempYamlFile.documents[0].topLevelValue as YAMLSequence
        val sequenceItem = sequence.items.first() as YAMLSequenceItem

        sequenceItem.keysValues.forEach {
            println("SKV: ${it.text}")
        }

        val nameKeyValue = sequenceItem.keysValues.find { it.keyText == "name" } as YAMLKeyValue
        val nameValueGenerated = generator.createYamlKeyValue("name", nameString)
        nameKeyValue.setValue(nameValueGenerated.value!!)


        val valueFromKeyValue = sequenceItem.keysValues.find { it.keyText == "valueFrom" } as YAMLKeyValue

        val configMapKeyRefMapping = valueFromKeyValue.value as YAMLMapping
        println("inner = ${configMapKeyRefMapping.text}")

        configMapKeyRefMapping.children.forEach {
            println("child = ${it.text}")
        }

        val nameElement = generator.createYamlKeyValue("name", "the-map")
        val keyElement = generator.createYamlKeyValue("key", nameStringCamel)

        configMapKeyRefMapping.putKeyValue(nameElement)
        configMapKeyRefMapping.putKeyValue(keyElement)


        println("inner after = \n${configMapKeyRefMapping.text}")

        println("WHOLE = ${sequenceItem.text}")

        yamlSequenceItem.replace(sequenceItem)


        // add value to the config-map

        // should i make it in all modules? or only in the current one?

//
//        val projectName = project.name
//        val vFiles = ProjectRootManager.getInstance(project)
//            .contentSourceRoots
//        val sourceRootsList: String = Arrays.stream(vFiles)
//            .map { obj: VirtualFile -> obj.getUrl() }
//            .collect(Collectors.joining("\n"))
//
//        println(sourceRootsList)
//
//
//        val projectFileIndex = ProjectFileIndex.getInstance(project)
//
//        // source content module
//
//        val module = projectFileIndex.getModuleForFile(sequenceItem.containingFile.virtualFile)
//
//        if(module == null) {
//            println("MODULE IS NULL")
//            // cannot add new value to the config-map
//            return
//        }
//
//        println("module = $module")
//        val yamlFiles = getYamlFiles(module)
//        println(yamlFiles)
    }

    private fun getYamlFiles(module: Module): List<VirtualFile> {
        lateinit var files: Collection<VirtualFile>
        DumbService.getInstance(module.project).runReadActionInSmartMode {
            files = FileTypeIndex.getFiles(YAMLFileType.YML, GlobalSearchScope.moduleScope(module))
        }
        return files.toList()
    }
}

