package com.exteragram.messenger.proxy

import android.net.Uri
import android.os.SystemClock
import com.exteragram.messenger.utils.network.ExteraHttpClient
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import org.telegram.messenger.FileLog
import org.telegram.messenger.Utilities
import java.io.IOException
import java.net.InetAddress
import java.util.concurrent.TimeUnit

object IpAddressInfoController {

    private const val CACHE_SIZE = 100
    private const val SUCCESS_TTL = 24 * 60 * 60 * 1000L
    private const val FAILURE_TTL = 5 * 60 * 1000L

    private val httpClient: OkHttpClient = ExteraHttpClient.client.newBuilder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .writeTimeout(4, TimeUnit.SECONDS)
        .build()

    private val ipv4Regex = Regex("^\\d{1,3}(?:\\.\\d{1,3}){3}$")
    private val ipv6CharsRegex = Regex("^[0-9a-f:.]+$", RegexOption.IGNORE_CASE)

    private val cache = object : LinkedHashMap<String, CacheEntry>(CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CacheEntry>?): Boolean = size > CACHE_SIZE
    }

    data class IpAddressInfo(
        @JvmField val ip: String,
        @JvmField val country: String,
        @JvmField val countryCode: String,
        @JvmField val region: String,
        @JvmField val city: String,
        @JvmField val isp: String,
        @JvmField val organization: String,
        @JvmField val domain: String,
        @JvmField val timezone: String,
    )

    data class CacheEntry(val info: IpAddressInfo?, val time: Long, val ttl: Long)

    @JvmStatic
    fun extractIpAddress(address: String?): String? {
        if (address.isNullOrEmpty()) {
            return null
        }
        var host = try {
            Uri.parse(address).host ?: if (address.contains("://")) null else Uri.parse("http://$address").host
        } catch (e: Exception) {
            FileLog.e(e)
            null
        }
        if (host.isNullOrEmpty()) {
            return null
        }
        if (host.startsWith("[") && host.endsWith("]") && host.length > 2) {
            host = host.substring(1, host.length - 1)
        }
        if (!isIpAddress(host)) {
            return null
        }
        return try {
            val inet = InetAddress.getByName(host)
            if (inet.isAnyLocalAddress || inet.isLoopbackAddress || inet.isLinkLocalAddress || inet.isSiteLocalAddress || inet.isMulticastAddress) {
                null
            } else {
                inet.hostAddress
            }
        } catch (e: Exception) {
            FileLog.e(e)
            null
        }
    }

    @JvmStatic
    fun requestIpAddressInfo(ip: String, callback: Utilities.Callback<IpAddressInfo?>): Call? {
        getCachedInfo(ip)?.let {
            callback.run(it.info)
            return null
        }
        val url = "https://ipwho.is".toHttpUrlOrNull()?.newBuilder()?.addPathSegment(ip)?.build()
        if (url == null) {
            callback.run(null)
            return null
        }
        val call = httpClient.newCall(Request.Builder().url(url).build())
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (call.isCanceled()) {
                    return
                }
                FileLog.e(e)
                putCachedInfo(ip, null)
                callback.run(null)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val info = try {
                        if (it.isSuccessful) parseIpAddressInfo(ip, it.body?.string().orEmpty()) else null
                    } catch (e: Exception) {
                        FileLog.e(e)
                        null
                    }
                    if (!call.isCanceled()) {
                        putCachedInfo(ip, info)
                        callback.run(info)
                    }
                }
            }
        })
        return call
    }

    private fun isIpAddress(host: String?): Boolean {
        if (host.isNullOrEmpty()) {
            return false
        }
        if (host.indexOf(':') >= 0) {
            return ipv6CharsRegex.matches(host)
        }
        if (!ipv4Regex.matches(host)) {
            return false
        }
        return host.split('.').all { Utilities.parseInt(it) in 0..255 }
    }

    private fun getCachedInfo(ip: String): CacheEntry? = synchronized(cache) {
        val entry = cache[ip] ?: return@synchronized null
        if (SystemClock.elapsedRealtime() - entry.time > entry.ttl) {
            cache.remove(ip)
            null
        } else {
            entry
        }
    }

    private fun putCachedInfo(ip: String, info: IpAddressInfo?) {
        synchronized(cache) {
            cache[ip] = CacheEntry(info, SystemClock.elapsedRealtime(), if (info != null) SUCCESS_TTL else FAILURE_TTL)
        }
    }

    private fun parseIpAddressInfo(ip: String, body: String): IpAddressInfo? {
        val json = JSONObject(body)
        if (!json.optBoolean("success", false)) {
            return null
        }
        val connection = json.optJSONObject("connection")
        val timezone = json.optJSONObject("timezone")
        return IpAddressInfo(
            ip = json.optString("ip", ip),
            country = json.optString("country", ""),
            countryCode = json.optString("country_code", ""),
            region = json.optString("region", ""),
            city = json.optString("city", ""),
            isp = connection?.optString("isp", "") ?: "",
            organization = connection?.optString("org", "") ?: "",
            domain = connection?.optString("domain", "") ?: "",
            timezone = timezone?.optString("id", "") ?: "",
        )
    }

    @JvmStatic
    fun joinInfo(vararg parts: String?): String = parts.filterNot { it.isNullOrEmpty() }.joinToString(", ")
}
