package com.soordinary.transfer.view.folder

import android.os.Bundle
import android.view.View
import androidx.core.view.GravityCompat
import androidx.fragment.app.Fragment
import com.soordinary.transfer.R
import com.soordinary.transfer.databinding.FragmentFolderBinding
import com.soordinary.transfer.view.MainActivity

class FolderFragment : Fragment(R.layout.fragment_folder)  {

    private lateinit var binding: FragmentFolderBinding

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding = FragmentFolderBinding.bind(view)

        binding.initClick()
    }

    private fun FragmentFolderBinding.initClick() {

        // 点击头像后打开侧边栏[requireActivity不会有空的情况]
        iconP.setOnClickListener {
            (requireActivity() as MainActivity).binding.layoutMain.openDrawer(GravityCompat.START)
        }
    }
}