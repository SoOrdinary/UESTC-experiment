package com.soordinary.transfer.utils

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/**
 * App相关工具类，提供获取AppId、版本号、版本名的方法
 */
object AppUtil {

    /**
     * 获取App的唯一标识（ApplicationId/AppId）
     * @param context 上下文，建议用Application Context避免内存泄漏
     * @return AppId（如com.soordinary.transfer），获取失败返回空字符串
     */
    fun getAppId(context: Context): String {
        return try {
            val packageManager = context.packageManager
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // Android 13及以上，需要指定包名和标志位
                packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                // 低版本兼容
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(context.packageName, 0)
            }
            // 获取ApplicationId（即你在build.gradle里配置的applicationId）
            packageInfo.applicationInfo.packageName
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
            ""
        }
    }

    /**
     * 获取App版本号（数字，如1、2、3，用于应用市场更新判断）
     * @param context 上下文
     * @return 版本号，获取失败返回0
     */
    fun getVersionCode(context: Context): Int {
        return try {
            val packageManager = context.packageManager
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(context.packageName, 0)
            }
            // 低版本用versionCode，Android P及以上推荐用long型的versionCodeLong
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode
            }
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
            0
        }
    }

    /**
     * 获取App版本名（显示给用户看的，如1.0.0、2.1.1）
     * @param context 上下文
     * @return 版本名，获取失败返回空字符串
     */
    fun getVersionName(context: Context): String {
        return try {
            val packageManager = context.packageManager
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(context.packageName, 0)
            }
            packageInfo.versionName ?: ""
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
            ""
        }
    }
}