@file:OptIn(ExperimentalMaterial3Api::class)

package com.darkdeploy.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = Store(this)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Surface(Modifier.fillMaxSize()) { App(store) }
                }
            }
        }
    }
}

@Composable
fun App(store: Store) {
    var accounts by remember { mutableStateOf(store.accounts) }
    var active by remember { mutableStateOf(store.active) }
    var screen by remember { mutableStateOf("home") }
    val shown = if (accounts.isEmpty()) "accounts" else screen

    BackHandler(shown != "home") { screen = "home" }

    when (shown) {
        "accounts" -> AccountsScreen(
            store, accounts, active,
            onChange = { a, act ->
                accounts = a; active = act
                store.accounts = a; store.active = act
            },
            onDone = { screen = "home" }
        )
        "railway" -> RailwayScreen(
            store,
            ghToken = accounts.firstOrNull { it.login == active }?.token ?: accounts.first().token,
            ghLogin = active.ifEmpty { accounts.first().login },
            onBack = { screen = "home" }
        )
        "cloudflare" -> Page("کلودفلر", { screen = "home" }) {
            Text("به‌زودی...")
        }
        else -> Page("Dark Deploy", null) {
            Text("اکانت فعال: ${active.ifEmpty { accounts.first().login }}")
            TextButton(onClick = { screen = "accounts" }) { Text("مدیریت اکانت‌ها") }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Tile("🚂", "ریلوی", Modifier.weight(1f)) { screen = "railway" }
                Tile("☁️", "کلودفلر", Modifier.weight(1f)) { screen = "cloudflare" }
            }
        }
    }
}

@Composable
fun Page(title: String, onBack: (() -> Unit)?, content: @Composable ColumnScope.() -> Unit) {
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(title) },
            navigationIcon = { if (onBack != null) TextButton(onClick = onBack) { Text("بازگشت") } }
        )
    }) { pad ->
        Column(
            Modifier.padding(pad).padding(16.dp).fillMaxSize().verticalScroll(rememberScrollState()),
            content = content
        )
    }
}

@Composable
fun Tile(icon: String, title: String, modifier: Modifier, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = modifier.height(130.dp)) {
        Column(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(icon, fontSize = 44.sp)
            Text(title, fontSize = 16.sp)
        }
    }
}

@Composable
fun AccountsScreen(
    store: Store, accounts: List<Account>, active: String,
    onChange: (List<Account>, String) -> Unit, onDone: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val uri = LocalUriHandler.current
    var token by remember { mutableStateOf("") }
    var clientId by remember { mutableStateOf(store.clientId) }
    var msg by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var code by remember { mutableStateOf<String?>(null) }

    fun addAccount(t: String) {
        scope.launch {
            busy = true; msg = ""
            try {
                val login = Api.githubUser(t.trim())
                onChange(accounts.filter { it.login != login } + Account(login, t.trim()), login)
                msg = "وارد شدی: $login"; token = ""
            } catch (e: Exception) { msg = e.message ?: "خطا" }
            busy = false
        }
    }

    fun deviceLogin() {
        scope.launch {
            busy = true; msg = ""
            try {
                val cid = clientId.trim()
                store.clientId = cid
                val d = Api.startDevice(cid)
                code = d.userCode
                clipboard.setText(AnnotatedString(d.userCode))
                uri.openUri(d.uri)
                val t = Api.pollDevice(cid, d)
                code = null
                val login = Api.githubUser(t)
                onChange(accounts.filter { it.login != login } + Account(login, t), login)
                msg = "وارد شدی: $login"
            } catch (e: Exception) { msg = e.message ?: "خطا"; code = null }
            busy = false
        }
    }

    Page("اکانت‌های گیت‌هاب", if (accounts.isNotEmpty()) onDone else null) {
        accounts.forEach { a ->
            Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = a.login == active, onClick = { onChange(accounts, a.login) })
                    Text(a.login, Modifier.weight(1f))
                    TextButton(onClick = {
                        val rest = accounts.filter { it.login != a.login }
                        onChange(rest, if (active == a.login) rest.firstOrNull()?.login ?: "" else active)
                    }) { Text("حذف") }
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        Text("افزودن اکانت با توکن (Personal Access Token)")
        OutlinedTextField(
            value = token, onValueChange = { token = it }, singleLine = true,
            label = { Text("توکن گیت‌هاب") }, modifier = Modifier.fillMaxWidth(),
            visualTransformation = PasswordVisualTransformation()
        )
        Button(enabled = !busy && token.isNotBlank(), onClick = { addAccount(token) }) { Text("ورود با توکن") }

        Spacer(Modifier.height(20.dp))
        Text("یا ورود با کد (Device Flow)")
        OutlinedTextField(
            value = clientId, onValueChange = { clientId = it }, singleLine = true,
            label = { Text("Client ID اپ OAuth") }, modifier = Modifier.fillMaxWidth()
        )
        Button(enabled = !busy && clientId.isNotBlank(), onClick = { deviceLogin() }) { Text("ورود با کد") }
        code?.let { Text("کد: $it (کپی شد؛ در صفحه گیت‌هاب وارد کن)", fontSize = 18.sp) }

        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 12.dp))
        if (msg.isNotEmpty()) Text(msg, Modifier.padding(top = 12.dp))
    }
}

@Composable
fun RailwayScreen(store: Store, ghToken: String, ghLogin: String, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val uri = LocalUriHandler.current
    val clipboard = LocalClipboardManager.current
    var selected by remember { mutableStateOf<Panel?>(null) }
    var rw by remember { mutableStateOf(store.railwayToken) }
    val log = remember { mutableStateListOf<String>() }
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf("") }

    Page("ریلوی", onBack) {
        Text("یک پنل انتخاب کن (اکانت: $ghLogin)")
        PANELS.forEach { p ->
            Card(
                onClick = { selected = p; log.clear(); result = "" },
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(p.icon, fontSize = 30.sp)
                    Spacer(Modifier.width(12.dp))
                    Text(p.title, fontSize = 18.sp)
                }
            }
        }

        selected?.let { p ->
            Spacer(Modifier.height(16.dp))
            Text("دیپلوی ${p.title}", fontSize = 18.sp)
            OutlinedTextField(
                value = rw, onValueChange = { rw = it }, singleLine = true,
                label = { Text("توکن ریلوی") }, modifier = Modifier.fillMaxWidth(),
                visualTransformation = PasswordVisualTransformation()
            )
            Button(enabled = !busy && rw.isNotBlank(), onClick = {
                scope.launch {
                    busy = true; log.clear(); result = ""
                    store.railwayToken = rw.trim()
                    try {
                        log.add("فورک ${p.repo} ...")
                        val (fork, branch) = Api.forkRepo(ghToken, p.repo)
                        log.add("فورک شد: $fork")
                        result = Api.railwayDeploy(rw.trim(), fork, branch, p.slug) { log.add(it) }
                        log.add("انجام شد ✅ (چند دقیقه صبر کن تا بیلد تموم بشه)")
                    } catch (e: Exception) { log.add("خطا: ${e.message}") }
                    busy = false
                }
            }) { Text(if (busy) "در حال انجام..." else "فورک و دیپلوی") }

            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
            log.forEach { Text("• $it", Modifier.padding(top = 4.dp)) }
            if (result.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(result)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { uri.openUri(result) }) { Text("باز کردن") }
                    OutlinedButton(onClick = { clipboard.setText(AnnotatedString(result)) }) { Text("کپی لینک") }
                }
            }
        }
    }
}
