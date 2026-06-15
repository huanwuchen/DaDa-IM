package com.dada.app.widget.video

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.dada.app.R

/**
 * 视频播放器使用示例 Activity
 * 展示如何在 Activity 中使用视频播放组件
 */
class VideoPlayerDemoActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ChatVideoAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video_player_demo)

        setupRecyclerView()
        loadDemoData()
    }

    private fun setupRecyclerView() {
        recyclerView = findViewById(R.id.recycler_view)
        adapter = ChatVideoAdapter()

        recyclerView.apply {
            layoutManager = LinearLayoutManager(this@VideoPlayerDemoActivity)
            adapter = this@VideoPlayerDemoActivity.adapter
        }
    }

    private fun loadDemoData() {
        // 示例数据
        val messages = mutableListOf<ChatMessage>()

        // 添加文本消息
        messages.add(
            ChatMessage(
                id = "1",
                type = MessageType.TEXT,
                content = "欢迎使用视频播放器组件！"
            )
        )

        // 添加视频消息（示例 URL，实际使用时替换为真实地址）
        messages.add(
            ChatMessage(
                id = "2",
                type = MessageType.VIDEO,
                videoItem = VideoItem(
                    url = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
                    coverUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/images/BigBuckBunny.jpg"
                )
            )
        )

        messages.add(
            ChatMessage(
                id = "3",
                type = MessageType.TEXT,
                content = "点击视频即可播放"
            )
        )

        messages.add(
            ChatMessage(
                id = "4",
                type = MessageType.VIDEO,
                videoItem = VideoItem(
                    url = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4",
                    coverUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/images/ElephantsDream.jpg"
                )
            )
        )

        messages.add(
            ChatMessage(
                id = "5",
                type = MessageType.TEXT,
                content = "支持滚动切换视频"
            )
        )

        adapter.submitList(messages)
    }

    override fun onPause() {
        super.onPause()
        // 页面不可见时暂停播放
        VideoPlayerManager.getInstance(this).pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        // 页面销毁时停止播放
        VideoPlayerManager.getInstance(this).stop()
    }
}
