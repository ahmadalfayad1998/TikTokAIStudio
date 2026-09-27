package com.example.tiktokai

enum class SceneRole {
    HOOK,
    CONTEXT,
    IMPACT,
    RESPONSE,
    CONSEQUENCE,
    CTA
}

data class ScenePlan(
    val narration:String,
    val caption:String,
    val visualPrompt:String,
    val role:SceneRole
)

object ScenePlanner {
    fun build(
        hook:String,
        scenes:List<String>,
        visualPrompts:List<String> = emptyList()
    ):List<ScenePlan> {
        val narration=(listOf(hook)+scenes)
            .map { normalize(it) }
            .filter { it.isNotBlank() }
            .take(8)

        if(narration.isEmpty()) return emptyList()

        return narration.mapIndexed { index,text ->
            val role=roleFor(index,narration.size)
            val prompt=visualPrompts.getOrNull(index)
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: defaultPrompt(text,role,index)

            ScenePlan(
                narration=text,
                caption=captionFor(text,role),
                visualPrompt=prompt,
                role=role
            )
        }
    }

    fun captionFor(text:String,role:SceneRole):String {
        var clean=normalize(text)
        clean=stripBoilerplate(clean)

        val firstClause=clean
            .split(Regex("[.!؟?؛;]"))
            .firstOrNull { it.isNotBlank() }
            ?.trim()
            .orEmpty()

        val candidate=when {
            firstClause.length in 18..78 -> firstClause
            clean.length<=78 -> clean
            else -> shorten(clean,78)
        }

        val minUseful=when(role) {
            SceneRole.HOOK -> 8
            SceneRole.CTA -> 6
            else -> 10
        }
        return if(candidate.length>=minUseful) candidate else shorten(clean,78)
    }

    private fun roleFor(index:Int,total:Int):SceneRole {
        if(index==0) return SceneRole.HOOK
        if(index==total-1) return SceneRole.CTA
        return when(index) {
            1 -> SceneRole.CONTEXT
            2 -> SceneRole.IMPACT
            3 -> SceneRole.RESPONSE
            else -> SceneRole.CONSEQUENCE
        }
    }

    private fun defaultPrompt(text:String,role:SceneRole,index:Int):String {
        val roleHint=when(role) {
            SceneRole.HOOK -> "strong opening visual, immediate tension"
            SceneRole.CONTEXT -> "establishing scene, clear context"
            SceneRole.IMPACT -> "visible real-world impact"
            SceneRole.RESPONSE -> "people or systems reacting"
            SceneRole.CONSEQUENCE -> "secondary consequences, wider perspective"
            SceneRole.CTA -> "clean closing visual, reflective mood"
        }
        return "Vertical 9:16 cinematic editorial scene, no text, no watermark, "+
            roleHint+", scene "+(index+1)+", concept: "+text
    }

    private fun stripBoilerplate(value:String):String {
        val prefixes=listOf(
            "تبدأ القصة لحظة حدوث السيناريو:",
            "نبدأ من السؤال نفسه:",
            "نبدأ بالسؤال:",
            "السؤال الرئيسي هو:",
            "تخيل المشهد:",
            "تخيل أن الفكرة أصبحت حقيقة:",
            "اللقطة الأولى:",
            "نبدأ من اللحظة الأولى:",
            "ابدأ من الفكرة الأساسية:",
            "نضع الفكرة في سياقها أولًا:",
            "تخيل المشهد من البداية:"
        )
        var out=value
        prefixes.firstOrNull { out.startsWith(it,ignoreCase=true) }?.let {
            out=out.removePrefix(it).trim()
        }
        return out
    }

    private fun shorten(value:String,max:Int):String {
        if(value.length<=max) return value
        val cut=value.take(max+1)
        val boundary=listOf(
            cut.lastIndexOf(' '),
            cut.lastIndexOf('،'),
            cut.lastIndexOf(',')
        ).maxOrNull() ?: -1
        val safe=if(boundary>=max/2) cut.substring(0,boundary) else value.take(max)
        return safe.trim().trimEnd('،',',','؛',';','.')+"…"
    }

    private fun normalize(value:String):String =
        value.replace(Regex("\\s+")," ").trim()
}
