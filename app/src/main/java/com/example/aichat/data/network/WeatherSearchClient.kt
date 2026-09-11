package com.example.aichat.data.network

import kotlinx.serialization.json.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** Structured weather data: never treat an indexed weather-page snippet as a live observation. */
internal class WeatherSearchClient(
    private val client: OkHttpClient,
    private val clock: Clock,
    private val geocodingUrl: String = "https://geocoding-api.open-meteo.com/v1/search",
    private val forecastUrl: String = "https://api.open-meteo.com/v1/forecast",
) {
    suspend fun search(query: String, previousQuery: String? = null): WebSearchResult? {
        if (!Regex("天气|气温|温度|下雨|下雪|weather|temperature", RegexOption.IGNORE_CASE).containsMatchIn(query)) return null
        val place = extractWeatherPlace(query) ?: previousQuery?.let(::extractWeatherPlace) ?: return null
        val geoUrl = geocodingUrl.toHttpUrl().newBuilder().addQueryParameter("name", place)
            .addQueryParameter("count", "10").addQueryParameter("language", if (place.any { it in '\u4e00'..'\u9fff' }) "zh" else "en").addQueryParameter("format", "json").build()
        val places = fetch(geoUrl.toString())["results"]?.jsonArray ?: return null
        val candidates = places.map { it.jsonObject }.filter {
            val name = it["name"]?.jsonPrimitive?.content.orEmpty().removeSuffix("市")
            name.equals(place.removeSuffix("市"), true) || it["admin1"]?.jsonPrimitive?.content?.removeSuffix("市") == place.removeSuffix("市")
        }
        // Prefer an unambiguous administrative city over villages sharing its name.
        val cities = candidates.filter { it["feature_code"]?.jsonPrimitive?.content?.startsWith("PPLA") == true || it["feature_code"]?.jsonPrimitive?.content == "PPLC" }
        val location = cities.singleOrNull() ?: candidates.singleOrNull() ?: return null
        val lat = location["latitude"]?.jsonPrimitive?.doubleOrNull ?: return null
        val lon = location["longitude"]?.jsonPrimitive?.doubleOrNull ?: return null
        val zone = location["timezone"]?.jsonPrimitive?.content?.let(ZoneId::of) ?: return null
        val locationClock = clock.withZone(zone)
        val today = LocalDate.now(locationClock)
        val plan = planSearch(query, locationClock)
        val target = plan.targetDate ?: today
        if (target.isBefore(today) || target.isAfter(today.plusDays(15))) return null
        val url = forecastUrl.toHttpUrl().newBuilder()
            .addQueryParameter("latitude", lat.toString()).addQueryParameter("longitude", lon.toString())
            .addQueryParameter("current", "temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,wind_speed_10m")
            .addQueryParameter("daily", "weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max")
            .addQueryParameter("timezone", zone.id).addQueryParameter("forecast_days", "16").build().toString()
        val data = fetch(url)
        return parseForecast(data, target, locationClock, location, url)
    }

    private suspend fun fetch(url: String): JsonObject = client.searchResponse(Request.Builder().url(url).build()).use {
        check(it.isSuccessful) { "天气服务暂时不可用" }
        Json.parseToJsonElement(it.body?.string().orEmpty()).jsonObject
    }

    internal fun parseForecast(data: JsonObject, target: LocalDate, localClock: Clock, location: JsonObject, url: String): WebSearchResult? {
        val daily = data["daily"]?.jsonObject ?: return null
        val index = daily["time"]?.jsonArray?.indexOfFirst { it.jsonPrimitive.content == target.toString() } ?: -1
        if (index < 0) return null
        fun dayValue(key: String) = daily[key]?.jsonArray?.getOrNull(index)?.jsonPrimitive?.contentOrNull
        val max = dayValue("temperature_2m_max") ?: return null
        val min = dayValue("temperature_2m_min") ?: return null
        val place = listOf("country", "admin1", "admin2", "name").mapNotNull { location[it]?.jsonPrimitive?.contentOrNull }.distinct().joinToString(" / ")
        val current = data["current"]?.jsonObject
        val currentTime = current?.get("time")?.jsonPrimitive?.contentOrNull?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() }
        val fresh = currentTime != null && Duration.between(currentTime.atZone(localClock.zone).toInstant(), localClock.instant()).let { !it.isNegative && it <= Duration.ofHours(3) }
        val currentText = if (target == LocalDate.now(localClock) && fresh) {
            "当前模型估算（并非气象站实测）：数据时刻 $currentTime ${localClock.zone}；" +
                listOf("temperature_2m" to "气温 °C", "apparent_temperature" to "体感 °C", "relative_humidity_2m" to "湿度 %", "wind_speed_10m" to "风速 km/h")
                    .mapNotNull { (key, label) -> current?.get(key)?.jsonPrimitive?.contentOrNull?.let { "$label：$it" } }.joinToString("；") +
                "；天气：${weatherDescription(current?.get("weather_code")?.jsonPrimitive?.intOrNull)}。\n"
        } else ""
        return WebSearchResult(
            title = "$place $target 天气 · Open-Meteo",
            url = url,
            snippet = "地点：$place；经纬度：${location["latitude"]}, ${location["longitude"]}；时区：${localClock.zone}。\n" +
                currentText + "$target 逐日预报：${weatherDescription(dayValue("weather_code")?.toIntOrNull())}；最低 $min °C，最高 $max °C" +
                (dayValue("precipitation_probability_max")?.let { "；最大降水概率 $it%" } ?: "") +
                "。获取时间：${localClock.instant()}。来源：Open-Meteo（CC BY 4.0）；预报会随模型更新。",
        )
    }
}

internal fun extractWeatherPlace(query: String): String? {
    var text = query.lineSequence().filterNot { it.trimStart().startsWith(">") }.joinToString(" ")
    text = text.replace(Regex("20\\d{2}[-年/]\\d{1,2}[-月/]\\d{1,2}日?"), "")
        .replace(Regex("今天|今日|明天|后天|昨天|现在|当前|目前|实时|最新|请问|请|帮我|查一下|查询|搜索|联网|我想知道|怎么样|如何|预报|情况|多少|几度|会不会|会|吗|的|[？?，,。！!]"), "")
    Regex("(?:weather|temperature)\\s+(?:in|at|for)\\s+([a-zA-Z][a-zA-Z .'-]*?)(?:\\s+(?:today|tomorrow|now)|$)", RegexOption.IGNORE_CASE).find(text)?.let { return it.groupValues[1].trim() }
    val place = text.replace(Regex("天气|气温|温度|下雨|下雪|today|tomorrow|weather|temperature", RegexOption.IGNORE_CASE), "").trim().removeSuffix("市")
    return place.takeIf { it.length in 2..40 && !Regex("这里|当地|这边|那边|全国|哪里|what|how", RegexOption.IGNORE_CASE).containsMatchIn(it) }
}

internal fun weatherDescription(code: Int?): String = when (code) {
    0 -> "晴"; 1 -> "大部晴朗"; 2 -> "多云"; 3 -> "阴"; 45, 48 -> "雾"
    51, 53, 55 -> "毛毛雨"; 56, 57, 66, 67 -> "冻雨"; 61 -> "小雨"; 63 -> "中雨"; 65 -> "大雨"
    71 -> "小雪"; 73 -> "中雪"; 75 -> "大雪"; 77 -> "雪粒"; 80, 81, 82 -> "阵雨"; 85, 86 -> "阵雪"
    95 -> "雷暴"; 96, 99 -> "雷暴伴冰雹"; else -> "天气现象代码 ${code ?: "缺失"}"
}
