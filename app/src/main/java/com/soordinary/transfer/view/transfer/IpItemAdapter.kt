// IpItemAdapter.kt
package com.soordinary.transfer.view.transfer

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.soordinary.transfer.databinding.FragmentTransferBinding
import com.soordinary.transfer.databinding.FragmentTransferItemBinding

/**
 * IP列表的RecyclerView适配器
 * @param onItemClick item点击事件的回调
 */
class IpItemAdapter(
    private val onItemClick: (IpItem) -> Unit
) : ListAdapter<IpItem, IpItemAdapter.IpItemViewHolder>(DiffCallback()) {

    // ViewHolder类，绑定IP列表项布局
    inner class IpItemViewHolder(private val binding: FragmentTransferItemBinding) :
        RecyclerView.ViewHolder(binding.root) {

        init {
            // 为整个Item设置点击事件
            binding.root.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val item = getItem(position)
                    onItemClick(item) // 触发外部点击回调
                }
            }
        }

        // 绑定数据到UI控件
        fun bind(item: IpItem) {
            binding.apply {
                ipString.text = item.ip       // 显示IP地址
                password.text = item.password // 显示密码/备注
                // 可选：动态设置图标
                // ipIcon.setImageResource(R.drawable.ic_ip_icon)
            }
        }
    }

    // 创建ViewHolder（绑定布局）
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): IpItemViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = FragmentTransferItemBinding.inflate(inflater, parent, false)
        return IpItemViewHolder(binding)
    }

    // 绑定数据到ViewHolder
    override fun onBindViewHolder(holder: IpItemViewHolder, position: Int) {
        val currentItem = getItem(position)
        holder.bind(currentItem)
    }

    // DiffCallback：高效对比列表数据，避免重复刷新
    class DiffCallback : DiffUtil.ItemCallback<IpItem>() {
        override fun areItemsTheSame(oldItem: IpItem, newItem: IpItem): Boolean {
            // 以IP作为唯一标识判断是否是同一个Item
            return oldItem.ip == newItem.ip
        }

        override fun areContentsTheSame(oldItem: IpItem, newItem: IpItem): Boolean {
            // 判断Item内容是否完全一致
            return oldItem == newItem
        }
    }
}