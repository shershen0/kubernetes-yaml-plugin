package com.example.kubernetes

import com.intellij.AbstractBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.PropertyKey

class NewEnvVariableYamlBundle : AbstractBundle(BUNDLE) {
    companion object {
        private val instance = NewEnvVariableYamlBundle()

        const val BUNDLE = "messages.NewEnvVariableInspectionBundle"

        fun message(key: @PropertyKey(resourceBundle = BUNDLE) String, vararg params: Any): @Nls String {
            return instance.getMessage(key, *params)
        }
    }
}
