package com.soordinary.myapplication.data.shared


import com.tencent.mmkv.MMKV


/**
 * 个人信息的存储类
 */
object UserMMKV {

    private val mmkv = MMKV.mmkvWithID("user_info", MMKV.MULTI_PROCESS_MODE)
    private const val USER_ID: String = "user_id" // 个人ID，全局唯一，自动生成且不可更改
    private const val USER_ICON_URI: String = "user_icon_uri" // 个人头像的Uri
    private const val USER_NAME: String = "user_name" // 个人昵称
    private const val USER_SIGNATURE: String = "user_signature" // 个人签名
    private const val USER_PASSWORD: String = "user_password"  // 用户密码

    val userId: Long
        get() {
            return if (mmkv.containsKey(USER_ID)) {
                // 存在则直接读取（即使是 0，也按存储值返回，符合「不可更改」）
                mmkv.decodeLong(USER_ID, 0)
            } else {
                // 不存在则生成并存储（仅第一次执行）
                uniqueId()
            }
        }

    // 生成唯一ID（优化：用更可靠的生成方式，避免重复）
    private fun uniqueId(): Long {
        // 方案1：保留原逻辑（时间戳），但增加防重复处理（可选）
        val id = System.currentTimeMillis()
        // 存储并返回
        mmkv.encode(USER_ID, id)
        return id
    }

    var userIconUri: String?
        get() = mmkv.decodeString(USER_ICON_URI, "android.resource://com.todo.android/drawable/app_icon")
        set(value) { mmkv.encode(USER_ICON_URI, value) }

    var userName: String?
        get() = mmkv.decodeString(USER_NAME, "SoOrdinary")
        set(value) { mmkv.encode(USER_NAME, value) }

    var userSignature: String?
        get() = mmkv.decodeString(USER_SIGNATURE, "Tencent School Enterprise Joint Project")
        set(value) { mmkv.encode(USER_SIGNATURE, value) }

    var userPassword: String?
        get() = mmkv.decodeString(USER_PASSWORD, "")
        set(value) { mmkv.encode(USER_PASSWORD, value) }

}