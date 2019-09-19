package tech.harmonysoft.oss.kotlin.baker.example.client

data class Tasks(val tasks: Set<TaskConfig>)

data class TaskConfig(val id: String, val properties: Map<String, Any>)

data class Servers(val servers: List<Server>)

data class Server(val address: String, val settings: Map<String, String>)