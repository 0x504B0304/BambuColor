package com.m0h31h31.bambucolor

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.Image
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.*
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import com.m0h31h31.bambucolor.nfc.NfcReader
import com.m0h31h31.bambucolor.nfc.TagDataParsed
import com.m0h31h31.bambucolor.data.AppDatabase
import com.m0h31h31.bambucolor.data.ConsumableDao
import com.m0h31h31.bambucolor.data.ConsumableEntity
import com.m0h31h31.bambucolor.data.TagConfigDao
import com.m0h31h31.bambucolor.data.TagWithConsumable
import com.m0h31h31.bambucolor.ui.theme.BambuColorTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

class MainActivity : ComponentActivity() {

    private lateinit var nfcReader: NfcReader

    private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Compose 可观察状态：刷到 NFC 就更新它们
    private var lastUidState by mutableStateOf<String?>(null)
    private var currentDestinationState by mutableStateOf(AppDestinations.HOME)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        nfcReader = NfcReader(
            activity = this,
            onTagRead = { uidHex, _ ->
                runOnUiThread {
                    Log.d("NFC", "UID=$uidHex")
                    lastUidState = uidHex
                    currentDestinationState = AppDestinations.HOME
                }
            },
            onTagParsed = { data ->
                handleTagParsed(data)
            },
            onNoMifareClassic = {
                runOnUiThread {
                    Toast.makeText(this, "该设备不支持读取加密标签数据", Toast.LENGTH_SHORT).show()
                }
            },
            onError = { msg ->
                runOnUiThread {
                    Log.w("NFC", msg)
                    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                }
            }
        )

        ensureExternalJsonCopy()

