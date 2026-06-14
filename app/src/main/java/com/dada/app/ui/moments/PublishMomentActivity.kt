package com.dada.app.ui.moments

import android.Manifest
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.view.View
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.getSystemService
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.dada.core.ui.base.BaseActivity
import com.dada.app.databinding.ActivityPublishMomentBinding
import com.dada.core.imageloader.ImageLoader
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject
import kotlin.coroutines.resume

/**
 * 发布朋友圈
 *
 * 流程:
 *  1. 输入文字
 *  2. 选图(最多 9 张)—— ViewModel 内部并行上传
 *  3. 点「发表」 -> 等待所有图片上传完 -> 调发布接口 -> 关闭页面
 *
 * 当前未实现:选视频(保留 UI)
 */
@AndroidEntryPoint
class PublishMomentActivity : BaseActivity<ActivityPublishMomentBinding>() {

    private val viewModel: PublishMomentViewModel by viewModels()
    private lateinit var imageAdapter: PublishImageAdapter

    @Inject lateinit var imageLoader: ImageLoader
    private var selectedLocation: String? = null

    private val pickImagesLauncher = registerForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(MAX_IMAGES)
    ) { uris ->
        if (uris.isNotEmpty()) viewModel.addImages(uris)
    }

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) fetchCity()
        else Toast.makeText(this, "需要定位权限才能获取位置", Toast.LENGTH_SHORT).show()
    }

    override fun inflateBinding() = ActivityPublishMomentBinding.inflate(layoutInflater)

    override fun initView() {
        setupToolbar()
        setupImageGrid()
    }

    override fun initData() {
        bindBaseViewModel(viewModel)
        observe(viewModel.uiState) { state ->
            imageAdapter.submitImages(state.imageUris)
            binding.loadingMask.visibility = if (state.isPublishing) View.VISIBLE else View.GONE
            if (state.published) {
                Toast.makeText(this, "发布成功", Toast.LENGTH_SHORT).show()
                setResult(RESULT_OK)
                finish()
            }
        }
    }

    private fun setupToolbar() {
        binding.btnCancel.setOnClickListener { finish() }
        binding.btnPublish.setOnClickListener {
            val content = binding.etContent.text.toString().trim()
            viewModel.publish(content, selectedLocation)
        }
        binding.btnPickLocation.setOnClickListener {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    private fun setupImageGrid() {
        imageAdapter = PublishImageAdapter(
            maxCount = MAX_IMAGES,
            imageLoader = imageLoader,
            onPickClick = {
                pickImagesLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            onRemoveClick = viewModel::removeImage,
        )
        binding.rvImages.apply {
            layoutManager = GridLayoutManager(this@PublishMomentActivity, 3)
            adapter = imageAdapter
        }
    }

    // ============================== 定位 ==============================

    @Suppress("MissingPermission")
    private fun fetchCity() {
        val lm = getSystemService<LocationManager>() ?: return
        val location = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)

        if (location == null) {
            Toast.makeText(this, "获取位置失败，请稍后重试", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            val city = runCatching { resolveCity(location) }.getOrNull()
            when {
                city.isNullOrBlank() -> Toast.makeText(this@PublishMomentActivity,
                    "无法识别城市名称", Toast.LENGTH_SHORT).show()
                else -> {
                    selectedLocation = city
                    binding.tvLocation.text = city
                }
            }
        }
    }

    /** Geocoder 是阻塞 IO;33+ 提供异步 API,旧版本回退到 [Dispatchers.IO] 上的同步调用。 */
    private suspend fun resolveCity(location: Location): String? {
        val geocoder = Geocoder(this, Locale.getDefault())
        val address = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            suspendCancellableCoroutine { cont ->
                geocoder.getFromLocation(location.latitude, location.longitude, 1) { list ->
                    cont.resume(list.firstOrNull())
                }
            }
        } else {
            withContext(Dispatchers.IO) {
                @Suppress("DEPRECATION")
                geocoder.getFromLocation(location.latitude, location.longitude, 1)?.firstOrNull()
            }
        } ?: return null
        return buildString {
            address.adminArea?.let(::append)
            address.locality?.let(::append)
        }.takeIf { it.isNotBlank() }
    }

    companion object {
        private const val MAX_IMAGES = 9
    }
}
