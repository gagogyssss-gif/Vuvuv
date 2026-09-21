package com.vuvuv.usernamelab

import android.content.Context
import org.json.JSONArray
import kotlin.random.Random

data class CandidateFilters(
    val meaningful: Boolean = true,
    val randomFive: Boolean = false,
    val translit: Boolean = false,
    val compounds: Boolean = false,
    val shuzoGram: Boolean = false,
    val custom: Boolean = false,
    val exactFive: Boolean = true,
)

enum class CandidateStatus { TAKEN, FREE }

class FilterStore(context: Context) {
    private val prefs = context.getSharedPreferences("filters", Context.MODE_PRIVATE)

    fun get(): CandidateFilters = CandidateFilters(
        meaningful = prefs.getBoolean("meaningful", true),
        randomFive = prefs.getBoolean("randomFive", false),
        translit = prefs.getBoolean("translit", false),
        compounds = prefs.getBoolean("compounds", false),
        shuzoGram = prefs.getBoolean("shuzoGram", false),
        custom = prefs.getBoolean("custom", false),
        exactFive = prefs.getBoolean("exactFive", true),
    )

    fun save(v: CandidateFilters) {
        prefs.edit()
            .putBoolean("meaningful", v.meaningful)
            .putBoolean("randomFive", v.randomFive)
            .putBoolean("translit", v.translit)
            .putBoolean("compounds", v.compounds)
            .putBoolean("shuzoGram", v.shuzoGram)
            .putBoolean("custom", v.custom)
            .putBoolean("exactFive", v.exactFive)
            .apply()
    }
}

class CandidateRepository(private val context: Context) {
    private val prefs = context.getSharedPreferences("candidate_state", Context.MODE_PRIVATE)
    private val sessionSeen = LinkedHashSet<String>()
    private val customFileName = "custom_candidates.txt"

    private val general by lazy { readAsset("general_candidates.txt") }
    private val shuzo by lazy { readAsset("shuzogram_candidates.txt") }
    private val translit by lazy { readAsset("translit_candidates.txt") }

    private fun readAsset(name: String): List<String> =
        context.assets.open(name).bufferedReader().useLines { lines ->
            lines.map { it.trim() }
                .filter { it.length in 5..32 && it.matches(Regex("[A-Za-z0-9_]+")) }
                .distinctBy { it.lowercase() }
                .toList()
        }

    private fun readCustom(): List<String> = runCatching {
        context.openFileInput(customFileName).bufferedReader().useLines { lines ->
            lines.map { it.trim() }
                .filter { it.length in 5..32 && it.matches(Regex("[A-Za-z0-9_]+")) }
                .distinctBy { it.lowercase() }
                .toList()
        }
    }.getOrDefault(emptyList())

    private fun taken(): Set<String> = prefs.getStringSet("taken", emptySet()) ?: emptySet()
    private fun free(): Set<String> = prefs.getStringSet("free", emptySet()) ?: emptySet()

    fun next(filters: CandidateFilters): String? {
        val blocked = (taken() + free() + sessionSeen).mapTo(HashSet()) { it.lowercase() }
        val candidates = ArrayList<String>(4096)

        fun addCandidate(value: String) {
            if (lengthOk(value, filters.exactFive) && value.lowercase() !in blocked) {
                candidates += value
            }
        }

        if (filters.meaningful || filters.compounds) {
            general.forEach { word ->
                val isCompound = word.drop(1).any { it.isUpperCase() }
                val use = (filters.meaningful && !isCompound) || (filters.compounds && isCompound)
                if (use) addCandidate(word)
            }
        }

        if (filters.translit) translit.forEach(::addCandidate)
        if (filters.shuzoGram) shuzo.forEach(::addCandidate)
        if (filters.custom) readCustom().forEach(::addCandidate)

        if (filters.randomFive) {
            repeat(128) {
                val random = buildString(5) {
                    repeat(5) { append(('a'.code + Random.nextInt(26)).toChar()) }
                }
                addCandidate(random)
            }
        }

        if (candidates.isEmpty()) return null
        val result = candidates[Random.nextInt(candidates.size)]
        sessionSeen += result
        return result
    }

    private fun lengthOk(value: String, exactFive: Boolean): Boolean =
        if (exactFive) value.length == 5 else value.length in 5..32

    fun importCustomText(text: String, append: Boolean): Int {
        val incoming = parseCustomText(text)
        val combined = if (append) readCustom() + incoming else incoming
        val unique = LinkedHashMap<String, String>()
        combined.forEach { unique.putIfAbsent(it.lowercase(), it) }

        context.openFileOutput(customFileName, Context.MODE_PRIVATE).bufferedWriter().use { writer ->
            unique.values.forEach {
                writer.append(it)
                writer.newLine()
            }
        }
        sessionSeen.clear()
        return unique.size
    }

    fun customCount(): Int = readCustom().size

    fun clearCustom() {
        runCatching { context.deleteFile(customFileName) }
        sessionSeen.clear()
    }

    private fun parseCustomText(text: String): List<String> {
        val tokens = Regex("@?[A-Za-z0-9_]{5,32}")
            .findAll(text)
            .map { it.value.removePrefix("@") }
            .filter { it.matches(Regex("[A-Za-z0-9_]{5,32}")) }
            .toList()

        val unique = LinkedHashMap<String, String>()
        tokens.forEach { unique.putIfAbsent(it.lowercase(), it) }
        return unique.values.toList()
    }

    fun mark(name: String, status: CandidateStatus) {
        val taken = taken().toMutableSet()
        val free = free().toMutableSet()

        when (status) {
            CandidateStatus.TAKEN -> {
                free.remove(name)
                taken.add(name)
            }
            CandidateStatus.FREE -> {
                taken.remove(name)
                free.add(name)
            }
        }

        prefs.edit()
            .putStringSet("taken", taken)
            .putStringSet("free", free)
            .putString("history", prependHistory(name, status))
            .apply()
    }

    fun history(limit: Int = 80): List<Pair<String, CandidateStatus>> {
        val raw = prefs.getString("history", "[]") ?: "[]"
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until minOf(array.length(), limit)) {
                    val item = array.getString(i)
                    val sep = item.indexOf('|')
                    if (sep > 0) {
                        val status = if (item.substring(0, sep) == "F") CandidateStatus.FREE else CandidateStatus.TAKEN
                        add(item.substring(sep + 1) to status)
                    }
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun prependHistory(name: String, status: CandidateStatus): String {
        val fresh = mutableListOf((if (status == CandidateStatus.FREE) "F|" else "T|") + name)
        history(99)
            .filter { it.first != name }
            .take(99)
            .forEach { (oldName, oldStatus) ->
                fresh += (if (oldStatus == CandidateStatus.FREE) "F|" else "T|") + oldName
            }
        return JSONArray(fresh).toString()
    }

    fun counts(): Pair<Int, Int> = taken().size to free().size
}
