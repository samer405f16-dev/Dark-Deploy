package com.darkdeploy.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

object Api {
    private val client = OkHttpClient()
    private val JSON = "application/json".toMediaType()

    class DeviceCode(val deviceCode: String, val userCode: String, val uri: String, val interval: Int)

    private fun call(req: Request): Pair<Int, String> =
        client.newCall(req).execute().use { it.code to (it.body?.string() ?: "") }

    private fun gh(url: String, token: String) = Request.Builder()
        .url(url)
        .header("Authorization", "Bearer $token")
        .header("Accept", "application/vnd.github+json")
        .header("X-GitHub-Api-Version", "2022-11-28")
        .header("User-Agent", "DarkDeploy")

    private fun msg(b: String): String =
        try { JSONObject(b).optString("message", b.take(200)) } catch (e: Exception) { b.take(200) }

    // ---------- GitHub ----------
    suspend fun githubUser(token: String): String = withContext(Dispatchers.IO) {
        val (c, b) = call(gh("https://api.github.com/user", token).get().build())
        if (c != 200) error("توکن گیت‌هاب معتبر نیست ($c)")
        JSONObject(b).getString("login")
    }

    suspend fun startDevice(clientId: String): DeviceCode = withContext(Dispatchers.IO) {
        val body = FormBody.Builder().add("client_id", clientId).add("scope", "repo").build()
        val (_, b) = call(
            Request.Builder().url("https://github.com/login/device/code")
                .header("Accept", "application/json").post(body).build()
        )
        val j = JSONObject(b)
        if (j.has("error")) error(j.optString("error_description", j.getString("error")))
        DeviceCode(
            j.getString("device_code"), j.getString("user_code"),
            j.getString("verification_uri"), j.optInt("interval", 5)
        )
    }

    suspend fun pollDevice(clientId: String, d: DeviceCode): String = withContext(Dispatchers.IO) {
        var wait = d.interval
        repeat(180) {
            delay(wait * 1000L)
            val body = FormBody.Builder()
                .add("client_id", clientId)
                .add("device_code", d.deviceCode)
                .add("grant_type", "urn:ietf:params:oauth:grant-type:device_code")
                .build()
            val (_, b) = call(
                Request.Builder().url("https://github.com/login/oauth/access_token")
                    .header("Accept", "application/json").post(body).build()
            )
            val j = JSONObject(b)
            if (j.has("access_token")) return@withContext j.getString("access_token")
            when (j.optString("error")) {
                "authorization_pending" -> {}
                "slow_down" -> wait += 5
                else -> error(j.optString("error_description", "ورود لغو شد"))
            }
        }
        error("زمان ورود تمام شد")
    }

    suspend fun forkRepo(token: String, repo: String): Pair<String, String> = withContext(Dispatchers.IO) {
        val (c, b) = call(gh("https://api.github.com/repos/$repo/forks", token).post("{}".toRequestBody(JSON)).build())
        if (c != 202 && c != 200) error("فورک ناموفق ($c): ${msg(b)}")
        val j = JSONObject(b)
        val full = j.getString("full_name")
        val branch = j.optString("default_branch", "main")
        repeat(30) {
            val (c2, _) = call(gh("https://api.github.com/repos/$full", token).get().build())
            if (c2 == 200) return@withContext full to branch
            delay(2000)
        }
        full to branch
    }

    // ---------- Railway ----------
    private fun gql(token: String, query: String): JSONObject {
        val payload = JSONObject().put("query", query).toString()
        val req = Request.Builder().url("https://backboard.railway.com/graphql/v2")
            .header("Authorization", "Bearer $token")
            .header("User-Agent", "DarkDeploy")
            .post(payload.toRequestBody(JSON)).build()
        val (c, b) = call(req)
        val j = try { JSONObject(b) } catch (e: Exception) { error("پاسخ نامعتبر از ریلوی ($c)") }
        if (j.has("errors")) error(j.getJSONArray("errors").getJSONObject(0).optString("message", "خطای ریلوی"))
        return j.getJSONObject("data")
    }

    suspend fun railwayDeploy(
        token: String, repo: String, branch: String, name: String, log: (String) -> Unit
    ): String = withContext(Dispatchers.IO) {
        log("ساخت پروژه در ریلوی...")
        val p = gql(token, """mutation { projectCreate(input: { name: "$name" }) { id environments { edges { node { id } } } } }""")
            .getJSONObject("projectCreate")
        val projectId = p.getString("id")
        val envId = p.getJSONObject("environments").getJSONArray("edges")
            .getJSONObject(0).getJSONObject("node").getString("id")

        log("ساخت سرویس از ریپوی فورک‌شده...")
        val serviceId = gql(
            token,
            """mutation { serviceCreate(input: { projectId: "$projectId", name: "$name", source: { repo: "$repo" }, branch: "$branch" }) { id } }"""
        ).getJSONObject("serviceCreate").getString("id")

        log("ساخت دامنه عمومی...")
        val domain = gql(
            token,
            """mutation { serviceDomainCreate(input: { serviceId: "$serviceId", environmentId: "$envId" }) { domain } }"""
        ).getJSONObject("serviceDomainCreate").getString("domain")

        "https://$domain"
    }
}
