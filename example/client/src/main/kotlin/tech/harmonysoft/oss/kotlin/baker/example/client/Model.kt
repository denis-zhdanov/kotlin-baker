package tech.harmonysoft.oss.kotlin.baker.example.client

data class Servers(val servers: Collection<Server>)

data class Server(val address: String, val settings: Map<String, String>)