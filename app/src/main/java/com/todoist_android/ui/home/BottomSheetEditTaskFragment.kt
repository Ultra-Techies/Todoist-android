package com.todoist_android.ui.home

import android.annotation.SuppressLint
import android.app.Dialog
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.View.GONE
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.snackbar.Snackbar
import com.todoist_android.R
import com.todoist_android.data.models.TodoModel
import com.todoist_android.data.network.APIResource
import com.todoist_android.data.repository.UserPreferences
import com.todoist_android.data.responses.TasksResponseItem
import com.todoist_android.databinding.FragmentBottomsheetEditTaskBinding
import com.todoist_android.ui.formartDate
import com.todoist_android.ui.hideKeyboard
import com.todoist_android.ui.pickDate
import com.todoist_android.ui.pickTime
import com.todoist_android.ui.popupMenuTwo
import com.todoist_android.ui.showKeyboard
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject


@AndroidEntryPoint
class BottomSheetEditTaskFragment : BottomSheetDialogFragment(), View.OnClickListener {
    @Inject
    lateinit var prefs: UserPreferences

    private lateinit var binding: FragmentBottomsheetEditTaskBinding
    private val viewModel: EditTaskBottomSheetViewModel by viewModels()

    private lateinit var todoModel: TasksResponseItem

    private var selectedReminderDate: String? = null
    private var selectedDueTime: String? = null
    private var selectedReminderTime: String? = null
    private var dueDate: String? = null
    private var newDueDate: String? = null
    private var taskReminder: String? = null
    private var taskStatus = "created"
    private var userId: String? = null

    companion object {
        fun newInstance(item: TasksResponseItem): BottomSheetEditTaskFragment {
            val bundle = Bundle()
            bundle.apply {
                putParcelable("data", item)
            }
            return BottomSheetEditTaskFragment().apply {
                arguments = bundle
            }
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        todoModel = arguments?.getParcelable("data")!!
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        if (dialog is BottomSheetDialog) {
            dialog.behavior.skipCollapsed = true
            dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        }
        return dialog
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentBottomsheetEditTaskBinding.inflate(layoutInflater, container, false)
        return binding.root
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setText()

        setUpGlobalVariables()

        getLoggedInUserId()

        setOnClickListeners()

    }

    private fun setOnClickListeners(){
        binding.ivEditReminder.setOnClickListener(this)
        binding.ivEditFlag.setOnClickListener(this)
        binding.tvEditDatePicker.setOnClickListener(this)
        binding.tvDeleteTask.setOnClickListener(this)
        binding.buttonEditTask.setOnClickListener(this)
        binding.tvCloseEditTask.setOnClickListener(this)
    }

    private fun editTask(editTasksRequest: TodoModel) {
        binding.root.hideKeyboard()
        binding.pbEditBottomSheet.visibility = View.VISIBLE
        Snackbar.make(dialog?.window!!.decorView, "Editing your task...", Snackbar.LENGTH_LONG)
            .show()
        viewLifecycleOwner.lifecycleScope.launchWhenStarted {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.editTasks(editTasksRequest).collect {
                    when (it) {
                        is APIResource.Success -> {
                            binding.pbEditBottomSheet.visibility = GONE
                            Snackbar.make(
                                dialog?.window!!.decorView,
                                "Task edited successfully",
                                Snackbar.LENGTH_SHORT
                            )
                                .show()
                            Log.d("task", editTasksRequest.toString())

                            viewLifecycleOwner.lifecycleScope.launch {
                                delay(1000)
                                dismiss()
                            }
                        }

                        is APIResource.Error -> {
                            binding.pbEditBottomSheet.visibility = GONE
                            Snackbar.make(
                                dialog?.window!!.decorView,
                                it.errorBody.toString(),
                                Snackbar.LENGTH_SHORT
                            )
                                .show()
                        }
                        is APIResource.Loading -> {
                            binding.pbEditBottomSheet.visibility = View.VISIBLE
                        }
                    }
                }
            }
        }
    }

