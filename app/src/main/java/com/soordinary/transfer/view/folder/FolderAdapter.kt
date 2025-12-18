package com.soordinary.transfer.view.folder

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.soordinary.transfer.R
import com.soordinary.transfer.databinding.FragmentFolderItemLinearBinding
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FolderAdapter(
    private val fragment: FolderFragment,
    private val folderList: List<FileEntity>,
    // 新增：目录跳转回调
    private val onDirClick: (String) -> Unit,
    // 普通文件点击回调（改为直接在Adapter内处理打开逻辑，无需透传）
    private val onFileClick: (String) -> Unit,
    // 新增：勾选状态回调（可选，用于备份选中）
    private val onFileChecked: (FileEntity, Boolean) -> Unit = { _, _ -> }
) : RecyclerView.Adapter<FolderAdapter.BaseViewHolder>() {

    // 内部基类，简化多种适配item与bind的书写
    abstract inner class BaseViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        abstract fun bind(file: FileEntity)
    }

    // 根据返回的布局ID判断加载哪种布局
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BaseViewHolder {
        val view: View = LayoutInflater.from(parent.context)
            .inflate(R.layout.fragment_folder_item_linear, parent, false)
        return LinearViewHolder(view)
    }

    override fun getItemCount() = folderList.size

    override fun onBindViewHolder(holder: BaseViewHolder, position: Int) {
        holder.bind(folderList[position])
    }

    // 线性列表（左侧图标+右侧文字）
    inner class LinearViewHolder(view: View) : BaseViewHolder(view) {
        private val binding = FragmentFolderItemLinearBinding.bind(view)

        @SuppressLint("SetTextI18n")
        override fun bind(file: FileEntity) {
            with(binding) {
                ivFileIcon.setImageResource(getFileIconRes(file.type, file.extension))
                when(file.type){
                    // 上级目录
                    FileEntity.FileType.PARENTDIRECTORY->{
                        tvFileName.text=".."
                        cbFileSelected.visibility=View.INVISIBLE
                        tvFileModifyTime.text = "返回上级目录"
                        itemView.setOnClickListener {
                            val currentPath = File(file.parentPath)
                            currentPath.parent?.let {
                                onDirClick(it)
                            }
                        }
                    }
                    // 文件夹
                    FileEntity.FileType.DIRECTORY->{
                        tvFileName.text=file.name
                        cbFileSelected.visibility=View.INVISIBLE
                        tvFileModifyTime.text = formatTime(file.lastModified)
                        itemView.setOnClickListener {
                            onDirClick(file.parentPath+file.name)
                        }
                    }
                    // 其他文件
                    else->{
                        tvFileName.text=file.name
                        // todo:判断是否已经压缩，决定是否勾选;以及压缩方法
                        cbFileSelected.isChecked = false
                        cbFileSelected.setOnCheckedChangeListener { _, isChecked ->
                            onFileChecked(file, isChecked)
                        }

                        tvFileModifyTime.text = formatTime(file.lastModified)
                        itemView.setOnClickListener {
                            onFileClick(file.parentPath+"/"+file.name)
                        }
                    }
                }
            }
        }


        /**
         * 根据文件类型/扩展名获取图标资源
         */
        private fun getFileIconRes(type: FileEntity.FileType, extension: String): Int {
            // 临时使用app_icon，后续替换为实际图标
            return R.drawable.app_icon
        }

        /**
         * 格式化文件大小（字节转KB/MB/GB）
         */
        private fun formatFileSize(size: Long): String {
            return when {
                size < 1024 -> "$size B"
                size < 1024 * 1024 -> String.format("%.1f KB", size / 1024.0)
                size < 1024 * 1024 * 1024 -> String.format("%.1f MB", size / (1024.0 * 1024))
                else -> String.format("%.1f GB", size / (1024.0 * 1024 * 1024))
            }
        }

        /**
         * 格式化时间戳为易读格式
         */
        private fun formatTime(timestamp: Long): String {
            return if (timestamp == 0L) {
                ""
            } else {
                SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA)
                    .format(Date(timestamp))
            }
        }
    }
}