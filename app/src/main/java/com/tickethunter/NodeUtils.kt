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
            val t = nodeText(node)
            texts.any { t.contains(it) } && node.isVisibleToUser
        }
    }

    fun findSoldOut(root: AccessibilityNodeInfo, extra: List<String> = emptyList()): Boolean {
        val soldOutTexts = listOf("全部售罄", "已售罄", "暂时无票", "缺货登记") + extra
        return soldOutTexts.any { text ->
            !root.findAccessibilityNodeInfosByText(text).isNullOrEmpty()
        }
    }

    fun findTierNode(root: AccessibilityNodeInfo, tier: Int): TierMatch? =
        findAvailableTierNode(root, tier)

    fun findAvailableTierNode(root: AccessibilityNodeInfo, tier: Int): TierMatch? {
        val candidates = walkAll(root).filter { node ->
            val text = nodeText(node)
            TierMatcher.matchesText(text, tier) && node.isVisibleToUser
        }
        for (node in candidates) {
            if (isTierSoldOut(node)) continue
            val target = bestClickTarget(node) ?: node
            if (!target.isEnabled) continue
            return TierMatch(tier, target)
        }
        return null
    }

    fun hasBuyButton(root: AccessibilityNodeInfo, texts: List<String>): Boolean =
        findByTexts(root, texts) != null

    fun hasText(root: AccessibilityNodeInfo, texts: List<String>): Boolean {
        return texts.any { !root.findAccessibilityNodeInfosByText(it).isNullOrEmpty() }
    }

    fun isTierSoldOut(node: AccessibilityNodeInfo): Boolean {
        var current: AccessibilityNodeInfo? = node
        var depth = 0
        while (current != null && depth < 4) {
            if (TierMatcher.isSoldOutContext(nodeText(current))) return true
            current = current.parent
            depth++
        }
        for (child in childrenOf(node, 2)) {
            if (TierMatcher.isSoldOutContext(nodeText(child))) return true
        }
        return false
    }

    fun bestClickTarget(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isClickable) return node
        return findClickableAncestor(node)
    }

    fun findClickableAncestor(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var parent = node.parent
        var depth = 0
        while (parent != null && depth < 5) {
            if (parent.isClickable && parent.isVisibleToUser) return parent
            parent = parent.parent
            depth++
        }
        return null
    }

    fun findPlusButton(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        return dfs(root) { node ->
            val t = nodeText(node)
            (t == "+" || t == "＋" || t.contains("加")) &&
                node.isClickable && node.isVisibleToUser
        }
    }

    fun findSessionOption(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val markers = listOf("场次", "选择场次")
        if (findByTexts(root, markers) == null) return null
        return dfs(root) { node ->
            if (!node.isVisibleToUser || !node.isEnabled) return@dfs false
            val t = nodeText(node)
            val looksLikeSession = t.contains("场") || t.contains("月") || t.contains("周")
            val available = listOf("可售", "有票", "可购", "预售", "在售").any { t.contains(it) }
            node.isClickable && (available || (looksLikeSession && !TierMatcher.isSoldOutContext(t)))
        }
    }

    private fun nodeText(node: AccessibilityNodeInfo): String =
        node.text?.toString() ?: node.contentDescription?.toString() ?: ""

    private fun childrenOf(node: AccessibilityNodeInfo, maxDepth: Int): List<AccessibilityNodeInfo> {
        val result = mutableListOf<AccessibilityNodeInfo>()
        fun walk(n: AccessibilityNodeInfo, depth: Int) {
            if (depth > maxDepth) return
            for (i in 0 until n.childCount) {
                val child = n.getChild(i) ?: continue
                result.add(child)
                walk(child, depth + 1)
            }
        }
        walk(node, 1)
        return result
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
