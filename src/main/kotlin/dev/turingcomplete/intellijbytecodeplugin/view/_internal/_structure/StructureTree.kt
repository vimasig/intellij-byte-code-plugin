package dev.turingcomplete.intellijbytecodeplugin.view._internal._structure

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.project.DumbAware
import com.intellij.ui.LoadingNode
import com.intellij.ui.scale.JBUIScale
import com.intellij.ui.tree.AsyncTreeModel
import com.intellij.ui.tree.BaseTreeModel
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.castSafelyTo
import com.intellij.util.ui.EmptyIcon
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.tree.TreeUtil
import dev.turingcomplete.intellijbytecodeplugin._ui.configureForCell
import dev.turingcomplete.intellijbytecodeplugin.bytecode.MethodDeclarationUtils
import dev.turingcomplete.intellijbytecodeplugin.bytecode.TypeUtils
import dev.turingcomplete.intellijbytecodeplugin.common.ClassFileContext
import dev.turingcomplete.intellijbytecodeplugin.common.CommonDataKeys
import dev.turingcomplete.intellijbytecodeplugin.openclassfiles._internal.FilesDropHandler
import dev.turingcomplete.intellijbytecodeplugin.view._internal.CopyValueAction
import dev.turingcomplete.intellijbytecodeplugin.view._internal.ViewValueAction
import dev.turingcomplete.intellijbytecodeplugin.view._internal._structure._class.ClassStructureNode
import dev.turingcomplete.intellijbytecodeplugin.view._internal._structure._common.InteractiveNode
import dev.turingcomplete.intellijbytecodeplugin.view._internal._structure._common.StructureNode
import dev.turingcomplete.intellijbytecodeplugin.view._internal._structure._common.ValueNode
import org.jetbrains.annotations.TestOnly
import java.awt.Component
import java.awt.event.*
import java.util.*
import javax.swing.AbstractCellEditor
import javax.swing.JLabel
import javax.swing.JTree
import javax.swing.tree.TreeCellEditor
import javax.swing.tree.TreeCellRenderer
import javax.swing.tree.TreeNode

