package com.vuvuv.usernamelab

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFF70B9FF),
                    secondary = Color(0xFFB69CFF),
                    background = Color(0xFF07090D),
                    surface = Color(0xFF11151D),
                )
            ) {
                UsernameLabScreen()
            }
        }
    }
}

@Composable
private fun UsernameLabScreen() {
    val context = LocalContext.current
    val store = remember { FilterStore(context) }
    val repo = remember { CandidateRepository(context) }

    var filters by remember { mutableStateOf(store.get()) }
    var candidate by remember { mutableStateOf(repo.next(filters) ?: "—") }
    var refresh by remember { mutableIntStateOf(0) }
    var customRefresh by remember { mutableIntStateOf(0) }
    var pasteDialog by remember { mutableStateOf(false) }
    var pasteText by remember { mutableStateOf("") }
    var importMessage by remember { mutableStateOf<String?>(null) }

    val history = remember(refresh) { repo.history() }
    val customCount = remember(customRefresh) { repo.customCount() }

    fun updateFilters(next: CandidateFilters) {
        val safe = if (
            !next.meaningful && !next.randomFive && !next.translit &&
            !next.compounds && !next.shuzoGram && !next.custom
        ) next.copy(meaningful = true) else next

        filters = safe
        store.save(safe)
        candidate = repo.next(safe) ?: "—"
    }

    fun activateCustomOnly() {
        val customOnly = CandidateFilters(
            meaningful = false,
            randomFive = false,
            translit = false,
            compounds = false,
            shuzoGram = false,
            custom = true,
            exactFive = false,
        )
        filters = customOnly
        store.save(customOnly)
        candidate = repo.next(customOnly) ?: "—"
    }

    fun importCustom(text: String, append: Boolean) {
        val count = repo.importCustomText(text, append)
        customRefresh++
        activateCustomOnly()
        importMessage = "Загружено юзов: $count. Сейчас включён режим «Только мои»."
    }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)
                    ?.bufferedReader()
                    ?.use { it.readText() }
                    ?: ""
            }.onSuccess { text ->
                importCustom(text, append = true)
            }.onFailure {
                importMessage = "Не удалось прочитать файл."
            }
        }
    }

    if (pasteDialog) {
        AlertDialog(
            onDismissRequest = { pasteDialog = false },
            title = { Text("Вставить свои юзы") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Можно вставлять по одному в строке, через пробел/запятую или вместе с @. Разрешены 5–32 символа: A–Z, a–z, 0–9 и _.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                    OutlinedTextField(
                        value = pasteText,
                        onValueChange = { pasteText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 160.dp, max = 320.dp),
                        placeholder = { Text("@trade\npizza\nMyUser\nOmniHash") },
                        minLines = 7
                    )
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(
                        enabled = pasteText.isNotBlank(),
                        onClick = {
                            importCustom(pasteText, append = false)
                            pasteText = ""
                            pasteDialog = false
                        }
                    ) {
                        Text("Заменить")
                    }
                    Button(
                        enabled = pasteText.isNotBlank(),
                        onClick = {
                            importCustom(pasteText, append = true)
                            pasteText = ""
                            pasteDialog = false
                        }
                    ) {
                        Text("Добавить")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { pasteDialog = false }) {
                    Text("Отмена")
                }
            }
        )
    }

    val background = Brush.verticalGradient(
        listOf(Color(0xFF05070A), Color(0xFF0B111B), Color(0xFF07090D))
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(background)
    ) {
        Box(
            Modifier
                .size(280.dp)
                .offset(x = 220.dp, y = (-80).dp)
                .background(
                    Brush.radialGradient(
                        listOf(Color(0x444D9FFF), Color.Transparent)
                    ),
                    RoundedCornerShape(999.dp)
                )
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Text(
                    "Vuvuv Username Lab",
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Встроенные базы + свой личный список юзов.",
                    color = Color(0xFF9299A6),
                    fontSize = 14.sp
                )
            }

            item {
                GlassCard {
                    Text(
                        "Мои юзы",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Сейчас сохранено: $customCount. Можно загрузить TXT/CSV или просто вставить слова.",
                        color = Color(0xFF8992A0),
                        fontSize = 13.sp
                    )

                    Spacer(Modifier.height(12.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = {
                                filePicker.launch(
                                    arrayOf(
                                        "text/plain",
                                        "text/csv",
                                        "application/octet-stream"
                                    )
                                )
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.FolderOpen, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Файл")
                        }

                        OutlinedButton(
                            onClick = { pasteDialog = true },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.Edit, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Вставить")
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        FilledTonalButton(
                            enabled = customCount > 0,
                            onClick = { activateCustomOnly() },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Только мои")
                        }

                        OutlinedButton(
                            enabled = customCount > 0,
                            onClick = {
                                repo.clearCustom()
                                customRefresh++
                                if (filters.custom) {
                                    updateFilters(filters.copy(custom = false))
                                }
                                importMessage = "Мой список очищен."
                            }
                        ) {
                            Icon(Icons.Rounded.Delete, null)
                        }
                    }

                    if (importMessage != null) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            importMessage!!,
                            color = Color(0xFF70B9FF),
                            fontSize = 12.sp
                        )
                    }
                }
            }

            item {
                GlassCard {
                    Text(
                        "Фильтры",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Категории можно включать одновременно.",
                        color = Color(0xFF8992A0),
                        fontSize = 13.sp
                    )
                    Spacer(Modifier.height(10.dp))

                    FilterRow(
                        "Мои юзы",
                        "Использует только слова, которые ты добавил сам.",
                        filters.custom
                    ) { updateFilters(filters.copy(custom = it)) }

                    FilterRow(
                        "Осмысленные",
                        "Обычные английские слова со значением.",
                        filters.meaningful
                    ) { updateFilters(filters.copy(meaningful = it)) }

                    FilterRow(
                        "Рандомные 5 букв",
                        "Случайные пятибуквенные сочетания.",
                        filters.randomFive
                    ) { updateFilters(filters.copy(randomFive = it)) }

                    FilterRow(
                        "Транслитом",
                        "Отдельный набор русских слов и сленга латиницей.",
                        filters.translit
                    ) { updateFilters(filters.copy(translit = it)) }

                    FilterRow(
                        "Два слова / modern",
                        "Слитно и с заглавной буквой второго слова: OmniHash, NeonVault.",
                        filters.compounds
                    ) { updateFilters(filters.copy(compounds = it)) }

                    FilterRow(
                        "Для ShuzoGram",
                        "Отдельный пул; в обычный поиск не подмешивается.",
                        filters.shuzoGram
                    ) { updateFilters(filters.copy(shuzoGram = it)) }

                    Spacer(Modifier.height(8.dp))
                    Text("Длина", color = Color.White, fontWeight = FontWeight.SemiBold)

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = filters.exactFive,
                            onClick = { updateFilters(filters.copy(exactFive = true)) },
                            label = { Text("ровно 5") }
                        )
                        FilterChip(
                            selected = !filters.exactFive,
                            onClick = { updateFilters(filters.copy(exactFive = false)) },
                            label = { Text("5–32") }
                        )
                    }
                }
            }

            item {
                GlassCard {
                    Text("Кандидат", color = Color(0xFF9199A8), fontSize = 13.sp)
                    Text(
                        candidate,
                        color = Color.White,
                        fontSize = 31.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { candidate = repo.next(filters) ?: "—" }
                        ) {
                            Icon(Icons.Rounded.Refresh, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Следующий")
                        }

                        OutlinedButton(
                            onClick = {
                                if (candidate != "—") {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(
                                        ClipData.newPlainText("username", candidate)
                                    )
                                }
                            }
                        ) {
                            Icon(Icons.Rounded.ContentCopy, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Копировать")
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                if (candidate != "—") {
                                    repo.mark(candidate, CandidateStatus.TAKEN)
                                    refresh++
                                    candidate = repo.next(filters) ?: "—"
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF6A2730)
                            )
                        ) {
                            Text("Занят")
                        }

                        Button(
                            onClick = {
                                if (candidate != "—") {
                                    repo.mark(candidate, CandidateStatus.FREE)
                                    refresh++
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF17633D)
                            )
                        ) {
                            Text("Свободен")
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Занятые варианты сохраняются и больше не выдаются, в том числе из твоего списка.",
                        color = Color(0xFF798291),
                        fontSize = 12.sp
                    )
                }
            }

            item {
                val counts = remember(refresh) { repo.counts() }
                Text(
                    "История · занято ${counts.first} · свободно ${counts.second}",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
            }

            items(
                items = history,
                key = { it.first }
            ) { (name, status) ->
                HistoryRow(name, status)
            }

            item { Spacer(Modifier.height(28.dp)) }
        }
    }
}

@Composable
private fun FilterRow(
    title: String,
    hint: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontWeight = FontWeight.Medium)
            Text(hint, color = Color(0xFF727B89), fontSize = 12.sp)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun HistoryRow(name: String, status: CandidateStatus) {
    val isFree = status == CandidateStatus.FREE
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0x7712161E))
            .border(1.dp, Color(0x223A4351), RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(name, color = Color.White, modifier = Modifier.weight(1f))
        Text(
            if (isFree) "свободен" else "занят",
            color = if (isFree) Color(0xFF63DB91) else Color(0xFFFF6F78),
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun GlassCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xB5121720))
            .border(1.dp, Color(0x334C5D72), RoundedCornerShape(24.dp))
            .padding(16.dp),
        content = content
    )
}
