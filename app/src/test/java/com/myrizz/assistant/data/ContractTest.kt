package com.myrizz.assistant.data

import com.myrizz.assistant.domain.ReplyStyle
import com.myrizz.assistant.domain.ToolType
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** Guards against the app and the backend drifting apart (style / tool ids, endpoints). */
class ContractTest {
    private fun backendFile(name: String): File? =
        listOf("../backend/src/$name", "backend/src/$name").map(::File).firstOrNull { it.exists() }

    @Test fun everyStyleExistsInBackend() {
        val js = backendFile("prompts.js")?.readText()
        assumeTrue("backend sources not found", js != null)
        ReplyStyle.entries.forEach { assertTrue("style ${it.apiId}", js!!.contains("${it.apiId}:")) }
    }

    @Test fun everyToolExistsInBackend() {
        val js = backendFile("prompts.js")?.readText()
        assumeTrue("backend sources not found", js != null)
        ToolType.entries.filter { it != ToolType.REPLY }.forEach { assertTrue("tool ${it.apiId}", js!!.contains("${it.apiId}:")) }
    }

    @Test fun endpointsExistInBackend() {
        val js = backendFile("app.js")?.readText()
        assumeTrue("backend sources not found", js != null)
        listOf("/v1/replies", "/v1/tools", "/v1/transcribe", "/health").forEach { assertTrue(it, js!!.contains(it)) }
    }

    @Test fun twelveStylesAndElevenTools() {
        assertTrue(ReplyStyle.entries.size == 12)
        assertTrue(ToolType.entries.size == 11)
    }
}
