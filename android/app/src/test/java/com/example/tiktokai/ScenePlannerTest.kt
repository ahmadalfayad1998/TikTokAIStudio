package com.example.tiktokai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScenePlannerTest {
    @Test
    fun createsHookAndScenesWithShortCaptions() {
        val plans=ScenePlanner.build(
            hook="لو اختفى الإنترنت فجأة، أول ساعة وحدها ستغيّر تفاصيل يومنا أكثر مما نتوقع.",
            scenes=listOf(
                "تبدأ القصة لحظة حدوث السيناريو: ماذا سيحدث لو اختفى الإنترنت من العالم بالكامل؟",
                "التواصل والخدمات المتصلة ستكون أول ما يشعر به الناس مباشرة.",
                "بعدها يظهر الأثر على العمل والدفع والخدمات التي تعتمد على الاتصال المستمر.",
                "الناس والمؤسسات سيبحثون سريعًا عن بدائل محلية وطرق تواصل أبسط.",
                "المفاجأة ليست في الانقطاع نفسه، بل في الأشياء التي اكتشفنا أننا نعتمد عليها يوميًا."
            )
        )

        assertEquals(6,plans.size)
        assertEquals(SceneRole.HOOK,plans.first().role)
        assertEquals(SceneRole.CTA,plans.last().role)
        assertTrue(plans.all { it.caption.length<=63 })
        assertTrue(plans.all { it.narration.isNotBlank() })
        assertTrue(plans.all { it.visualPrompt.isNotBlank() })
    }

    @Test
    fun stripsBoilerplateFromOnScreenCaption() {
        val caption=ScenePlanner.captionFor(
            "تبدأ القصة لحظة حدوث السيناريو: ماذا سيحدث لو انقطعت الكهرباء 24 ساعة؟",
            SceneRole.CONTEXT
        )
        assertTrue(caption.startsWith("ماذا سيحدث"))
    }

    @Test
    fun keepsNarrationLongerThanCaption() {
        val longText="بعد ساعات ستبدأ التأثيرات بالظهور على العمل والتعليم والدفع والخدمات اليومية التي تعتمد على الاتصال المستمر في كل لحظة."
        val plan=ScenePlanner.build("",listOf(longText)).first()
        assertTrue(plan.narration.length>plan.caption.length)
        assertTrue(plan.caption.endsWith("…") || plan.caption.length<=62)
    }
}
