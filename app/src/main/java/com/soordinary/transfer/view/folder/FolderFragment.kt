package com.soordinary.transfer.view.folder

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.webkit.MimeTypeMap
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
    // 备份路径拼接
    private lateinit var rootPath : String
    private lateinit var currentPath : String

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding = FragmentFolderBinding.bind(view)
        rootPath = requireActivity().getExternalFilesDir(null)?.path  ?: requireActivity().filesDir.path
        currentPath = "/"

        // 初始化点击事件 + 初始化文件列表 + 更新路径显示
        binding.initView()
        // 首次加载：更新路径文本 + 加载文件列表
        updateFilePathDisplay()
        loadFolderList()
    }

    /**
     * 初始化RecyclerView（文件列表）
     */
    private fun FragmentFolderBinding.initView() {
        // 点击头像打开侧边栏
        iconP.setOnClickListener {
            (requireActivity() as MainActivity).binding.layoutMain.openDrawer(GravityCompat.START)
        }
        // 初始化Adapter（初始空列表）
        folderAdapter = FolderAdapter(
            fragment = this@FolderFragment,
            folderList = emptyList(),
            onDirClick = { newPath ->
                // 目录跳转回调：更新路径 → 刷新路径显示 → 重新加载列表
                currentPath = FolderTreeLoader.getRelativePath(newPath)
                updateFilePathDisplay()
                loadFolderList()
            },
            onFileClick = {
                openFileWithSystemChooser(it)
            },
            onFileChecked = { fileEntity, isChecked ->
                // 可选：文件勾选状态回调，用于后续备份逻辑
            }
        )

        // 绑定RecyclerView（注意布局中ID是folder_list）
        folderList.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = folderAdapter
            setHasFixedSize(true) // 优化性能
        }
    }

    /**
     * 更新文件路径显示（核心：把currentPath显示到file_path控件）
     */
    private fun updateFilePathDisplay() {
        // 优化路径显示：根目录显示"根目录"，其他路径显示完整路径
        val displayPath = if (currentPath == "/" || currentPath == File.separator) {
            "根目录"
        } else {
            currentPath
        }
        binding.filePath.text = displayPath
    }

    /**
     * 加载文件列表（子线程读取，避免主线程阻塞）
     */
    private fun loadFolderList() {
        Thread {
            val fileList = FolderTreeLoader.loadFolderList("$rootPath$currentPath")
            activity?.runOnUiThread {
                folderAdapter = FolderAdapter(
                    fragment = this@FolderFragment,
                    folderList = fileList,
                    onDirClick = { newPath ->
                        currentPath = FolderTreeLoader.getRelativePath(newPath)
                        updateFilePathDisplay()
                        loadFolderList()
                    },
                    onFileClick = {
                        openFileWithSystemChooser(it)
                    },
                    onFileChecked = { _, _ -> }
                )
                binding.folderList.adapter = folderAdapter
            }
        }.start()
    }


    /**
     * 核心：唤起系统选择器打开文件
     * @param filePath 文件完整路径
     */
    private fun openFileWithSystemChooser(filePath: String) {
        val context = requireContext()
        val file = File(filePath)
        // 1. 校验文件是否存在
        if (!file.exists()) {
            return
        }

        // 2. 获取文件MIME类型
        val mimeType = getMimeType(filePath) ?: "*/*"

        // 3. 创建Intent（区分Android版本，适配FileProvider）
        val intent = Intent(Intent.ACTION_VIEW).apply {
            val fileUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                // Android 7.0+ 需用FileProvider获取Uri
                FileProvider.getUriForFile(
                    context,
                    "com.soordinary.transfer.fileprovider",
                    file
                )
            } else {
                // 低版本直接用文件Uri
                Uri.fromFile(file)
            }

            setDataAndType(fileUri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) // 授予读取权限
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        // 4. 唤起系统选择器
        try {
            val chooser = Intent.createChooser(intent, "选择应用打开文件")
            context.startActivity(chooser)
        } catch (e: Exception) {
            // 无应用可打开时的容错
            e.printStackTrace()
            // 可选：提示"暂无应用可打开该文件"
        }
    }

    /**
     * 获取文件MIME类型
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
     * 避免内存泄漏，销毁时清空绑定
     */
    override fun onDestroyView() {
        super.onDestroyView()
        // 清空RecyclerView的Adapter引用
        binding.folderList.adapter = null
    }
}