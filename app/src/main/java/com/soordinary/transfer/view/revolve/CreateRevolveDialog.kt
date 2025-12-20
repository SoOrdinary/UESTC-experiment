package com.soordinary.transfer.view.revolve

import android.content.Context
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.soordinary.transfer.R
import com.soordinary.transfer.data.room.entity.RevolveEntity

/**
 * 重构后的弹窗：创建RevolveEntity实体并通过回调传递
 * @param context 上下文
 * @param onTaskCreated 任务创建成功的回调（传递创建的实体）
 */
class CreateRevolveDialog(
    private val context: Context,
    private val onTaskCreated: (String,RevolveEntity.TaskType,String,String) -> Unit // 新增回调：传递创建的实体
) : BottomSheetDialog(context) {

    // 控件引用
    private lateinit var etTaskName: EditText
    private lateinit var spTaskType: Spinner
    private lateinit var btnConfirm: Button

    init {
        initView()
        initSpinner()
        initClickListeners()
    }

    private fun initView() {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_revolve_create, null)
        setContentView(view)

        // 绑定控件
        etTaskName = view.findViewById(R.id.et_task_name)
        spTaskType = view.findViewById(R.id.sp_task_type)
        btnConfirm = view.findViewById(R.id.btn_confirm)
    }

    /**
     * 初始化任务类型下拉选择器
     */
    private fun initSpinner() {
        // 获取所有任务类型的【中文名称】
        val taskTypes = RevolveEntity.TaskType.values().map { it.chineseName }
        // 创建适配器
        val adapter = ArrayAdapter(
            context,
            android.R.layout.simple_spinner_item,
            taskTypes
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        spTaskType.adapter = adapter
    }

    /**
     * 初始化点击事件
     */
    private fun initClickListeners() {
        btnConfirm.setOnClickListener {
            createTask()
        }
    }

    /**
     * 校验输入并创建任务实体
     */
    private fun createTask() {
        // 1. 获取输入内容
        val taskName = etTaskName.text.toString().trim()
        val selectedChineseName = spTaskType.selectedItem?.toString() ?: ""

        // 2. 输入校验
        if (taskName.isEmpty()) {
            etTaskName.error = "任务名称不能为空"
            return
        }

        // 3. 转换任务类型（带容错）
        val taskType = RevolveEntity.TaskType.values().find { it.chineseName == selectedChineseName }
            ?: RevolveEntity.TaskType.PACK

        // 5. 通过回调传递实体并关闭弹窗
        onTaskCreated.invoke(taskName,taskType,"","value")
        dismiss()
    }
}