package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "chat_messages")
data class ChatMessage(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val sessionId: String,
    val role: String, // "user", "assistant", "system"
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val status: String = "sent", // "sending", "sent", "error"
    val modelUsed: String? = null,
    val latencyMs: Long? = null,
    val isCommand: Boolean = false,
    val errorMessage: String? = null,
    // Proposed task support matching AlwaysOnAgent
    val proposalToken: String? = null,
    val proposalInstruction: String? = null,
    val proposalProject: String? = null,
    val proposalState: String? = null, // "pending", "run", "dismissed"
    val linkedTaskId: String? = null,
    val proposalsJson: String? = null
) {
    fun getProposals(): List<TaskProposal> {
        if (!proposalsJson.isNullOrBlank()) {
            try {
                val array = org.json.JSONArray(proposalsJson)
                val list = mutableListOf<TaskProposal>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    list.add(
                        TaskProposal(
                            id = obj.optString("id"),
                            token = obj.optString("token", obj.optString("id")),
                            instruction = obj.optString("instruction"),
                            reason = obj.optString("reason", ""),
                            project = if (obj.has("project") && !obj.isNull("project")) obj.optString("project") else null,
                            isLocal = obj.optBoolean("isLocal", false),
                            state = obj.optString("state", "pending")
                        )
                    )
                }
                if (list.isNotEmpty()) return list
            } catch (_: Exception) {}
        }
        if (!proposalInstruction.isNullOrBlank()) {
            return listOf(
                TaskProposal(
                    id = proposalToken ?: "",
                    token = proposalToken ?: "",
                    instruction = proposalInstruction,
                    project = proposalProject,
                    isLocal = proposalToken.isNullOrBlank() || proposalToken.startsWith("local_"),
                    state = proposalState ?: "pending"
                )
            )
        }
        return emptyList()
    }

    fun withUpdatedProposal(targetId: String, newState: String): ChatMessage {
        val current = getProposals()
        if (current.isEmpty()) return copy(proposalState = newState)
        val updated = current.map { prop ->
            if (prop.id == targetId || prop.token == targetId || (prop.isLocal && targetId.startsWith("local_"))) {
                prop.copy(state = newState)
            } else {
                prop
            }
        }
        val first = updated.firstOrNull()
        return copy(
            proposalsJson = serializeProposals(updated),
            proposalState = if (first?.id == targetId || first?.token == targetId) newState else proposalState
        )
    }

    companion object {
        fun serializeProposals(proposals: List<TaskProposal>): String {
            val array = org.json.JSONArray()
            for (p in proposals) {
                val obj = org.json.JSONObject()
                obj.put("id", p.id)
                obj.put("token", p.token)
                obj.put("instruction", p.instruction)
                obj.put("reason", p.reason)
                if (p.project != null) obj.put("project", p.project)
                obj.put("isLocal", p.isLocal)
                obj.put("state", p.state)
                array.put(obj)
            }
            return array.toString()
        }
    }
}
