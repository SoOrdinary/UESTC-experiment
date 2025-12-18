package com.soordinary.transfer.view.folder

/**
 * 适配数据备份软件实验的文件实体类
 * 覆盖：基础备份/还原、软/硬链接、元数据、特殊文件、筛选等所有实验要求
 */
data class FileEntity(
    // ===================== 核心基础字段（满足40分基本要求） =====================
    /** 文件类型 */
    val type:FileType,
    /** 文件扩展名（用于类型筛选，文件夹/特殊文件为空） */
    val extension: String = "",
    /** 文件/文件夹名称 */
    val name: String,
    /** 父目录完整路径（用于目录树遍历、返回上级） */
    val parentPath: String,
    /** 文件大小（字节），文件夹大小可累计子文件计算 */
    val size: Long = 0,
    /** 最后修改时间戳（毫秒） */
    val lastModified: Long = 0,
) {
    // 特殊文件类型枚举
    enum class FileType {
        PARENTDIRECTORY, // 上级目录
        NORMAL,       // 普通文件
        DIRECTORY,    // 文件夹
        SYMBOLIC_LINK,// 软链接
        HARD_LINK,    // 硬链接
        PIPE,         // 管道文件
        DEVICE,       // 设备文件
        SOCKET        // 套接字文件
    }
}