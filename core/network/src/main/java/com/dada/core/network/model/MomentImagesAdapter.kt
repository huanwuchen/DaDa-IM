package com.dada.core.network.model

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import java.lang.reflect.Type

/**
 * 灵活解析「图片 URL 列表」
 *
 * 后端在不同接口返回的 images 字段格式不一致：
 *  - feed/详情：JSON 数组   `"images": ["/uploads/a.jpg", "/uploads/b.jpg"]`
 *  - 发布响应：JSON 字符串  `"images": "[\"/uploads/a.jpg\"]"`
 *  - 空内容：null 或 []
 *
 * 此适配器统一吃下三种情况，最终都返回 [List]<[String]>，简化 UI 处理。
 */
class MomentImagesAdapter : JsonDeserializer<List<String>> {
    override fun deserialize(
        json: JsonElement?,
        typeOfT: Type?,
        context: JsonDeserializationContext?,
    ): List<String> {
        if (json == null || json.isJsonNull) return emptyList()

        // 1. 直接是 JSON 数组
        if (json.isJsonArray) {
            return json.asJsonArray.mapNotNull { it.takeUnless { it.isJsonNull }?.asString }
        }

        // 2. 是字符串：可能是 JSON 编码的数组，或单个 URL
        if (json.isJsonPrimitive && json.asJsonPrimitive.isString) {
            val s = json.asString.trim()
            if (s.isEmpty()) return emptyList()
            // 试着按 JSON 数组解析
            if (s.startsWith("[")) {
                return runCatching {
                    JsonParser.parseString(s).asJsonArray
                        .mapNotNull { it.takeUnless { it.isJsonNull }?.asString }
                }.getOrElse { listOf(s) }
            }
            // 单个字符串当作单个 URL
            return listOf(s)
        }

        return emptyList()
    }
}
