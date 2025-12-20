package com.soordinary.transfer.view.revolve

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.soordinary.transfer.R
import com.soordinary.transfer.data.room.entity.RevolveEntity
import com.soordinary.transfer.databinding.FragmentRevolveItemBinding
import java.text.SimpleDateFormat
import java.util.Locale

class RevolveAdapter(
    private var revolveList: List<RevolveEntity>,
) : RecyclerView.Adapter<RevolveAdapter.BaseViewHolder>() {

    // 定义点击/长按回调接口
    private var onItemClickListener: ((RevolveEntity) -> Unit)? = null
    private var onItemLongClickListener: ((RevolveEntity) -> Boolean)? = null

    // 更新数据源
    fun updateData(newList: List<RevolveEntity>) {
        this.revolveList = newList
        notifyDataSetChanged()
    }

    // 设置点击回调
    fun setOnItemClickListener(listener: (RevolveEntity) -> Unit) {
        this.onItemClickListener = listener
    }

    // 设置长按回调
    fun setOnItemLongClickListener(listener: (RevolveEntity) -> Boolean) {
        this.onItemLongClickListener = listener
    }

    // 内部基类ViewHolder
    abstract inner class BaseViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        abstract fun bind(entity: RevolveEntity)
    }

    // 创建ViewHolder（绑定CardView布局）
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BaseViewHolder {
        val view: View = LayoutInflater.from(parent.context)
            .inflate(R.layout.fragment_revolve_item, parent, false)
        return RevolveViewHolder(view)
    }

    override fun getItemCount() = revolveList.size

    override fun onBindViewHolder(holder: BaseViewHolder, position: Int) {
        holder.bind(revolveList[position])
    }

    // 具体的ViewHolder实现（对应Grid风格的CardView布局）
    inner class RevolveViewHolder(view: View) : BaseViewHolder(view) {
        // 绑定布局（使用ViewBinding）
        private val binding = FragmentRevolveItemBinding.bind(view)

        @SuppressLint("SetTextI18n")
        override fun bind(entity: RevolveEntity) {
            with(binding) {
                // 1. 封面图片（使用Glide加载Uri，添加占位/错误图）
                Glide.with(itemView.context)
                    .load(entity.coverUri)
                    .placeholder(R.drawable.app_icon) // 加载中占位
                    .error(R.drawable.app_icon)       // 加载失败占位
                    .into(taskGridCoverImage)

                // 2. 标题（中转计划名称）
                taskGridTitle.text = entity.name

                // 3. 中转类型（如时间）
                val taskTypeDesc = entity.type.chineseName
                // 拼接类型和value（value如果是时间可格式化，这里保留原逻辑）
                taskGridDueDate.text = taskTypeDesc

                // ========== 点击事件 ==========
                // 条目点击
                itemView.setOnClickListener {
                    onItemClickListener?.invoke(entity)
                }

                // 条目长按
                itemView.setOnLongClickListener {
                    onItemLongClickListener?.invoke(entity) ?: false
                }
            }
        }

        /**
         * 可选：格式化时间的工具方法（仿照FolderAdapter）
         */
        private fun formatTime(timestamp: Long): String {
            return if (timestamp == 0L) {
                ""
            } else {
                SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA)
                    .format(java.util.Date(timestamp))
            }
        }
    }
}