//package com.example.kubernetes
//
//import com.intellij.openapi.actionSystem.ActionUpdateThread
//import com.intellij.openapi.actionSystem.AnAction
//import com.intellij.openapi.actionSystem.AnActionEvent
//import com.intellij.openapi.actionSystem.CommonDataKeys
//import com.intellij.openapi.editor.ex.EditorEx
//import com.intellij.openapi.project.Project
//import com.intellij.openapi.ui.Messages
//import com.intellij.psi.PsiElement
//import com.intellij.psi.PsiManager
//
//
//class ExtractToConfigMapVariableAction: AnAction() {
//    override fun getActionUpdateThread(): ActionUpdateThread {
//        return ActionUpdateThread.BGT
//    }
//
//
//    override fun actionPerformed(e: AnActionEvent) {
//        val editor = e.getRequiredData(CommonDataKeys.EDITOR)
//        val caretModel = editor.caretModel
//        // Getting the primary caret ensures we get the correct one of a possible many.
//        val primaryCaret = caretModel.primaryCaret
//        // Get the caret information
//        val logicalPos = primaryCaret.logicalPosition
//        val visualPos = primaryCaret.visualPosition
//        val caretOffset = primaryCaret.offset
//        // Build and display the caret report.
//        val report = """
//             $logicalPos
//             $visualPos
//             Offset: $caretOffset
//             """.trimIndent()
//        Messages.showInfoMessage(report, "Caret Parameters Inside The Editor")    }
//
//
//    override fun update(e: AnActionEvent) {
//        // Get required data keys
//        val project = e.project
//        val editor = e.getData(CommonDataKeys.EDITOR)
//        val psiElement = e.getData(CommonDataKeys.PSI_ELEMENT)
//        // Set visibility only in case of existing project and editor
//
////        println("psiElement = ${psiElement?.text}")
//
//
//        e.presentation.setEnabledAndVisible(project != null && editor != null)
//
//
//        val editor1 = CommonDataKeys.EDITOR.getData(e.dataContext) as EditorEx?
//        if (editor1 != null) {
//            val project: Project? = editor1.project
//            if (project != null) {
//                val psiFile = PsiManager.getInstance(project).findFile(editor1.virtualFile)
//                if (psiFile != null) {
//                    val caretModel = editor1.caretModel
//                    val element: PsiElement = psiFile.findElementAt(caretModel.offset)!! //scalar
//
//
//
//                    println("element = ${element.text}")
//                    println("parent = ${element.parent.parent.text}")
//                }
//            }
//        }
//    }
//
//
//
//}
//