    private fun deleteTask(deleteTaskRequest: TodoModel) {
        binding.root.hideKeyboard()
        binding.pbEditBottomSheet.visibility = View.VISIBLE
        Snackbar.make(dialog?.window!!.decorView, "Deleting your task...", Snackbar.LENGTH_LONG)
            .show()
        viewLifecycleOwner.lifecycleScope.launchWhenStarted {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.deleteTasks(deleteTaskRequest).collect {
                    when (it) {
                        is APIResource.Success -> {
                            Snackbar.make(
                                dialog?.window!!.decorView,
                                getString(R.string.task_deleted),
                                Snackbar.LENGTH_SHORT
                            )
                                .show()
                            viewLifecycleOwner.lifecycleScope.launch {
                                delay(1000)
                                dismiss()
                            }
                        }
                        is APIResource.Error -> {
                            Snackbar.make(
                                dialog?.window!!.decorView,
                                it.errorBody.toString(),
                                Snackbar.LENGTH_SHORT
                            )
                                .show()
                        }
                        is APIResource.Loading -> {

                        }
                    }


                }
            }
        }

    }

    private fun setText() {
        todoModel.apply {
            title?.let {
                binding.editTextEditTitle.setText(it)
            }

            description?.let {
                binding.editTextEditTask.setText(it)
            }

            due_date?.let {
                dueDate = formartDate(it, "yyyy/MM/dd HH:mm:ss", "dd/MM/yyyy h:mm a")
                binding.tvEditDatePicker.text = dueDate
            }

            reminder?.let {
                taskReminder = formartDate(it, "yyyy/MM/dd HH:mm:ss", "dd/MM/yyyy h:mm a")
            }

            status?.let {
                taskStatus = it
            }
        }

    }

    private fun setUpGlobalVariables() {
        binding.pbEditBottomSheet.visibility = GONE
        binding.editTextEditTask.showKeyboard()

    }


    private fun getLoggedInUserId() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                prefs.todoToken.collectLatest { todoId ->
                    todoId?.let {
                        userId = todoId
                    } ?: kotlin.run {
                        Toast.makeText(
                            requireActivity(),
                            getString(R.string.unable_to_find_user_id),
                            Toast.LENGTH_SHORT
                        ).show()
                    }

                }
            }
        }
    }


    override fun onClick(view: View?) {
        when (view) {
            binding.buttonEditTask -> submitEditedTask()
            binding.ivEditReminder -> selectNewReminderDate()
            binding.ivEditFlag -> selectNewTaskStatus()
            binding.tvEditDatePicker -> selectNewDueDate()
            binding.tvDeleteTask -> deleteTask()
            binding.tvCloseEditTask -> closeBottomSheet()
        }

    }


    private fun submitEditedTask() {
        // validate
        if (binding.editTextEditTitle.text.isNullOrEmpty()) {
            binding.editTextEditTask.error = getString(R.string.error_task_title)
            return
        }

        if (binding.editTextEditTask.text.isNullOrEmpty()) {
            binding.editTextEditTitle.error = getString(R.string.error_task_description)
            return
        }

        val editTasksRequest = TodoModel(
            id = todoModel.id,
            title = binding.editTextEditTitle.text.trim().toString(),
            description = binding.editTextEditTask.text.trim().toString(),
            due_date = dueDate,
            reminder = taskReminder,
            status = taskStatus
        )

        editTask(editTasksRequest)
    }

    private fun selectNewReminderDate() {
        pickDate(childFragmentManager) { selectedText, timeInMilliseconds ->
            selectedReminderDate = selectedText
            val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
            calendar.timeInMillis = timeInMilliseconds
            selectedReminderDate =
                SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()).format(calendar.time)

            pickTime(childFragmentManager) { selectTime ->

                selectedReminderTime = formartDate(selectTime, "h:mm a", "HH:mm:ss")
                Log.d("selectedReminderDate", selectedReminderTime.toString())
                taskReminder = "$selectedReminderDate $selectedReminderTime"
            }

        }
    }

    private fun selectNewTaskStatus() {
            popupMenuTwo(requireContext(), binding.ivEditFlag ){ statusSelected ->
                taskStatus = statusSelected

        }
    }

    @SuppressLint("SetTextI18n")
    private fun selectNewDueDate() {
        pickDate(childFragmentManager) { selectedText, timeInMilliseconds ->
            newDueDate = selectedText
            val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
            calendar.timeInMillis = timeInMilliseconds
            newDueDate =
                SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()).format(calendar.time)


            pickTime(childFragmentManager) { selectTime ->
                selectedDueTime = formartDate(selectTime, "h:mm a", "HH:mm:ss")
                binding.tvEditDatePicker.text = "$newDueDate $selectedDueTime"
                dueDate = "$newDueDate $selectedDueTime"
            }


        }

    }

    private fun deleteTask() {
        val deleteTaskRequest = TodoModel(
            id = todoModel.id,
            title = binding.editTextEditTitle.text.trim().toString(),
            description = binding.editTextEditTask.text.trim().toString(),
            due_date = dueDate ?: " ",
            reminder = taskReminder ?: " ",
            status = taskStatus
        )

        deleteTask(deleteTaskRequest)
    }

    private fun closeBottomSheet() {
        dismiss()
    }

    override fun onDestroy() {
        binding.root.hideKeyboard()
        super.onDestroy()
    }

}