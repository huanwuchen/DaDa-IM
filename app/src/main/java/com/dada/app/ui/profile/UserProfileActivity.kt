package com.dada.app.ui.profile

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.dada.app.R
import com.dada.app.databinding.ActivityUserProfileBinding
import com.dada.core.imageloader.ImageLoader
import com.dada.core.imageloader.loadImage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@AndroidEntryPoint
class UserProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityUserProfileBinding
    private val viewModel: UserProfileViewModel by viewModels()

    @Inject lateinit var imageLoader: ImageLoader

    private var currentUserId: Long = 0
    private var isEditable: Boolean = false
    private var tempPhotoFile: File? = null

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { handleImageSelected(it) }
    }

    private val takePictureLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            tempPhotoFile?.let { handleImageSelected(Uri.fromFile(it)) }
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) showImagePickerDialog()
        else Toast.makeText(this, "需要相机权限", Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUserProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        currentUserId = intent.getLongExtra(EXTRA_USER_ID, 0)
        isEditable = intent.getBooleanExtra(EXTRA_EDITABLE, false)

        if (currentUserId == 0L) {
            Toast.makeText(this, "用户ID错误", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupViews()
        observeViewModel()
        viewModel.loadUserProfile(currentUserId)
    }

    private fun setupViews() {
        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.ivAvatar.setOnClickListener {
            if (isEditable) showImagePickerDialog()
        }
        binding.llUsername.setOnClickListener {
            if (isEditable) showEditUsernameDialog()
        }
        binding.ivAvatarEdit.visibility = if (isEditable) View.VISIBLE else View.GONE
        binding.ivUsernameArrow.visibility = if (isEditable) View.VISIBLE else View.GONE
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.progressBar.visibility = if (state.isLoading) View.VISIBLE else View.GONE

                    state.profile?.let { profile ->
                        binding.tvUsername.text = profile.username
                        binding.tvUserId.text = "ID: ${profile.id}"
                        if (!profile.avatar.isNullOrEmpty()) {
                            binding.ivAvatar.loadImage(profile.avatar, imageLoader) {
                                placeholder = R.drawable.ic_default_avatar
                                error = R.drawable.ic_default_avatar
                                asCircle = true
                            }
                        } else {
                            binding.ivAvatar.setImageResource(R.drawable.ic_default_avatar)
                        }
                    }

                    state.error?.let {
                        Toast.makeText(this@UserProfileActivity, it, Toast.LENGTH_SHORT).show()
                        viewModel.clearEvent()
                    }
                    state.uploadSuccess?.let {
                        Toast.makeText(this@UserProfileActivity, "头像上传成功", Toast.LENGTH_SHORT).show()
                        viewModel.clearEvent()
                    }
                    state.updateSuccess?.let {
                        Toast.makeText(this@UserProfileActivity, "修改成功", Toast.LENGTH_SHORT).show()
                        viewModel.clearEvent()
                    }
                }
            }
        }
    }

    private fun showImagePickerDialog() {
        val options = arrayOf("拍照", "从相册选择")
        AlertDialog.Builder(this)
            .setTitle("选择头像")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> checkCameraPermissionAndTakePicture()
                    1 -> pickImageLauncher.launch("image/*")
                }
            }
            .show()
    }

    private fun checkCameraPermissionAndTakePicture() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            takePicture()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun takePicture() {
        tempPhotoFile = File(cacheDir, "temp_avatar_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", tempPhotoFile!!)
        takePictureLauncher.launch(uri)
    }

    private fun handleImageSelected(uri: Uri) {
        try {
            val inputStream = contentResolver.openInputStream(uri)
            val tempFile = File(cacheDir, "avatar_${System.currentTimeMillis()}.jpg")
            tempFile.outputStream().use { inputStream?.copyTo(it) }
            viewModel.uploadAvatar(currentUserId, tempFile)
        } catch (e: Exception) {
            Toast.makeText(this, "图片处理失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showEditUsernameDialog() {
        val currentUsername = binding.tvUsername.text.toString()
        val editText = android.widget.EditText(this).apply {
            setText(currentUsername)
            setSelection(currentUsername.length)
        }
        AlertDialog.Builder(this)
            .setTitle("修改昵称")
            .setView(editText)
            .setPositiveButton("确定") { _, _ ->
                val newUsername = editText.text.toString().trim()
                if (newUsername.isNotEmpty()) viewModel.updateUsername(currentUserId, newUsername)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    companion object {
        const val EXTRA_USER_ID = "extra_user_id"
        const val EXTRA_EDITABLE = "extra_editable"

        fun start(context: Context, userId: Long, editable: Boolean = false) {
            val intent = Intent(context, UserProfileActivity::class.java).apply {
                putExtra(EXTRA_USER_ID, userId)
                putExtra(EXTRA_EDITABLE, editable)
            }
            context.startActivity(intent)
        }
    }
}