internal class StructureTree private constructor(classFileContext: ClassFileContext,
                                                 parent: Disposable,
                                                 private val structureTreeModel: StructureTreeModel)
  : Tree(AsyncTreeModel(StructureTreeModel(classFileContext), true, parent)), DataProvider {

  // -- Companion Object -------------------------------------------------------------------------------------------- //

  companion object {
    fun create(classFileContext: ClassFileContext, parent: Disposable): StructureTree {
      val structureTreeModel = StructureTreeModel(classFileContext)
      return StructureTree(classFileContext, parent, structureTreeModel)
    }
  }

  // -- Properties -------------------------------------------------------------------------------------------------- //

  private val context = StructureTreeContext(classFileContext.project(), syncTree())

  // -- Initialization ---------------------------------------------------------------------------------------------- //

  init {
    setCellEditor(StructureTreeCellEditor())
    setCellRenderer(StructureTreeCellRenderer())
    isEditable = true
    addMouseListener(StructureTreeMouseAdapter())
    transferHandler = FilesDropHandler(classFileContext.project())
  }

  // -- Exposed Methods --------------------------------------------------------------------------------------------- //

  fun reload() {
    structureTreeModel.reload()
  }

  fun createToolBarActions(): ActionGroup {
    return DefaultActionGroup().apply {
      add(RenderOptionsGroup())
    }
  }

  @TestOnly
  internal fun getChildren(): List<TreeNode>? {
    return structureTreeModel.getChildren()
  }

  @TestOnly
  internal fun getChildren(parent: Any): List<TreeNode>? {
    return structureTreeModel.getChildren(parent)
  }

  override fun getData(dataId: String): Any? {
    val selectedStructureNode = selectionModel.selectionPath?.lastPathComponent.castSafelyTo<StructureNode>() ?: return null

    return when {
      PlatformDataKeys.PREDEFINED_TEXT.`is`(dataId) -> selectedStructureNode.searchProvider?.value
      CommonDataKeys.VALUE.`is`(dataId) -> {
        if (selectedStructureNode is ValueNode) selectedStructureNode.rawValue(context) else null
      }
      else -> null
    }
  }

  // -- Private Methods --------------------------------------------------------------------------------------------- //

  private fun syncTree(): () -> Unit = {
    TreeUtil.treeTraverser(this@StructureTree).forEach {
      if (it is StructureNode) {
        it.invalidateComponent()
      }
    }

    this@StructureTree.revalidate()
    this@StructureTree.repaint()
  }

  // -- Inner Type -------------------------------------------------------------------------------------------------- //

  private class StructureTreeModel(private val classFileContext: ClassFileContext) : BaseTreeModel<TreeNode>() {

    var rootNode: ClassStructureNode? = null

    override fun getRoot(): ClassStructureNode {
      if (rootNode == null) {
        rootNode = createRootNode()
      }
      return rootNode!!
    }

    fun getChildren(): List<TreeNode>? {
      return getChildren(root)
    }

    override fun getChildren(parent: Any): List<TreeNode>? {
      val structureNode = parent as StructureNode
      val asyncLoadChildrenInProgress = structureNode.asyncLoadChildren(classFileContext.workAsync())
      return if (asyncLoadChildrenInProgress) null else structureNode.children().toList()
    }

    fun reload() {
      rootNode = createRootNode()
      treeStructureChanged(null, null, null)
    }

    fun createRootNode(): ClassStructureNode {
      return ClassStructureNode(classFileContext.classNode(), classFileContext.classFile())
    }
  }

  // -- Inner Type -------------------------------------------------------------------------------------------------- //

  private inner class StructureTreeCellRenderer : TreeCellRenderer {

    private val loadingNodeLabel = JLabel(LoadingNode.getText()).apply {
      foreground = UIUtil.getInactiveTextColor()
      icon = JBUIScale.scaleIcon(EmptyIcon.create(8, 16))
    }

    override fun getTreeCellRendererComponent(tree: JTree, value: Any, selected: Boolean, expanded: Boolean, leaf: Boolean, row: Int, hasFocus: Boolean): Component {
      tree.rowHeight = 0 // Will use height of component

      return when (value) {
        is LoadingNode -> loadingNodeLabel
        is StructureNode -> value.component(selected, context)
        else -> throw IllegalArgumentException("Unknown value type ${value::class.java}")
      }.configureForCell(tree, selected, hasFocus)
    }
  }

  // -- Inner Type -------------------------------------------------------------------------------------------------- //

  private inner class StructureTreeCellEditor : AbstractCellEditor(), TreeCellEditor, ActionListener {

    override fun getCellEditorValue(): Any? {
      return null
    }

    override fun isCellEditable(mouseEvent: EventObject?): Boolean {
      if (mouseEvent is MouseEvent) {
        return getClosestPathForLocation(mouseEvent.x, mouseEvent.y)?.let { it.lastPathComponent is InteractiveNode }
               ?: false
      }

      return false
    }

    override fun getTreeCellEditorComponent(tree: JTree, value: Any, selected: Boolean, expanded: Boolean, leaf: Boolean, row: Int): Component {
      if (value !is InteractiveNode) {
        throw IllegalArgumentException("Value type ${value::class.java} is not interactive.")
      }

      return cellRenderer.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, true)
    }

    override fun actionPerformed(e: ActionEvent?) {
      stopCellEditing()
    }
  }

  // -- Inner Type -------------------------------------------------------------------------------------------------- //

  private inner class StructureTreeMouseAdapter : MouseAdapter() {

    override fun mousePressed(e: MouseEvent) {
      handleTreeMouseEvent(e)
    }

    override fun mouseReleased(e: MouseEvent) {
      handleTreeMouseEvent(e)
    }

    private fun handleTreeMouseEvent(event: InputEvent) {
      if (event !is MouseEvent || !event.isPopupTrigger) {
        return
      }

      val valueNode = getClosestPathForLocation(event.x, event.y)
                              ?.takeIf { it.lastPathComponent is ValueNode }
                              ?.let { it.lastPathComponent as ValueNode }
                      ?: return

      val actions = DefaultActionGroup().apply {
        valueNode.searchProvider?.let {
          add(it.searchAction())
          addSeparator()
        }

        add(CopyValueAction())
        add(ViewValueAction())
      }

      ActionManager.getInstance()
              .createActionPopupMenu(ActionPlaces.UNKNOWN, actions)
              .component
              .show(event.getComponent(), event.x, event.y)
    }
  }

  // -- Inner Type -------------------------------------------------------------------------------------------------- //

  private inner class RenderOptionsGroup : DefaultActionGroup("Render Options", true), Toggleable, DumbAware {

    init {
      templatePresentation.icon = AllIcons.Actions.Edit

      TypeUtils.TypeNameRenderMode.values().forEach {
        add(RenderOption(it.title, { context.typeNameRenderMode = it }, { context.typeNameRenderMode == it }))
      }

      addSeparator()

      add(RenderOption("Show Access as Decimal", { context.showAccessAsHex = false }, { !context.showAccessAsHex }))
      add(RenderOption("Show Access as Hex", { context.showAccessAsHex = true }, { context.showAccessAsHex }))

      addSeparator()

      MethodDeclarationUtils.MethodDescriptorRenderMode.values().forEach {
        add(RenderOption(it.title, { context.methodDescriptorRenderMode = it }, { context.methodDescriptorRenderMode == it }))
      }
    }
  }
}

