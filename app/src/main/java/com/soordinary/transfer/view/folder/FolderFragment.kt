package com.soordinary.transfer.view.folder

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.webkit.MimeTypeMap
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.core.view.GravityCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.soordinary.transfer.R
import com.soordinary.transfer.databinding.FragmentFolderBinding
import com.soordinary.transfer.view.MainActivity
import java.io.File

class FolderFragment : Fragment(R.layout.fragment_folder)  {

    private lateinit var binding: FragmentFolderBinding
    private lateinit var folderAdapter: FolderAdapter
    private lateinit var rootPath : String
    private lateinit var currentPath : String
    private lateinit var filePickerLauncher: ActivityResultLauncher<Array<String>>
    // 声明Dialog实例（可选，按需创建）
    private var createFolderDialog: CreateFolderDialog? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding = FragmentFolderBinding.bind(view)

        // 1. 初始化根路径
        rootPath = (requireActivity().getExternalFilesDir(null)?.path  ?: requireActivity().filesDir.path)+"/storage"
        // 2. 检查并创建文件夹（核心修改点）
        createFolderIfNotExists(rootPath)

        currentPath = "/"

        binding.initView()
        updateFilePathDisplay()
        loadFolderList()
        initFilePicker()
    }

    /**
     * 工具方法：检查文件夹是否存在，不存在则创建
     * @param folderPath 要检查的文件夹路径
     */
    private fun createFolderIfNotExists(folderPath: String) {
        val folder = File(folderPath)
        if (!folder.exists()) {
            val isCreated = folder.mkdirs()
            if (isCreated) {
                // 可选：创建成功的日志
                // Log.d("FolderFragment", "文件夹创建成功: $folderPath")
            } else {
                // 可选：创建失败的日志（排查权限问题）
                // Log.e("FolderFragment", "文件夹创建失败: $folderPath")
            }
        }
    }

    /**
     * 初始化View
     */
    private fun FragmentFolderBinding.initView() {
        iconP.setOnClickListener {
            (requireActivity() as MainActivity).binding.layoutMain.openDrawer(GravityCompat.START)
        }

        folderAdapter = FolderAdapter(
            fragment = this@FolderFragment,
            folderList = emptyList(),
            onDirClick = { newPath ->
                currentPath = FolderTreeLoader.getRelativePath(newPath)
                updateFilePathDisplay()
                loadFolderList()
            },
            onFileClick = { openFileWithSystemChooser(it) },
            onLongClick = {
                // 弹出编辑弹窗
                val editDialog = EditDialog(
                    requireContext(),
                    it,
                    {
                        loadFolderList()
                    }
                )
                editDialog.show()
            },
            onFileChecked = { _, _ -> }
        )

        folderList.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = folderAdapter
            setHasFixedSize(true)
        }
    }

    /**
     * 更新路径显示
     */
    private fun updateFilePathDisplay() {
        val displayPath = if (currentPath == "/" || currentPath == File.separator) {
            "根目录"
        } else {
            currentPath
        }
        binding.filePath.text = displayPath
    }

    /**
     * 加载文件列表
     */
    private fun loadFolderList() {
        Thread {
            val fileList = FolderTreeLoader.loadFolderList("$rootPath$currentPath")
            activity?.runOnUiThread {
                folderAdapter.updateData(fileList)
            }
        }.start()
    }

    /**
     * 打开文件
     */
    private fun openFileWithSystemChooser(filePath: String) {
        val context = requireContext()
        val file = File(filePath)
        if (!file.exists()) return

        val mimeType = getMimeType(filePath) ?: "*/*"
        val intent = Intent(Intent.ACTION_VIEW).apply {
            val fileUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                FileProvider.getUriForFile(context, "com.soordinary.transfer.fileprovider", file)
            } else {
                Uri.fromFile(file)
            }
            setDataAndType(fileUri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
            val chooser = Intent.createChooser(intent, "选择应用打开文件")
            context.startActivity(chooser)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 获取MIME类型
     */
    private fun getMimeType(filePath: String): String? {
        var type: String? = null
        val extension = MimeTypeMap.getFileExtensionFromUrl(filePath)
        if (extension != null) {
            type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.lowercase())
        }
        return type
    }

    /**
     * 初始化文件选择器（只传递Uri给Dialog处理）
     */
    private fun initFilePicker() {
        filePickerLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            uri?.let {
                // 调用Dialog的内部方法处理复制
                createFolderDialog?.copyFileToTargetPath(it)
            }
        }
    }

    /**
     * 简化后的弹窗调用：只需传递参数，无回调逻辑
     */
    fun showCreateFolderDialog() {
        // 创建Dialog并传递关键参数
        createFolderDialog = CreateFolderDialog(
            context = requireContext(),
            rootPath = rootPath,
            currentPath = currentPath,
            refreshCallback = { loadFolderList() }, // 仅传递刷新方法
            filePickerLauncher = filePickerLauncher
        )
        createFolderDialog?.show()
    }

    /**
     * 销毁时释放Dialog（避免内存泄漏）
     */
    override fun onDestroyView() {
        super.onDestroyView()
        binding.folderList.adapter = null
        createFolderDialog?.dismiss()
        createFolderDialog = null
    }
}