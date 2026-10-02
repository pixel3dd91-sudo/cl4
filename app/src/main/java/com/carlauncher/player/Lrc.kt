package com.carlauncher.player

import java.io.File

/** خواندن متن آهنگ از فایل .lrc کنار فایل موزیک */
object Lrc {
    private val re = Regex("\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?\\]")

    fun parse(text: String): List<Pair<Long, String>> {
        val out = ArrayList<Pair<Long, String>>()
        for (line in text.lines()) {
            val ms = re.findAll(line).toList()
            if (ms.isEmpty()) continue
            val txt = line.substring(ms.last().range.last + 1).trim()
            for (m in ms) {
                val min = m.groupValues[1].toLong()
                val sec = m.groupValues[2].toLong()
                val fr = m.groupValues[3]
                val frac = if (fr.isEmpty()) 0L else fr.padEnd(3, '0').take(3).toLong()
                out.add(Pair(min * 60000 + sec * 1000 + frac, txt))
            }
        }
        out.sortBy { it.first }
        return out
    }

    fun loadFor(t: Track): List<Pair<Long, String>> {
        val p = t.path ?: return emptyList()
        try {
            val base = p.substringBeforeLast('.', p)
            for (ext in arrayOf(".lrc", ".LRC")) {
                val f = File(base + ext)
                if (f.exists() && f.canRead()) return parse(f.readText(Charsets.UTF_8))
            }
        } catch (_: Throwable) {
        }
        return emptyList()
    }
}
