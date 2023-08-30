package com.example.kubernetes

import com.intellij.codeInsight.daemon.quickFix.LightQuickFixTestCase
import com.intellij.codeInspection.LocalInspectionTool

//@RunWith(Parameterized::class)
//class NewEnvironmentVariableLocalFixTest : LightQuickFixParameterizedTestCase() {
//
//    override fun getTestDataPath(): String = "src/test/resources/testData"
//
//
//    override fun configureLocalInspectionTools(): Array<LocalInspectionTool> {
//        return arrayOf(CheckingNewVariableInspection())
//    }
//
////    fun testSmth() {
////        runSingle()
////    }
//
//    companion object {
//        @JvmStatic
//        @Parameterized.Parameters
//        fun testData(): Collection<Array<Any>> = listOf(
//            arrayOf("Deployment.yaml", "src/test/resources/testData")
//        )
//    }
//}


class NewEnvironmentVariableLocalFixTest : LightQuickFixTestCase() {

    override fun getTestDataPath(): String = "src/test/resources/testData"


    override fun configureLocalInspectionTools(): Array<LocalInspectionTool> {
        return arrayOf(CheckingNewVariableInspection())
    }

}
