package com.soordinary.myapplication

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivityB : AppCompatActivity() {
    private val TAG = "LifecycleDemoB"
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main_b)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        val intent= Intent(this,MainActivity::class.java)
        //startActivity(intent)
    }

    override fun onStart() {
        super.onStart()
        Log.d(TAG, "onStart() 被调用 - Activity正在启动，即将可见")
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume() 被调用 - Activity已可见，可与用户交互")
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "onPause() 被调用 - Activity即将暂停，失去焦点")
    }

    override fun onStop() {
        super.onStop()
        Log.d(TAG, "onStop() 被调用 - Activity已停止，不可见")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy() 被调用 - Activity即将销毁")
    }

    override fun onRestart() {
        super.onRestart()
        Log.d(TAG, "onRestart() 被调用 - Activity即将重新启动")
    }
}