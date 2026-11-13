package com.forgebuild.forgehouse50.ui.community

import com.forgebuild.forgehouse50.data.PendingChatOutgoing
import com.forgebuild.forgehouse50.data.SessionStore
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

object PendingChatStore {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val listSer = ListSerializer(PendingChatOutgoing.serializer())

    fun loadAll(session: SessionStore): List<PendingChatOutgoing> {
        val raw = session.pendingChatQueueJson ?: return emptyList()
        return runCatching { json.decodeFromString(listSer, raw) }.getOrDefault(emptyList())
    }

    fun upsert(session: SessionStore, item: PendingChatOutgoing) {
        val updated = loadAll(session).filterNot { it.client_msg_id == item.client_msg_id } + item
        saveAll(session, updated)
    }

    fun remove(session: SessionStore, clientMsgId: String) {
        val updated = loadAll(session).filterNot { it.client_msg_id == clientMsgId }
        saveAll(session, updated)
    }

    private fun saveAll(session: SessionStore, list: List<PendingChatOutgoing>) {
        session.pendingChatQueueJson = if (list.isEmpty()) null else json.encodeToString(listSer, list)
    }
}
