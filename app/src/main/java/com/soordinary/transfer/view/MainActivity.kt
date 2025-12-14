package com.soordinary.transfer.view

import android.os.Bundle
import android.util.Log
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.soordinary.transfer.BaseActivity
import com.soordinary.transfer.R
import com.soordinary.transfer.databinding.ActivityMainBinding

class MainActivity : BaseActivity<ActivityMainBinding>() {
    // 定义日志标签，方便过滤日志
    private val TAG = "MainActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate() 被调用 - Activity正在创建")

        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }


    }

    override fun getBindingInflate() = ActivityMainBinding.inflate(layoutInflater)

}
