package com.example.util

import com.example.data.network.NetworkConfig

object NetworkSecurityHelper {

    data class SecurityOptions(
        val disableInsecureServices: Boolean = true,
        val blockPortScan: Boolean = true,
        val blockBruteForceWinbox: Boolean = true,
        val preventDnsPoisoning: Boolean = true,
        val dropInvalidPackets: Boolean = true,
        val enableClientIsolation: Boolean = true,
        val customWinboxPort: Int = 8291
    )

    fun generateRouterOsHardeningScript(
        config: NetworkConfig,
        options: SecurityOptions
    ): String {
        val sb = StringBuilder()
        sb.appendLine("# ===========================================================")
        sb.appendLine("# SamMikrotik - سكربت حماية وتأمين راوتر ميكروتك (RouterOS v7)")
        sb.appendLine("# اسم الشبكة: ${config.networkName}")
        sb.appendLine("# الموديل: ${config.mainRouterModel}")
        sb.appendLine("# رينج الإدارة المعتمد: ${config.approvedSubnet}")
        sb.appendLine("# تم التوليد بواسطة SamMikrotik 2026 Engine")
        sb.appendLine("# ===========================================================")
        sb.appendLine()

        if (options.disableInsecureServices) {
            sb.appendLine("# 1. إيقاف الخدمات غير المشفرة والمعرضة للاختراق")
            sb.appendLine("/ip service set telnet disabled=yes")
            sb.appendLine("/ip service set ftp disabled=yes")
            sb.appendLine("/ip service set www disabled=yes")
            sb.appendLine("/ip service set api disabled=yes")
            sb.appendLine("/ip service set api-ssl disabled=yes")
            sb.appendLine("/ip service set winbox port=${options.customWinboxPort} address=\"${config.approvedSubnet}\"")
            sb.appendLine()
        }

        if (options.dropInvalidPackets || options.blockPortScan || options.blockBruteForceWinbox) {
            sb.appendLine("# 2. جدار الحماية وقواعد التصفية (Firewall Filter Rules)")
            sb.appendLine("/ip firewall filter")
            
            if (options.dropInvalidPackets) {
                sb.appendLine("add chain=input connection-state=invalid action=drop comment=\"SamMikrotik: Drop Invalid Input\"")
                sb.appendLine("add chain=forward connection-state=invalid action=drop comment=\"SamMikrotik: Drop Invalid Forward\"")
                sb.appendLine("add chain=input connection-state=established,related action=accept comment=\"SamMikrotik: Accept Established/Related\"")
            }

            if (options.blockPortScan) {
                sb.appendLine("add chain=input protocol=tcp psd=21,3s,3,1 action=add-src-to-address-list address-list=\"Port_Scanners\" address-list-timeout=14d comment=\"SamMikrotik: Detect Port Scanners\"")
                sb.appendLine("add chain=input src-address-list=\"Port_Scanners\" action=drop comment=\"SamMikrotik: Drop Port Scanners\"")
            }

            if (options.blockBruteForceWinbox) {
                sb.appendLine("add chain=input protocol=tcp dst-port=${options.customWinboxPort} connection-state=new src-address-list=\"Winbox_Stage3\" action=add-src-to-address-list address-list=\"Winbox_Blacklist\" address-list-timeout=7d comment=\"SamMikrotik: Winbox Brute Force Blacklist\"")
                sb.appendLine("add chain=input protocol=tcp dst-port=${options.customWinboxPort} connection-state=new src-address-list=\"Winbox_Stage2\" action=add-src-to-address-list address-list=\"Winbox_Stage3\" address-list-timeout=1m")
                sb.appendLine("add chain=input protocol=tcp dst-port=${options.customWinboxPort} connection-state=new src-address-list=\"Winbox_Stage1\" action=add-src-to-address-list address-list=\"Winbox_Stage2\" address-list-timeout=1m")
                sb.appendLine("add chain=input protocol=tcp dst-port=${options.customWinboxPort} connection-state=new action=add-src-to-address-list address-list=\"Winbox_Stage1\" address-list-timeout=1m")
                sb.appendLine("add chain=input src-address-list=\"Winbox_Blacklist\" action=drop comment=\"SamMikrotik: Drop Winbox Attackers\"")
            }
            sb.appendLine()
        }

        if (options.preventDnsPoisoning) {
            sb.appendLine("# 3. تأمين خادم الـ DNS ومنع هجمات التسميم DNS Poisoning")
            sb.appendLine("/ip dns set allow-remote-requests=no")
            sb.appendLine("/ip dns set servers=\"${config.primaryDns},${config.secondaryDns}\"")
            sb.appendLine("/ip firewall filter add chain=input in-interface=!lan protocol=udp dst-port=53 action=drop comment=\"SamMikrotik: Block External DNS Queries\"")
            sb.appendLine("/ip firewall filter add chain=input in-interface=!lan protocol=tcp dst-port=53 action=drop comment=\"SamMikrotik: Block External DNS TCP\"")
            sb.appendLine()
        }

        if (options.enableClientIsolation) {
            sb.appendLine("# 4. عزل المشتركين ومنع التجسس بين كروت الهوتسبوت (Client Isolation)")
            sb.appendLine("# تفعيل Horizon=1 على منافذ بريدج الهوتسبوت لمنع وصول الهواتف لبعضها")
            sb.appendLine(":do {")
            sb.appendLine("  /interface bridge port set [find bridge=\"bridge-hotspot\"] horizon=1")
            sb.appendLine("} on-error={ :log warning \"SamMikrotik: bridge-hotspot not found, check interface name\" }")
            sb.appendLine()
        }

        sb.appendLine("# 5. إيقاف بروتوكول MAC-Winbox على كروت البث العامة")
        sb.appendLine("/tool mac-server set allowed-interface-list=none")
        sb.appendLine("/tool mac-server mac-winbox set allowed-interface-list=none")
        sb.appendLine("/tool mac-server ping set enabled=no")
        sb.appendLine()
        sb.appendLine("# تم تطبيق معايير الأمان بنجاح من تطبيق SamMikrotik.")

        return sb.toString()
    }
}
