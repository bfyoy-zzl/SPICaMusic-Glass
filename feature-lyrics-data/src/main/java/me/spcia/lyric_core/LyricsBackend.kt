package me.spcia.lyric_core

/**
 * 在线歌词后端配置。
 *
 * 默认指向原作者搭建的公共歌词接口（个人自用，未做分发）。该服务端随时可能下线，
 * 且是明文 http，因此 Manifest 保留了 `usesCleartextTraffic="true"`。
 *
 * 想关闭在线歌词：把 [BASE_URL] 置空。此时：
 * 1. [ApiClient] 在入口处直接返回空列表，不会发起任何网络请求；
 * 2. 歌词来源面板不显示在线分区。
 * 播放器只用内嵌歌词和本地歌词文件即可正常工作。
 *
 * 想换成自己的服务端：填 https 地址即可（末尾斜杠会自动补齐），
 * 并把 Manifest 里的明文放行删掉。
 */
object LyricsBackend {
    /** 形如 "https://lyrics.example.com/api/v1/lyrics/"，留空表示关闭在线歌词。 */
    const val BASE_URL: String = "http://api.spica27.site/api/v1/lyrics/"

    val isEnabled: Boolean get() = BASE_URL.isNotBlank()

    /**
     * Retrofit 要求 baseUrl 非空且以 / 结尾。未启用时给一个不会被访问的占位地址，
     * 真正的拦截点在 [ApiClient.searchAllLyrics]。
     */
    val retrofitBaseUrl: String
        get() = if (isEnabled) BASE_URL.trimEnd('/') + "/" else "https://localhost/"
}
