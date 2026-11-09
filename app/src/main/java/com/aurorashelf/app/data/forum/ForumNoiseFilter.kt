package com.aurorashelf.app.data.forum

import java.util.Locale

/**
 * 社区垃圾内容与官方无用公告强力过滤管道
 * 遵循与 iOS ForumNoiseFilter 严格双端对齐规范
 */
internal object ForumNoiseFilter {
    val noticeKeywords: List<String> = listOf(
        "91网须知", "91论坛须知", "论坛须知", "本版须知", "发帖须知", "版面须知",
        "版规", "总版规", "新手必读", "新手须知", "置顶公告", "新手指南", "站务公告", "本站须知",
        "论坛手机版", "手机版使用", "手机版教程", "手机客户端", "手机浏览",
        "如何获取", "邀请码", "获取91", "论坛邀请", "注册邀请", "购买邀请", "邀请码发放", "求邀请",
        "新手入门", "防封指南", "最新地址", "永久地址", "地址发布", "发布页", "网址发布",
        "回家地址", "防丢失", "请收藏", "防屏蔽", "永久域名", "官方域名", "防封教程",
    )

    val spamKeywords: List<String> = listOf(
        "广告", "赞助商", "特约", "官方合作", "商务合作", "招租",
        "博彩", "彩票", "棋牌", "真人视讯", "澳门银河", "开元棋牌", "太阳城", "威尼斯人",
        "代理加盟", "高薪兼职", "代充", "话费充值", "币充值", "usdt", "USDT", "虚拟币",
        "加微信", "加v", "加微", "微信号", "薇信", "进群", "tg群", "电报群",
        "telegram", "扫码进群", "福利群", "约炮群", "修车群", "外围女", "品茶",
        "充值优惠", "返利", "开奖", "彩种", "投注", "包赢",
    )

    val blockedPrefixes: List<String> = listOf(
        "【公告】", "[公告]", "【置顶】", "[置顶]", "【版务】", "[版务]",
        "【注意】", "[注意]", "【通知】", "[通知]", "【必读】", "[必读]",
        "【版规】", "[版规]", "【提醒】", "[提醒]",
    )

    fun isNoisePost(title: String): Boolean {
        val trimmed = title.trim()
        if (trimmed.length < 4) return true

        val alphanumericOrChinese = trimmed.filter { it.isLetterOrDigit() }
        if (alphanumericOrChinese.length < 2) return true

        for (prefix in blockedPrefixes) {
            if (trimmed.startsWith(prefix)) return true
        }

        val normalized = trimmed.lowercase(Locale.ROOT)
            .replace(" ", "")
            .replace("\t", "")
            .replace("【", "")
            .replace("】", "")
            .replace("[", "")
            .replace("]", "")
            .replace("-", "")
            .replace("_", "")

        for (kw in noticeKeywords) {
            val cleanKw = kw.lowercase(Locale.ROOT).replace(" ", "")
            if (normalized.contains(cleanKw)) return true
        }

        for (kw in spamKeywords) {
            val cleanKw = kw.lowercase(Locale.ROOT).replace(" ", "")
            if (normalized.contains(cleanKw)) return true
        }

        return false
    }
}
