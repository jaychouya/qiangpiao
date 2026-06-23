package com.tickethunter

import android.view.accessibility.AccessibilityNodeInfo

object NodeUtils {
    fun findByTexts(root: AccessibilityNodeInfo, texts: List<String>): AccessibilityNodeInfo? {
        for (text in texts) {
            val nodes = root.findAccessibilityNodeInfosByText(text)
            if (!nodes.isNullOrEmpty()) {
                val clickable = nodes.firstOrNull { it.isVisibleToUser }
                if (clickable != null) return clickable
            }
        }
        return dfs(root) { node ->
            val t = node.text?.toString() ?: node.contentDescription?.toString() ?: ""
            texts.any { t.contains(it) } && node.isVisibleToUser
        }
    }

    fun findSoldOut(root: AccessibilityNodeInfo, extra: List<String> = emptyList()): Boolean {
        val soldOutTexts = listOf("缺货登记", "已售罄", "暂时无票", "售罄") + extra
        return soldOutTexts.any { text ->
            !root.findAccessibilityNodeInfosByText(text).isNullOrEmpty()
        }
    }

    fun findTierNode(root: AccessibilityNodeInfo, tier: Int): TierMatch? {
        walkAll(root).forEach { node ->
            val text = node.text?.toString() ?: node.contentDescription?.toString() ?: ""
            if (TierMatcher.matchesText(text, tier) && node.isVisibleToUser) {
                return TierMatch(tier, node)
            }
        }
        return null
    }

    fun hasText(root: AccessibilityNodeInfo, texts: List<String>): Boolean {
        return texts.any { !root.findAccessibilityNodeInfosByText(it).isNullOrEmpty() }
    }

    private fun dfs(
        node: AccessibilityNodeInfo,
        match: (AccessibilityNodeInfo) -> Boolean
    ): AccessibilityNodeInfo? {
        if (match(node)) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = dfs(child, match)
            if (found != null) return found
        }
        return null
    }

    private fun walkAll(node: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val result = mutableListOf<AccessibilityNodeInfo>()
        fun walk(n: AccessibilityNodeInfo) {
            result.add(n)
            for (i in 0 until n.childCount) {
                n.getChild(i)?.let { walk(it) }
            }
        }
        walk(node)
        return result
    }
}
