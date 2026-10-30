package com.argon.hub.model

data class ServiceItem(
    val id: String,
    val title: String,
    val description: String,
    val category: String,
    val port: Int,
    val isHttps: Boolean = false,
    val path: String = "/",
    val iconName: String = "dns",
    val externalUrl: String? = null,
    val isWebUi: Boolean = true,
    val adminOnly: Boolean = false
) {
    fun getUrl(activeHost: String): String {
        if (externalUrl != null) return externalUrl
        val scheme = if (isHttps) "https" else "http"
        val cleanPath = if (path.startsWith("/")) path else "/$path"
        return "$scheme://$activeHost:$port$cleanPath"
    }
}

object ServiceRepository {
    val services = listOf(
        // Core Security & DNS
        ServiceItem(
            id = "auth-portal",
            title = "Identity & User Management",
            description = "Manage users, Tailscale profiles & RBAC roles (Admin Portal)",
            category = "Core Security & DNS",
            port = 8089,
            path = "/admin",
            iconName = "shield",
            adminOnly = true
        ),
        ServiceItem(
            id = "adguard",
            title = "AdGuard Home",
            description = "Network-wide ad, tracker & malware sinkhole",
            category = "Core Security & DNS",
            port = 8080,
            iconName = "security",
            adminOnly = true
        ),
        ServiceItem(
            id = "vaultwarden",
            title = "Vaultwarden",
            description = "Encrypted password & API secrets vault",
            category = "Core Security & DNS",
            port = 8443,
            isHttps = true,
            iconName = "lock",
            adminOnly = true
        ),
        ServiceItem(
            id = "honeypot",
            title = "Cowrie Honeypot",
            description = "SSH/Telnet decoy sensor (Ports 2222/2223) • Telegram Bot alerts",
            category = "Core Security & DNS",
            port = 2222,
            iconName = "terminal",
            isWebUi = false,
            adminOnly = true
        ),
        ServiceItem(
            id = "crowdsec",
            title = "CrowdSec Defense",
            description = "Collaborative firewall & threat bouncer (LAPI: 8081)",
            category = "Core Security & DNS",
            port = 8081,
            iconName = "shield",
            externalUrl = "https://app.crowdsec.net",
            adminOnly = true
        ),

        // AI & Dev Workspace (Standard & User-Accessible)
        ServiceItem(
            id = "qdrant",
            title = "Qdrant Vector DB",
            description = "Vector search & embeddings engine for AI/RAG",
            category = "AI & Dev Workspace",
            port = 6333,
            path = "/dashboard",
            iconName = "database",
            adminOnly = true
        ),
        ServiceItem(
            id = "browser",
            title = "Disposable Browser",
            description = "Selkies WebRTC cleanroom sandbox browser",
            category = "AI & Dev Workspace",
            port = 3003,
            isHttps = true,
            iconName = "web",
            adminOnly = false
        ),
        ServiceItem(
            id = "stirling",
            title = "Stirling-PDF",
            description = "100% private in-browser document editor & OCR",
            category = "AI & Dev Workspace",
            port = 8086,
            iconName = "file",
            adminOnly = false
        ),
        ServiceItem(
            id = "it-tools",
            title = "IT-Tools",
            description = "Swiss Army Knife of 80+ developer utilities & converters",
            category = "AI & Dev Workspace",
            port = 8087,
            iconName = "build",
            adminOnly = false
        ),
        ServiceItem(
            id = "pairdrop",
            title = "PairDrop",
            description = "Local peer-to-peer file transfer & AirDrop alternative",
            category = "AI & Dev Workspace",
            port = 8088,
            iconName = "share",
            adminOnly = false
        ),

        // System & Management
        ServiceItem(
            id = "beszel",
            title = "Beszel Telemetry",
            description = "Live CPU temperature & system telemetry HUD",
            category = "System & Management",
            port = 8090,
            iconName = "thermostat",
            adminOnly = true
        ),
        ServiceItem(
            id = "watchtower",
            title = "Watchtower",
            description = "Automated container updates at 4:00 AM • Telegram alerts",
            category = "System & Management",
            port = 0,
            iconName = "update",
            isWebUi = false,
            adminOnly = true
        ),
        ServiceItem(
            id = "kuma",
            title = "Uptime Kuma",
            description = "Real-time service health & ping monitor",
            category = "System & Management",
            port = 3001,
            iconName = "speed",
            adminOnly = true
        ),
        ServiceItem(
            id = "syncthing",
            title = "Syncthing",
            description = "Encrypted P2P file & Obsidian notes sync",
            category = "System & Management",
            port = 8384,
            iconName = "sync",
            adminOnly = false
        ),
        ServiceItem(
            id = "registry",
            title = "Docker Registry UI",
            description = "Private container image catalog & manager",
            category = "System & Management",
            port = 5001,
            iconName = "layers",
            adminOnly = true
        ),
        ServiceItem(
            id = "bot",
            title = "Pi Sentinel Bot",
            description = "Telegram AI news, device alerts & system ops",
            category = "System & Management",
            port = 0,
            externalUrl = "https://t.me/ArgonPiSentinelBot",
            iconName = "send",
            adminOnly = false
        )
    )
}
