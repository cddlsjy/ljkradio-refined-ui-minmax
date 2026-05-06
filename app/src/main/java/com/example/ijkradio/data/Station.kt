package com.example.ijkradio.data

/**
 * 电台数据类
 * @param name 电台名称
 * @param url 电台流地址
 * @param id 电台唯一标识符（可选）
 * @param description 电台描述（可选）
 * @param logoUrl 电台图标URL（可选）
 */
data class Station(
    val name: String,
    val url: String,
    val id: String = java.util.UUID.randomUUID().toString(),
    val description: String = "",
    val logoUrl: String = ""
) {
    /**
     * 验证电台URL是否有效
     */
    fun isValid(): Boolean {
        return name.isNotBlank() && url.isNotBlank() &&
               (url.startsWith("http://") || url.startsWith("https://"))
    }

    /**
     * 电台显示名称
     */
    fun getDisplayName(): String {
        return if (description.isNotBlank()) {
            "$name - $description"
        } else {
            name
        }
    }

    override fun toString(): String {
        return "Station(name='$name', url='$url', id='$id')"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as Station
        return id == other.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }
}
