package dev.turingcomplete.intellijbytecodeplugin.openclassfiles._internal

import com.intellij.icons.AllIcons
import com.intellij.openapi.fileChooser.FileTypeDescriptor
import com.intellij.openapi.fileChooser.ex.FileChooserDialogImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VfsUtil
import dev.turingcomplete.intellijbytecodeplugin.openclassfiles.OpenClassFilesListener
import dev.turingcomplete.intellijbytecodeplugin.openclassfiles.OpenClassFilesToolWindowAction

class FileChooserAction : OpenClassFilesToolWindowAction("Open .class Files...", AllIcons.General.OpenDisk) {
  // -- Companion Object -------------------------------------------------------------------------------------------- //
  // -- Properties -------------------------------------------------------------------------------------------------- //
  // -- Initialization ---------------------------------------------------------------------------------------------- //
  // -- Exposed Methods --------------------------------------------------------------------------------------------- //

  override fun execute(project: Project) {
    val descriptor = FileTypeDescriptor("Open .class Files", "class")
    val dialog = FileChooserDialogImpl(descriptor, project)
    val startPath = project.guessProjectDir() ?: VfsUtil.getUserHomeDir()
    val classFilesToOpen = dialog.choose(project, startPath).filter { it.isValid }.toList()
    project.messageBus.syncPublisher(OpenClassFilesListener.OPEN_CLASS_FILES_TOPIC).openFiles(classFilesToOpen)
  }

  // -- Private Methods --------------------------------------------------------------------------------------------- //
  // -- Inner Type -------------------------------------------------------------------------------------------------- //
}