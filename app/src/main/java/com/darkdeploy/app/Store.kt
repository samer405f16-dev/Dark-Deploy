package com.darkdeploy.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class Account(val login: String, val token: String)
data class Panel(val title: String, val icon: String, val repo: String, val slug: String)

// برای اضافه کردن پنل جدید فقط یک خط به این لیست اضافه کن
val PANELS = listOf(
    Panel("Spider Panel", "🕷️", "amirh00sain/spiderpanel", "spider-panel")
)

class Store(ctx: Context) {
    private val sp = ctx.getSharedPreferences("dd", Context.MODE_PRIVATE)

    var accounts: List<Account>
        get() {
            val arr = JSONArray(sp.getString("accounts", "[]"))
            return (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                Account(o.getString("login"), o.getString("token"))
            }
        }
        set(v) {
            val arr = JSONArray()
            v.forEach { arr.put(JSONObject().put("login", it.login).put("token", it.token)) }
            sp.edit().putString("accounts", arr.toString()).apply()
        }

    var active: String
        get() = sp.getString("active", "") ?: ""
        set(v) { sp.edit().putString("active", v).apply() }

    var railwayToken: String
        get() = sp.getString("rw", "") ?: ""
        set(v) { sp.edit().putString("rw", v).apply() }

    var clientId: String
        get() = sp.getString("cid", "") ?: ""
        set(v) { sp.edit().putString("cid", v).apply() }
}
