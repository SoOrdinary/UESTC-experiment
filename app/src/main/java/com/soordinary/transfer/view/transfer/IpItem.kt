// IpItem.kt
package com.soordinary.transfer.view.transfer

/**
 * IP列表项的数据模型
 * @param ip IP地址字符串
 * @param password 对应的密码/备注信息
 */
data class IpItem(
    val ip: String,
    val password: String
)