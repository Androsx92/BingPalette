package app.bingpalette

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

object Bing {
    class Info(val urlBase: String, val title: String, val copyright: String)

    fun info(mkt: String): Info {
        val json = JSONObject(String(get("https://www.bing.com/HPImageArchive.aspx?format=js&idx=0&n=1&mkt=$mkt")))
        val img = json.getJSONArray("images").getJSONObject(0)
        return Info(img.getString("urlbase"), img.optString("title"), img.optString("copyright"))
    }

    /** size: UHD, 1920x1080, 1366x768, 800x480 ... */
    fun image(info: Info, size: String): ByteArray = get("https://www.bing.com${info.urlBase}_$size.jpg")

    fun get(url: String): ByteArray {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15000
        c.readTimeout = 30000
        try {
            if (c.responseCode != 200) throw RuntimeException("HTTP ${c.responseCode}")
            return c.inputStream.use { it.readBytes() }
        } finally {
            c.disconnect()
        }
    }
}

object Markets {
    val codes = listOf(
        "de-DE", "de-AT", "de-CH", "en-US", "en-GB", "en-CA", "en-AU", "en-IN", "en-IE", "en-NZ",
        "en-ZA", "fr-FR", "fr-CA", "fr-BE", "fr-CH", "it-IT", "es-ES", "es-MX", "es-AR", "pt-BR",
        "pt-PT", "nl-NL", "nl-BE", "sv-SE", "nb-NO", "da-DK", "fi-FI", "pl-PL", "cs-CZ", "hu-HU",
        "ro-RO", "el-GR", "tr-TR", "ru-RU", "uk-UA", "he-IL", "ja-JP", "ko-KR", "zh-CN", "zh-TW",
        "zh-HK", "th-TH"
    )

    fun label(code: String): String {
        val region = code.substringAfter('-').uppercase()
        val flag = region.map { String(Character.toChars(0x1F1E6 + (it - 'A'))) }.joinToString("")
        return "$flag  ${Locale.forLanguageTag(code).getDisplayName(Locale.ENGLISH)}"
    }
}
