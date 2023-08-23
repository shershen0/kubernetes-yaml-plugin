package com.example.kubernetes

import com.intellij.codeInspection.*
import com.intellij.find.FindManager
import com.intellij.find.findUsages.FindUsagesManager
import com.intellij.kubernetes.KubernetesCompletionContributor
import com.intellij.kubernetes.k8sMetaType
import com.intellij.kubernetes.model.ConfigMapDataEntryType
import com.intellij.kubernetes.model.KubernetesAnyScalarType
import com.intellij.kubernetes.model.KustomizeK8sResourceReferenceType
import com.intellij.kubernetes.references.KubernetesLabelValueFindUsagesHandlerFactory
import com.intellij.kubernetes.references.KubernetesLabelValueReference
import com.intellij.kubernetes.vfsFile
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.FileIndex
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiSearchHelper
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.elementType
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.IndexId
import org.jetbrains.kotlin.idea.search.usagesSearch.searchReferencesOrMethodReferences
import org.jetbrains.kotlin.util.capitalizeDecapitalize.toLowerCaseAsciiOnly
import org.jetbrains.yaml.YAMLElementGenerator
import org.jetbrains.yaml.YAMLFileType
import org.jetbrains.yaml.psi.*
import org.jetbrains.yaml.psi.impl.YAMLBlockMappingImpl

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

                    println(possibleName.keyText)
                    println(possibleValue.keyText)

                    holder.registerProblem(
                        sequenceItem,
                        NewEnvVariableYamlBundle.message("inspection.yaml.new.env.variable"),
                        ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                        NewEnvironmentVariableLocalFix()
                    )

                } /*else {
                    *//* consider that the yaml part is corrent and looks like this:
                                - name: ALT_GREETING
                                  valueFrom:
                                    configMapKeyRef:
                                      name: the-map
                                      key: altGreeting

                        need to same the /map/ name, in order to refactor new variable according to that map
                     *//*


                    val valueFromKeyValue = sequenceItem.keysValues.find { it.keyText == "valueFrom" } as YAMLKeyValue
                    val configMapKeyRefMapping = valueFromKeyValue.value as YAMLMapping
                    val configMapKeyRefKeyValue = configMapKeyRefMapping.getKeyValueByKey("configMapKeyRef") as YAMLKeyValue
                    val innerConfigMapKeyRefMapping = configMapKeyRefKeyValue.value as YAMLMapping

//                    innerConfigMapKeyRefMapping.keyValues.forEach {
//                        println("other values: ${it.text}")
//                    }
                    mapNameValue = innerConfigMapKeyRefMapping.getKeyValueByKey("name")?.value!!
                    println("VALUE = ${mapNameValue.text}")
                }*/
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

        sequenceItem.keysValues.forEach {
            println("SKV: ${it.text}")
        }

        val nameKeyValue = sequenceItem.keysValues.find { it.keyText == "name" } as YAMLKeyValue
        val nameValueGenerated = generator.createYamlKeyValue("name", nameString)
        nameKeyValue.setValue(nameValueGenerated.value!!)


        val valueFromKeyValue = sequenceItem.keysValues.find { it.keyText == "valueFrom" } as YAMLKeyValue

        val configMapKeyRefMapping = valueFromKeyValue.value as YAMLMapping
        configMapKeyRefMapping.keyValues.forEach {
            println("Child =- ${it.text}")
        }

        val configMapKeyRefKeyValue = configMapKeyRefMapping.getKeyValueByKey("configMapKeyRef") as YAMLKeyValue
        val innerConfigMapKeyRefMapping = configMapKeyRefKeyValue.value as YAMLMapping

        println("inner = ${configMapKeyRefMapping.text}")


        val nameElement = generator.createYamlKeyValue("name", "the-map")
        val keyElement = generator.createYamlKeyValue("key", nameStringCamel)

        innerConfigMapKeyRefMapping.putKeyValue(nameElement)
        innerConfigMapKeyRefMapping.putKeyValue(keyElement)


        println("inner after = \n${configMapKeyRefMapping.text}")

        println("WHOLE = ${sequenceItem.text}")

        yamlSequenceItem.replace(sequenceItem)


//        println(nameElement.text)
//        println(nameElement.value?.text)
//
//        val nameValueReference = nameElement.value?.reference
//
//        val resolved = nameValueReference?.resolve()
//        println(resolved?.text)


//        val files = PsiSearchHelper.getInstance(project).findFilesWithPlainTextWords(nameElement.valueText)
//        files.forEach {
//            println(it.text)
//        }

//        val canFindUsages = KubernetesLabelValueFindUsagesHandlerFactory().canFindUsages(nameElement.value!! as YAMLScalar)
//        println(canFindUsages) // false

        val nameElementScalar = nameElement.value as YAMLScalar
//
        println(nameElementScalar.text)
////        nameElementScalar.references.toList().forEach {
////            println("references = $it")
////        }
//
//        val references = nameElementScalar.searchReferencesOrMethodReferences()
//        references.toList().forEach {
//            println("resolve = ${it.resolve()}")
//        }
////


        val listOfReferences = ReferencesSearch.search(nameElementScalar, GlobalSearchScope.allScope(project))
        listOfReferences.mapping {
            println("mapping")
            val element = it.resolve()
            println(element)
        }


//        ApplicationManager.getApplication().invokeLater {
//            FindManager.getInstance(project).findUsages(nameElementScalar.originalElement)
//        }


//        FileBasedIndex.getInstance().getContainingFiles(getName(), indexKey, GlobalSearchScope.projectScope(project))
//
//        FileBasedIndex.getInstance()



    }

    private fun getYamlFiles(module: Module): List<VirtualFile> {
        lateinit var files: Collection<VirtualFile>
        DumbService.getInstance(module.project).runReadActionInSmartMode {
            files = FileTypeIndex.getFiles(YAMLFileType.YML, GlobalSearchScope.moduleScope(module))
        }
        return files.toList()
    }
}

