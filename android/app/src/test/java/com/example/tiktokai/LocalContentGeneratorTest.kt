package com.example.tiktokai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalContentGeneratorTest {
    @Test
    fun technologyTopicProducesCompletePackage() {
        val content=LocalContentGenerator.generate("ماذا لو اختفى الإنترنت من العالم لمدة 24 ساعة؟")
        assertTrue(content.title.isNotBlank())
        assertTrue(content.hook.isNotBlank())
        assertEquals(5,content.scenes.size)
        assertEquals(6,content.visualPrompts.size)
        assertTrue(content.hashtags.isNotEmpty())

        val plans=ScenePlanner.build(content.hook,content.scenes,content.visualPrompts)
        assertEquals(6,plans.size)
        assertTrue(plans.all { it.caption.length<=63 })
    }

    @Test
    fun differentTopicFamiliesDoNotReuseSameScript() {
        val technology=LocalContentGenerator.generate("ماذا لو اختفى الإنترنت؟")
        val space=LocalContentGenerator.generate("ماذا لو اختفى القمر؟")
        assertNotEquals(technology.hook,space.hook)
        assertNotEquals(technology.scenes,space.scenes)
    }

    @Test
    fun generatedVisualPromptsAreUniqueEnoughForScenes() {
        val content=LocalContentGenerator.generate("ماذا لو اختفت الكهرباء من العالم لمدة يوم؟")
        assertEquals(content.visualPrompts.size,content.visualPrompts.distinct().size)
    }
}
