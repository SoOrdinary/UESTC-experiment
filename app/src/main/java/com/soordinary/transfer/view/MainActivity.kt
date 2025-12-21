package com.soordinary.transfer.view

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.NavigationUI
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.DownsampleStrategy
import com.soordinary.transfer.BaseActivity
import com.soordinary.transfer.R
import com.soordinary.transfer.databinding.ActivityMainBinding
import com.soordinary.transfer.databinding.NavSideHeaderBinding
import com.soordinary.transfer.utils.SystemUtil
import com.soordinary.transfer.view.folder.FolderFragment
import com.soordinary.transfer.view.revolve.RevolveFragment
import com.soordinary.transfer.view.transfer.TransferFragment
import com.soordinary.transfer.view.user.UserViewModel

class MainActivity : BaseActivity<ActivityMainBinding>() {
    // 定义日志标签，方便过滤日志
    private val TAG = "MainActivity"

    private val userViewModel: UserViewModel by viewModels()

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
        val navController = navHostFragment.findNavController()
        NavigationUI.setupWithNavController(binding.navBottom, navController)

        with(binding.navSide){
            with(NavSideHeaderBinding.bind(getHeaderView(0))) {
                userViewModel.getIconUriLiveData().observe(this@MainActivity) {
                    Glide.with(icon.context)
                        .load(it)  // 图片的 URL
                        .downsample(DownsampleStrategy.CENTER_INSIDE) // 根据目标区域缩放图片
                        .placeholder(R.drawable.app_icon)  // 占位图
                        .into(icon)
                }
                userViewModel.getNameLiveData().observe(this@MainActivity) {
                    name.text = it
                }
                userViewModel.getSignatureLiveData().observe(this@MainActivity) {
                    signature.text = it
                }
            }
        }
    }


    private fun ActivityMainBinding.initClick() {

        val sideHeaderBinding = NavSideHeaderBinding.bind(navSide.getHeaderView(0))
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
                        is FolderFragment -> fragment.showCreateFolderDialog()
                        is RevolveFragment -> fragment.showCreateTaskDialog()
                        is TransferFragment -> fragment.showCreateIpDialog()
                        else -> {}
                    }
                    false
                }
                // 其他按钮默认导航
                else -> NavigationUI.onNavDestinationSelected(item, navController)
            }
        }

        sideHeaderBinding.searchTask.setOnEditorActionListener { v, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_SEARCH) {
                if (v.text.isNullOrEmpty()) return@setOnEditorActionListener true
                val fragment = navHostFragment.childFragmentManager.fragments[0]
                if (fragment is FolderFragment) fragment.searchByName(v.text.toString().trim())
                v.text = ""
                // 关闭软键盘
                val imm = v.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(v.windowToken, 0)
                // 关闭侧边栏
                layoutMain.closeDrawers()
            }
            true
        }
    }

}