        setContent {
            BambuColorTheme {
                BambuColorApp(
                    lastUid = lastUidState,
                    currentDestination = currentDestinationState,
                    onDestinationChange = { currentDestinationState = it }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        nfcReader.enable()
    }

    override fun onPause() {
        nfcReader.disable()
        super.onPause()
    }

    private fun ensureExternalJsonCopy() {
        try {
            val outFile = getExternalJsonFile()
            if (!outFile.exists()) {
                assets.open("filaments_color_codes.json").use { input ->
                    outFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
        } catch (_: Throwable) {
            // ignore
        }
    }

    private fun getExternalJsonFile(): File {
        val dir = getExternalFilesDir(null) ?: filesDir
        return File(dir, "filaments_color_codes.json")
    }

    override fun onDestroy() {
        ioScope.cancel()
        super.onDestroy()
    }

    private fun handleTagParsed(data: TagDataParsed) {
        val uid = data.uidHex
        if (uid.isBlank()) return

        val db = AppDatabase.get(this)
        val type = data.detailedFilamentType.ifBlank { data.filamentType }

        ioScope.launch {
            try {
                val consumableDao = db.consumableDao()
                val tagConfigDao = db.tagConfigDao()

                val all = consumableDao.getAll()
                val colorCode = "${data.materialId}-${data.variantId}"
                val colorValuesHex = if (data.colorArgbSecond != null) {
                    "${formatColorHex(data.colorArgb)},${formatColorHex(data.colorArgbSecond)}"
                } else {
                    formatColorHex(data.colorArgb)
                }

                val existing = all.find {
                    it.type.trim().equals(type.trim(), ignoreCase = true) &&
                        it.colorValueArgb == data.colorArgb
                }

                if (existing != null) {
                    tagConfigDao.upsertBinding(uid, existing.id)
                } else {
                    val newId = consumableDao.insert(
                        ConsumableEntity(
                            type = type,
                            colorName = data.detailedFilamentType.ifBlank { data.filamentType },
                            colorValueArgb = data.colorArgb,
                            colorCode = colorCode,
                            colorValuesHex = colorValuesHex
                        )
                    )
                    tagConfigDao.upsertBinding(uid, newId)
                }

                runOnUiThread {
                    lastUidState = uid
                    currentDestinationState = AppDestinations.HOME
                    Toast.makeText(this@MainActivity, "已自动识别并绑定：$type", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "标签解析失败：${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}

/* -------------------- UI: App Scaffold + Screens -------------------- */

@Composable
private fun BambuColorApp(
    lastUid: String?,
    currentDestination: AppDestinations,
    onDestinationChange: (AppDestinations) -> Unit
) {
    val context = LocalContext.current
    val db = remember { AppDatabase.get(context) }
    val consumableDao = remember { db.consumableDao() }
    val tagConfigDao = remember { db.tagConfigDao() }

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            AppDestinations.entries.forEach { dest ->
                item(
                    icon = { Icon(dest.icon, contentDescription = dest.label) },
                    label = { Text(dest.label) },
                    selected = dest == currentDestination,
                    onClick = { onDestinationChange(dest) }
                )
            }
        }
    ) {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            when (currentDestination) {
                AppDestinations.HOME -> HomeScreen(
                    uid = lastUid,
                    consumableDao = consumableDao,
                    tagConfigDao = tagConfigDao,
                    modifier = Modifier.padding(innerPadding)
                )

                AppDestinations.CONFIG -> ConfigScreen(
                    consumableDao = consumableDao,
                    tagConfigDao = tagConfigDao,
                    modifier = Modifier.padding(innerPadding)
                )

                AppDestinations.ABOUT -> AboutScreen(
                    modifier = Modifier.padding(innerPadding)
                )
            }
        }
    }
}

private enum class AppDestinations(val label: String, val icon: ImageVector) {
    HOME("颜色", Icons.Default.Home),
    CONFIG("配置", Icons.Default.Favorite),
    ABOUT("关于", Icons.Default.AccountBox),
}

@Composable
private fun HomeScreen(
    uid: String?,
    consumableDao: ConsumableDao,
    tagConfigDao: TagConfigDao,
    modifier: Modifier = Modifier
) {
    val hasUid = !uid.isNullOrBlank()
    val scope = rememberCoroutineScope()
    val consumables by consumableDao.observeAll().collectAsState(initial = emptyList())
    var bindingRefreshKey by remember { mutableStateOf(0) }
    var bindError by remember { mutableStateOf<String?>(null) }
    var selectedConsumableId by rememberSaveable(uid) { mutableStateOf<Long?>(null) }
    var bindSearchQuery by rememberSaveable(uid) { mutableStateOf("") }
    var isBindingLoading by remember { mutableStateOf(false) }

    val tagBinding by produceState<TagWithConsumable?>(initialValue = null, key1 = uid, key2 = bindingRefreshKey) {
        value = null
        if (!uid.isNullOrBlank()) {
            isBindingLoading = true
            value = withContext(Dispatchers.IO) {
                tagConfigDao.getTagWithConsumable(uid)
            }
            isBindingLoading = false
        }
    }
    val filteredConsumables = remember(consumables, bindSearchQuery) {
        val q = bindSearchQuery.trim()
        if (q.isEmpty()) consumables else consumables.filter { it.matchesQuery(q) }
    }

    val tagColors = tagBinding?.let { parseColorList(it.colorValuesHex, it.colorValueArgb) }
        ?: if (hasUid) listOf(Color(0xFF00BCD4)) else listOf(Color(0xFF2B2F36))
    val colorHexText = if (tagBinding != null) {
        if (tagColors.size > 1) "多色(${tagColors.size})" else formatColorHex(tagBinding!!.colorValueArgb)
    } else if (hasUid) {
        "(未匹配)"
    } else {
        "(待匹配)"
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 顶部提示卡
        ElevatedCard(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.nfc),
                    contentDescription = "NFC",
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = if (!hasUid) "请将手机靠近 NFC 标签进行识别" else "已识别到标签",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = if (!hasUid) "等待读取 UID / 数据…" else "UID：$uid",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        if (hasUid && isBindingLoading) {
            ElevatedCard(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("正在读取标签绑定信息…", style = MaterialTheme.typography.titleMedium)
                }
            }
        } else if (tagBinding != null) {
            ElevatedCard(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        "耗材信息",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    val colorBoxModifier = if (tagColors.size > 1) {
                        Modifier.background(Brush.horizontalGradient(tagColors), RoundedCornerShape(20.dp))
                    } else {
                        Modifier.background(tagColors.firstOrNull() ?: Color(0xFF2B2F36), RoundedCornerShape(20.dp))
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .then(colorBoxModifier)
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(tagBinding!!.type, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text(tagBinding!!.colorName, style = MaterialTheme.typography.titleLarge)
                        Text("色号：${tagBinding!!.colorCode}", style = MaterialTheme.typography.titleMedium)
                        val colorValuesInline = tagBinding!!.colorValuesHex.replace(",", " ")
                        Text("色值：$colorValuesInline", style = MaterialTheme.typography.titleMedium)
                    }
                    Button(
                        onClick = {
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    tagConfigDao.delete(
                                        com.m0h31h31.bambucolor.data.TagConfigEntity(
                                            id = tagBinding!!.tagId,
                                            uidHex = tagBinding!!.uidHex,
                                            consumableId = tagBinding!!.consumableId
                                        )
                                    )
                                }
                                bindingRefreshKey++
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("取消绑定")
                    }
                    Image(
                        painter = painterResource(id = R.drawable.bambucolor),
                        contentDescription = "BambuColor",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        contentScale = ContentScale.Fit
                    )
                }
            }
        } else if (hasUid) {
            ElevatedCard(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "未匹配到标签，请选择耗材绑定",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (consumables.isEmpty()) {
                        Text("耗材库为空，请先在配置页添加耗材。", style = MaterialTheme.typography.bodySmall)
                    } else {
                        OutlinedTextField(
                            value = bindSearchQuery,
                            onValueChange = { bindSearchQuery = it },
                            label = { Text("搜索耗材（任意字段）") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(filteredConsumables, key = { it.id }) { item ->
                                val itemColors = parseColorList(item.colorValuesHex, item.colorValueArgb)
                                ElevatedCard(
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        RadioButton(
                                            selected = selectedConsumableId == item.id,
                                            onClick = { selectedConsumableId = item.id }
                                        )
                                        val itemColorBox = if (itemColors.size > 1) {
                                            Modifier.background(Brush.horizontalGradient(itemColors), RoundedCornerShape(6.dp))
                                        } else {
                                            Modifier.background(itemColors.firstOrNull() ?: Color(item.colorValueArgb), RoundedCornerShape(6.dp))
                                        }
                                        Box(
                                            modifier = Modifier
                                                .size(22.dp)
                                                .then(itemColorBox)
                                        )
                                        Column(Modifier.weight(1f)) {
                                            Text(item.type, style = MaterialTheme.typography.bodyMedium)
                                            Text(item.colorName, style = MaterialTheme.typography.bodyMedium)
                                            Text("色号：${item.colorCode}", style = MaterialTheme.typography.bodySmall)
                                        }
                                        Text(formatColorLabel(item.colorValuesHex, item.colorValueArgb), style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                        if (filteredConsumables.isEmpty()) {
                            Text("未找到匹配耗材。", style = MaterialTheme.typography.bodySmall)
                        }

                        Button(
                            onClick = {
                                val id = selectedConsumableId
                                val uidValue = uid ?: run {
                                    bindError = "请先刷卡读取 UID"
                                    return@Button
                                }
                                if (id != null) {
                                    bindError = null
                                    scope.launch {
                                        withContext(Dispatchers.IO) {
                                            tagConfigDao.upsertBinding(uidValue, id)
                                        }
                                        bindingRefreshKey++
                                    }
                                } else {
                                    bindError = "请选择一个耗材进行绑定"
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("绑定到此标签")
                        }
                    }

                    if (bindError != null) {
                        Text(bindError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfigScreen(
    consumableDao: ConsumableDao,
    tagConfigDao: TagConfigDao,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val consumables by consumableDao.observeAll().collectAsState(initial = emptyList())
    val tagConfigs by tagConfigDao.observeAll().collectAsState(initial = emptyList())
    val consumableMap = remember(consumables) { consumables.associateBy { it.id } }
    val bindingsByConsumable = remember(tagConfigs) { tagConfigs.groupBy { it.consumableId } }

    var addType by rememberSaveable { mutableStateOf("") }
    var typeMenuExpanded by remember { mutableStateOf(false) }
    var addColorName by rememberSaveable { mutableStateOf("") }
    var addColorCode by rememberSaveable { mutableStateOf("") }
    var addColorValue by rememberSaveable { mutableStateOf("") }
    var configError by remember { mutableStateOf<String?>(null) }
    var editingConsumable by remember { mutableStateOf<ConsumableEntity?>(null) }
    var importStatus by remember { mutableStateOf<String?>(null) }
    var consumableSearchQuery by rememberSaveable { mutableStateOf("") }

    var bindUid by rememberSaveable { mutableStateOf("") }
    var bindConsumableId by rememberSaveable { mutableStateOf<Long?>(null) }
    var bindSearchQuery by rememberSaveable { mutableStateOf("") }

    val filteredConsumables = remember(consumables, consumableSearchQuery) {
        val q = consumableSearchQuery.trim()
        if (q.isEmpty()) consumables else consumables.filter { it.matchesQuery(q) }
    }

    val materialTypeOptions = remember {
        listOf(
            "Bambu ABS",
            "Bambu ABS-GF",
            "Bambu ASA",
            "Bambu ASA-Aero",
            "Bambu ASA-CF",
            "Bambu PA-CF",
            "Bambu PA6-CF",
            "Bambu PA6-GF",
            "Bambu PAHT-CF",
            "Bambu PC",
            "Bambu PC FR",
            "Bambu PET-CF",
            "Bambu PETG Basic",
            "Bambu PETG HF",
            "Bambu PETG Translucent",
            "Bambu PETG-CF",
            "Bambu PLA Aero",
            "Bambu PLA Basic",
            "Bambu PLA Dynamic",
            "Bambu PLA Galaxy",
            "Bambu PLA Glow",
            "Bambu PLA Impact",
            "Bambu PLA Lite",
            "Bambu PLA Marble",
            "Bambu PLA Matte",
            "Bambu PLA Metal",
            "Bambu PLA Silk",
            "Bambu PLA Silk+",
            "Bambu PLA Sparkle",
            "Bambu PLA Tough",
            "Bambu PLA Tough+",
            "Bambu PLA Translucent",
            "Bambu PLA Wood",
            "Bambu PLA-CF",
            "Bambu PPA-CF",
            "Bambu PPA-GF",
            "Bambu PPS-CF",
            "Bambu PVA",
            "Bambu Support for ABS",
            "Bambu Support For PA PET",
            "Bambu Support For PLA",
            "Bambu Support For PLA-PETG",
            "Bambu Support G",
            "Bambu Support W",
            "Bambu TPU 85A",
            "Bambu TPU 90A",
            "Bambu TPU 95A",
            "Bambu TPU 95A HF",
            "Bambu TPU for AMS"
        )
    }
    val filteredTypes = remember(addType, materialTypeOptions) {
        val q = addType.trim()
        if (q.isEmpty()) {
            materialTypeOptions
        } else {
            materialTypeOptions.filter { it.contains(q, ignoreCase = true) }
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("耗材配置库", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }

        item {
            ElevatedCard(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("新增耗材", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    ExposedDropdownMenuBox(
                        expanded = typeMenuExpanded,
                        onExpandedChange = { typeMenuExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = addType,
                            onValueChange = {
                                addType = it
                                typeMenuExpanded = true
                            },
                            label = { Text("耗材类型（可搜索）") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeMenuExpanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(MenuAnchorType.PrimaryEditable),
                            singleLine = true
                        )
                        ExposedDropdownMenu(
                            expanded = typeMenuExpanded,
                            onDismissRequest = { typeMenuExpanded = false }
                        ) {
                            filteredTypes.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option) },
                                    onClick = {
                                        addType = option
                                        typeMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = addColorName,
                        onValueChange = { addColorName = it },
                        label = { Text("色名") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = addColorCode,
                        onValueChange = { addColorCode = it },
                        label = { Text("色号") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = addColorValue,
                        onValueChange = { addColorValue = it },
                        label = { Text("色值（多色用空格分隔）") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = {
                            val parsedColors = parseMultiColorInput(addColorValue)
                            val colorInt = parsedColors.firstOrNull()
                            if (addType.isBlank() || addColorName.isBlank() || addColorCode.isBlank() || colorInt == null) {
                                configError = "请完整填写耗材信息，色值格式如 #RRGGBB，多色用空格分隔"
                                return@Button
                            }
                            configError = null
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    consumableDao.insert(
                                        ConsumableEntity(
                                            type = addType.trim(),
                                            colorName = addColorName.trim(),
                                            colorValueArgb = colorInt,
                                            colorCode = addColorCode.trim(),
                                            colorValuesHex = parsedColors.joinToString(",") { formatColorHex(it) }
                                        )
                                    )
                                }
                                addType = ""
                                addColorName = ""
                                addColorCode = ""
                                addColorValue = ""
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("保存耗材")
                    }
                    if (configError != null) {
                        Text(configError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        item {
            ElevatedCard(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("从 JSON 导入耗材", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        "外部路径：Android/data/${context.packageName}/files/filaments_color_codes.json",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Button(
                        onClick = {
                            importStatus = null
                            scope.launch {
                                val result = withContext(Dispatchers.IO) {
                                    try {
                                        val json = readConsumablesJson(context)
                                        val parsed = parseConsumablesFromJson(json)
                                        val existingKeys = consumables.map { it.uniqueKey() }.toHashSet()
                                        val toInsert = parsed.filter { existingKeys.add(it.uniqueKey()) }
                                        toInsert.forEach { consumableDao.insert(it) }
                                        "导入完成：新增 ${toInsert.size} 条"
                                    } catch (t: Throwable) {
                                        "导入失败：${t.message ?: t.javaClass.simpleName}"
                                    }
                                }
                                importStatus = result
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("开始导入")
                    }
                    if (importStatus != null) {
                        Text(importStatus!!, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        if (consumables.isNotEmpty()) {
            item {
                OutlinedTextField(
                    value = consumableSearchQuery,
                    onValueChange = { consumableSearchQuery = it },
                    label = { Text("搜索耗材（任意字段）") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        }

        if (consumables.isEmpty()) {
            item {
                Text("暂无耗材，请先新增。", style = MaterialTheme.typography.bodySmall)
            }
        } else {
            items(filteredConsumables, key = { it.id }) { item ->
                val itemColors = parseColorList(item.colorValuesHex, item.colorValueArgb)
                ElevatedCard(shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val itemColorBox = if (itemColors.size > 1) {
                            Modifier.background(Brush.horizontalGradient(itemColors), RoundedCornerShape(8.dp))
                        } else {
                            Modifier.background(itemColors.firstOrNull() ?: Color(item.colorValueArgb), RoundedCornerShape(8.dp))
                        }
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .then(itemColorBox)
                        )
                        Column(Modifier.weight(1f)) {
                            Text(item.type, style = MaterialTheme.typography.bodyMedium)
                            Text(item.colorName, style = MaterialTheme.typography.bodyMedium)
                            Text("色号：${item.colorCode}", style = MaterialTheme.typography.bodySmall)
                        }
                        Text(formatColorLabel(item.colorValuesHex, item.colorValueArgb), style = MaterialTheme.typography.bodySmall)
                        IconButton(onClick = { editingConsumable = item }) {
                            Icon(Icons.Default.Edit, contentDescription = "编辑")
                        }
                        IconButton(onClick = {
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    bindingsByConsumable[item.id].orEmpty().forEach { binding ->
                                        tagConfigDao.delete(binding)
                                    }
                                    consumableDao.delete(item)
                                }
                            }
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "删除")
                        }
                    }
                }
            }
            if (filteredConsumables.isEmpty()) {
                item {
                    Text("未找到匹配耗材。", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        item {
            Spacer(Modifier.height(8.dp))
            Text("标签配置库", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }

        item {
            ElevatedCard(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("绑定/更新标签", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = bindUid,
                        onValueChange = { bindUid = it.uppercase() },
                        label = { Text("标签 UID") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (consumables.isEmpty()) {
                        Text("耗材库为空，无法绑定。", style = MaterialTheme.typography.bodySmall)
                    } else {
                        OutlinedTextField(
                            value = bindSearchQuery,
                            onValueChange = { bindSearchQuery = it },
                            label = { Text("搜索耗材（任意字段）") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        val filteredConsumables = remember(consumables, bindSearchQuery) {
                            val q = bindSearchQuery.trim()
                            if (q.isEmpty()) consumables else consumables.filter { it.matchesQuery(q) }
                        }
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 200.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(filteredConsumables, key = { it.id }) { item ->
                                val itemColors = parseColorList(item.colorValuesHex, item.colorValueArgb)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    RadioButton(
                                        selected = bindConsumableId == item.id,
                                        onClick = { bindConsumableId = item.id }
                                    )
                                    val itemColorBox = if (itemColors.size > 1) {
                                        Modifier.background(Brush.horizontalGradient(itemColors), RoundedCornerShape(6.dp))
                                    } else {
                                        Modifier.background(itemColors.firstOrNull() ?: Color(item.colorValueArgb), RoundedCornerShape(6.dp))
                                    }
                                    Box(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .then(itemColorBox)
                                    )
                                    Column(Modifier.weight(1f)) {
                                        Text(item.type, style = MaterialTheme.typography.bodyMedium)
                                        Text(item.colorName, style = MaterialTheme.typography.bodyMedium)
                                        Text("色号：${item.colorCode}", style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                        if (filteredConsumables.isEmpty()) {
                            Text("未找到匹配耗材。", style = MaterialTheme.typography.bodySmall)
                        }
                        Button(
                            onClick = {
                                val uidValue = bindUid.trim()
                                val cid = bindConsumableId
                                if (uidValue.isNotBlank() && cid != null) {
                                    scope.launch {
                                        withContext(Dispatchers.IO) {
                                            tagConfigDao.upsertBinding(uidValue, cid)
                                        }
                                    }
                                } else {
                                    configError = "请填写 UID 并选择耗材"
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("绑定/更新")
                        }
                        Text("提示：重复 UID 会覆盖原绑定。", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        if (tagConfigs.isEmpty()) {
            item {
                Text("暂无标签配置。", style = MaterialTheme.typography.bodySmall)
            }
        } else {
            items(tagConfigs, key = { it.id }) { item ->
                val cons = consumableMap[item.consumableId]
                ElevatedCard(shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("UID：${item.uidHex}", style = MaterialTheme.typography.bodyMedium)
                            if (cons != null) {
                                Text(cons.type, style = MaterialTheme.typography.bodySmall)
                                Text(cons.colorName, style = MaterialTheme.typography.bodySmall)
                                Text("色号：${cons.colorCode}", style = MaterialTheme.typography.bodySmall)
                            } else {
                                Text("耗材已删除", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        IconButton(onClick = {
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    tagConfigDao.delete(item)
                                }
                            }
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "删除")
                        }
                    }
                }
            }
        }
    }

    if (editingConsumable != null) {
        val editing = editingConsumable!!
        val bindings = bindingsByConsumable[editing.id].orEmpty()
        var editType by rememberSaveable(editing.id) { mutableStateOf(editing.type) }
        var editColorName by rememberSaveable(editing.id) { mutableStateOf(editing.colorName) }
        var editColorCode by rememberSaveable(editing.id) { mutableStateOf(editing.colorCode) }
        var editColorValue by rememberSaveable(editing.id) {
            mutableStateOf(editing.colorValuesHex.replace(",", " "))
        }

        AlertDialog(
            onDismissRequest = { editingConsumable = null },
            title = { Text("编辑耗材") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = editType,
                        onValueChange = { editType = it },
                        label = { Text("耗材类型") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editColorName,
                        onValueChange = { editColorName = it },
                        label = { Text("色名") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editColorCode,
                        onValueChange = { editColorCode = it },
                        label = { Text("色号") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editColorValue,
                        onValueChange = { editColorValue = it },
                        label = { Text("色值（多色用空格分隔）") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (bindings.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Text("绑定UID：", style = MaterialTheme.typography.bodySmall)
                        bindings.forEach { binding ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(binding.uidHex, style = MaterialTheme.typography.bodySmall)
                                IconButton(
                                    onClick = {
                                        scope.launch {
                                            withContext(Dispatchers.IO) {
                                                tagConfigDao.delete(binding)
                                            }
                                        }
                                    }
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "删除绑定")
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val parsedColors = parseMultiColorInput(editColorValue)
                    val colorInt = parsedColors.firstOrNull()
                    if (colorInt == null || editType.isBlank() || editColorName.isBlank() || editColorCode.isBlank()) {
                        configError = "编辑失败：请完整填写且色值格式正确，多色用空格分隔"
                        return@TextButton
                    }
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            consumableDao.update(
                                editing.copy(
                                    type = editType.trim(),
                                    colorName = editColorName.trim(),
                                    colorCode = editColorCode.trim(),
                                    colorValueArgb = colorInt,
                                    colorValuesHex = parsedColors.joinToString(",") { formatColorHex(it) }
                                )
                            )
                        }
                        editingConsumable = null
                    }
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { editingConsumable = null }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun AboutScreen(modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current
    val boostLink = "bambulab://bbl/design/model/detail?design_id=2019552&instance_id=2251734&appSharePlatform=copy"
    val linkColor = MaterialTheme.colorScheme.primary
    val boostText = buildAnnotatedString {
        append("助力：")
        pushStringAnnotation(tag = "URL", annotation = boostLink)
        withStyle(style = SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)) {
            append("打开 Bambu APP")
        }
        pop()
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Text("BambuColor", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text("版本：${"v1.0"}", style = MaterialTheme.typography.bodyMedium)
        Text("作者：m0h31h31", style = MaterialTheme.typography.bodyMedium)
        Text(
            "用途：拓竹耗材NFC标签重复利用，通过手机 NFC 快速识别标签并绑定耗材信息。解决料盘混乱不好区分的问题!",
            style = MaterialTheme.typography.bodyMedium
        )
        Text("功能概览：", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Text(
            "• NFC 读取标签 UID\n" +
                "• 标签与耗材信息绑定\n" +
                "• 耗材库增删改查\n" +
                "• 多色耗材渐变展示",
            style = MaterialTheme.typography.bodySmall
        )
        Text("使用流程：", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Text(
            "1) 主页刷卡识别标签\n" +
                "2) 已绑定直接显示耗材信息\n" +
                "3) 未绑定选择耗材完成绑定\n" +
                "4) 配置页可编辑耗材与绑定",
            style = MaterialTheme.typography.bodySmall
        )
        Text("数据导入：", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Text(
            "支持从 Android/data/com.m0h31h31.bambucolor/files/filaments_color_codes.json 导入耗材颜色数据。\n文件来源于拓竹PC安装程序目录下的 resources/profiles/BBL/filament/filaments_color_codes.json",
            style = MaterialTheme.typography.bodySmall
        )
        Text("多色说明：", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Text(
            "多色耗材使用渐变色块展示，添加多色值以空格分隔多个颜色。",
            style = MaterialTheme.typography.bodySmall
        )
        Text("注意事项：", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Text(
            "请确保 NFC 已开启；删除耗材会先解绑关联标签。",
            style = MaterialTheme.typography.bodySmall
        )
        ClickableText(
            text = boostText,
            style = MaterialTheme.typography.bodySmall,
            onClick = { offset ->
                boostText.getStringAnnotations(tag = "URL", start = offset, end = offset)
                    .firstOrNull()
                    ?.let { uriHandler.openUri(it.item) }
            }
        )
    }
}

private fun parseColorInt(input: String): Int? {
    val value = input.trim()
    if (value.isEmpty()) return null
    val hex = value.removePrefix("#")
    if (hex.length != 6 && hex.length != 8) return null
    val parsed = hex.toLongOrNull(16) ?: return null
    return if (hex.length == 6) {
        (0xFF000000 or parsed).toInt()
    } else {
        parsed.toInt()
    }
}

private fun formatColorHex(argb: Int): String {
    return String.format("#%08X", argb)
}

private fun formatColorLabel(colorValuesHex: String, fallbackArgb: Int): String {
    val colors = parseColorList(colorValuesHex, fallbackArgb)
    return if (colors.size > 1) {
        "多色(${colors.size})"
    } else {
        formatColorHex(fallbackArgb)
    }
}

private fun readConsumablesJson(context: android.content.Context): String {
    val external = getExternalJsonFile(context)
    return if (external.exists()) {
        external.readText(Charsets.UTF_8)
    } else {
        throw IllegalStateException("未找到外部配置文件，请放到 Android/data/${context.packageName}/files/")
    }
}

private fun getExternalJsonFile(context: android.content.Context): File {
    val dir = context.getExternalFilesDir(null) ?: context.filesDir
    return File(dir, "filaments_color_codes.json")
}

private fun parseConsumablesFromJson(json: String): List<ConsumableEntity> {
    val root = JSONObject(json)
    val data = root.optJSONArray("data") ?: return emptyList()
    val items = ArrayList<ConsumableEntity>(data.length())
    for (i in 0 until data.length()) {
        val obj = data.optJSONObject(i) ?: continue
        val typeRaw = obj.optString("fila_type", "").trim()
        val type = if (typeRaw.startsWith("Bambu ", ignoreCase = true)) typeRaw else "Bambu $typeRaw"
        val colorCode = obj.optString("fila_color_code", "").trim()
        val colorNameObj = obj.optJSONObject("fila_color_name")
        val colorName = when {
            colorNameObj?.has("zh") == true -> colorNameObj.optString("zh", "")
            colorNameObj?.has("en") == true -> colorNameObj.optString("en", "")
            else -> ""
        }.trim()
        val colorArray = obj.optJSONArray("fila_color")
        val colorValuesArgb = ArrayList<Int>()
        if (colorArray != null) {
            for (j in 0 until colorArray.length()) {
                val hex = colorArray.optString(j, "").trim()
                val argb = parseRgbaOrRgbHexToArgb(hex)
                if (argb != null) {
                    colorValuesArgb.add(argb)
                }
            }
        }
        if (colorValuesArgb.isEmpty()) continue
        val colorValueArgb = colorValuesArgb.first()
        if (type.isBlank() || colorName.isBlank() || colorCode.isBlank()) continue
        items.add(
            ConsumableEntity(
                type = type,
                colorName = colorName,
                colorValueArgb = colorValueArgb,
                colorCode = colorCode,
                colorValuesHex = colorValuesArgb.joinToString(",") { formatColorHex(it) }
            )
        )
    }
    return items
}

private fun parseRgbaOrRgbHexToArgb(input: String): Int? {
    val value = input.trim().removePrefix("#")
    if (value.length != 6 && value.length != 8) return null
    val parsed = value.toLongOrNull(16) ?: return null
    return if (value.length == 6) {
        (0xFF000000 or parsed).toInt()
    } else {
        val r = (parsed shr 24) and 0xFF
        val g = (parsed shr 16) and 0xFF
        val b = (parsed shr 8) and 0xFF
        val a = parsed and 0xFF
        ((a shl 24) or (r shl 16) or (g shl 8) or b).toInt()
    }
}

private fun ConsumableEntity.uniqueKey(): String {
    return "${type.trim().lowercase()}|${colorName.trim().lowercase()}|${colorCode.trim().lowercase()}"
}

private fun parseColorList(colorValuesHex: String, fallbackArgb: Int): List<Color> {
    val colors = colorValuesHex.split(',')
        .mapNotNull { parseColorInt(it) }
        .map { Color(it) }
    return if (colors.isNotEmpty()) colors else listOf(Color(fallbackArgb))
}

private fun parseMultiColorInput(input: String): List<Int> {
    return input.trim()
        .split(Regex("\\s+"))
        .mapNotNull { parseColorInt(it.trim()) }
}

private fun ConsumableEntity.matchesQuery(query: String): Boolean {
    val q = query.trim().lowercase()
    if (q.isEmpty()) return true
    val fields = listOf(
        id.toString(),
        type,
        colorName,
        colorCode,
        colorValuesHex,
        formatColorHex(colorValueArgb)
    )
    return fields.any { it.lowercase().contains(q) }
}
