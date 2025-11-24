package com.soordinary.myapplication.data.network.file

interface DownloadListener {

    fun onProgress(progress: Int)

    fun onSuccess()

    fun onFailed()

}