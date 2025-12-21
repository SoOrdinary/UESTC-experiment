package com.soordinary.transfer.view.folder

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.webkit.MimeTypeMap
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.FileProvider
import androidx.core.view.GravityCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.DownsampleStrategy
import com.soordinary.transfer.R
import com.soordinary.transfer.data.room.database.RevolveDatabase
import com.soordinary.transfer.databinding.FragmentFolderBinding
import com.soordinary.transfer.repository.RevolveRepository
import com.soordinary.transfer.view.MainActivity
import com.soordinary.transfer.view.revolve.RevolveViewModel
import com.soordinary.transfer.view.user.UserViewModel
import java.io.File

class FolderFragment : Fragment(R.layout.fragment_folder)  {

    private lateinit var binding: FragmentFolderBinding
    private lateinit var folderAdapter: FolderAdapter
    private lateinit var rootPath : String
    private val userViewModel: UserViewModel by activityViewModels()
    private lateinit var currentPath : String
    private lateinit var filePickerLauncher: ActivityResultLauncher<Array<String>>
    // 声明Dialog实例（可选，按需创建）
    private var createFolderDialog: CreateFolderDialog? = null
    // 共享ViewModel（Activity作用域）
    private val revolveViewModel: RevolveViewModel by activityViewModels {
        object : androidx.lifecycle.ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                // 初始化数据库和仓库（确保ApplicationContext，避免内存泄漏）
                val database = RevolveDatabase.getDatabase(requireContext().applicationContext)
                val repository = RevolveRepository(database.revolveDao())
                return RevolveViewModel(repository) as T
            }
        }
    }

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

        refresh.setOnClickListener {
            currentPath =  "/"
            updateFilePathDisplay()
            loadFolderList()
        }

        folderAdapter = FolderAdapter(
            fragment = this@FolderFragment,
            folderList = emptyList(),
            onDirClick = { newPath ->
                currentPath = FolderTreeLoader.getRelativePath(newPath)
                updateFilePathDisplay()
                loadFolderList() // 进入子文件夹时加载完整列表
            },
            onFileClick = { openFileWithSystemChooser(it) },
            onLongClick = {
                // 弹出编辑弹窗
                val editDialog = EditDialog(
                    requireContext(),
                    it,
                    {
                        loadFolderList()
                    },
                    revolveViewModel
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

        userViewModel.getIconUriLiveData().observe(this@FolderFragment) {
            Glide.with(iconP.context)
                .load(it)  // 图片的 URL
                .downsample(DownsampleStrategy.CENTER_INSIDE) // 根据目标区域缩放图片
                .placeholder(R.drawable.app_icon)  // 占位图
                .into(iconP)
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
     * 加载文件列表（完整列表，无筛选）
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
     * 根据文件名筛选文件（直接读取文件系统，无状态变量）
     * @param keyword 搜索关键词（已去除首尾空格）
     */
    fun searchByName(keyword: String) {
        // 开启子线程读取文件系统，避免阻塞UI
        Thread {
            // 1. 读取当前路径下的所有文件
            val allFiles = FolderTreeLoader.loadFolderList("$rootPath$currentPath")
            // 2. 根据关键词筛选
            val filteredFiles = if (keyword.isBlank()) {
                // 关键词为空，返回全部文件
                allFiles
            } else {
                // 过滤：名称包含关键词（忽略大小写）
                allFiles.filter { file ->
                    file.name.lowercase().contains(keyword.lowercase())
                }
            }
            // 3. 在UI线程更新列表
            activity?.runOnUiThread {
                folderAdapter.updateData(filteredFiles)
            }
        }.start()
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