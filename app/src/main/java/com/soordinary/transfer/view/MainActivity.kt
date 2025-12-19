package com.soordinary.transfer.view

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import androidx.activity.enableEdgeToEdge
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.NavigationUI
import com.soordinary.transfer.BaseActivity
import com.soordinary.transfer.R
import com.soordinary.transfer.databinding.ActivityMainBinding
import com.soordinary.transfer.databinding.NavSideHeaderBinding
import com.soordinary.transfer.utils.SystemUtil
import com.soordinary.transfer.view.folder.FolderFragment

class MainActivity : BaseActivity<ActivityMainBinding>() {
    // 定义日志标签，方便过滤日志
    private val TAG = "MainActivity"

    companion object {
        // 静态打开方法，指明打开该类需要哪些参数
        fun actionStart(context: Context) {
            val intent = Intent(context, MainActivity::class.java).apply {
                // putExtra()
            }
            context.startActivity(intent)
        }
    }

    override fun getBindingInflate() = ActivityMainBinding.inflate(layoutInflater)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        binding.initView(this)
        // 初始化各种点击事件
        binding.initClick()
    }


    private fun ActivityMainBinding.initView(context: Context) {
        window.statusBarColor = ContextCompat.getColor(context, R.color.nav_background)
        window.navigationBarColor = ContextCompat.getColor(context, R.color.nav_bottom)

        // 设置样式[在这里面才能获取到系统UI参数，异步执行的]
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            // 侧边栏UI修正高度
            binding.navSide.getHeaderView(0).setPadding(0, systemBars.top, 0, 0)
            insets
        }

        // 侧边栏UI修正,宽度变为2/3
        val params: ViewGroup.LayoutParams = navSide.layoutParams
        params.width = (SystemUtil.screenWidth * 0.66).toInt()
        navSide.layoutParams = params

        // 获取一些必要的组件实例
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val sideHeaderBinding = NavSideHeaderBinding.bind(navSide.getHeaderView(0))
        val navController = navHostFragment.findNavController()
        NavigationUI.setupWithNavController(binding.navBottom, navController)
    }


    private fun ActivityMainBinding.initClick() {

        // 点击侧边栏的某一菜单后回调Fragment自定义好的逻辑，然后关闭侧边栏并选中该菜单
        navSide.setNavigationItemSelectedListener {
            val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
            val fragment = navHostFragment.childFragmentManager.fragments[0]
            // todo
            // if (fragment is TaskFragment) fragment.ListenTaskItemClick().onClickMenuItem(it)
            layoutMain.closeDrawers()
            true
        }
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.findNavController()
        binding.navBottom. setOnItemSelectedListener { item ->
            when (item.itemId) {
                // 如果是 "nav_add" 按钮，根据当前fragment设置逻辑，同时返回else表示add按钮不被选中
                R.id.nav_add -> {
                    when (val fragment = navHostFragment.childFragmentManager.fragments[0]) {
                        is FolderFragment -> fragment.onSelectFileClick()
                        else -> {}
                    }
                    false
                }
                // 其他按钮默认导航
                else -> NavigationUI.onNavDestinationSelected(item, navController)
            }
        }
    }


}
