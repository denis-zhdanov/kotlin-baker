package tech.harmonysoft.oss.kotlin.baker.example.client

data class Tasks(val tasks: Map<String, TaskConfig>)

data class TaskConfig(val id: String, val properties: Map<String, Any>)