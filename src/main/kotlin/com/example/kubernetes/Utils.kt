package com.example.kubernetes

import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope

internal fun getYamlFiles(project: Project, fileType: LanguageFileType): List<VirtualFile> {
    lateinit var files: Collection<VirtualFile>
    DumbService.getInstance(project).runReadActionInSmartMode {
        files = FileTypeIndex.getFiles(fileType, GlobalSearchScope.allScope(project))
    }
    return files.toList()
}
