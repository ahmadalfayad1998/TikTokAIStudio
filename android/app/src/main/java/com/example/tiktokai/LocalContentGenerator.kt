package com.example.tiktokai

object LocalContentGenerator {
    private enum class Kind { TECH, FINANCE, SPACE, HISTORY, MOTIVATION, GENERAL }

    fun generate(topic:String):BackendApiV16.GeneratedContent {
        val clean=topic.trim().replace(Regex("\\s+")," ")
        val kind=classify(clean)
        val packageData=when(kind) {
            Kind.TECH -> tech(clean)
            Kind.FINANCE -> finance(clean)
            Kind.SPACE -> space(clean)
            Kind.HISTORY -> history(clean)
            Kind.MOTIVATION -> motivation(clean)
            Kind.GENERAL -> general(clean)
        }

        val words=clean.split(Regex("[^\\p{L}\\p{N}]+"))
            .filter { it.length>=3 }
            .distinct()
            .take(3)
        val tags=buildList {
            add("#معلومات")
            when(kind) {
                Kind.TECH -> add("#تقنية")
                Kind.FINANCE -> add("#اقتصاد")
                Kind.SPACE -> add("#فضاء")
                Kind.HISTORY -> add("#تاريخ")
                Kind.MOTIVATION -> add("#تطوير_الذات")
                Kind.GENERAL -> add("#حقائق")
            }
            words.forEach { add("#"+it.replace(" ","_")) }
            add("#TikTok")
        }.distinct().take(7)

        val prompts=(listOf(packageData.hook)+packageData.scenes).mapIndexed { index,text ->
            visualPrompt(kind,clean,text,index)
        }

        return BackendApiV16.GeneratedContent(
            title=packageData.title,
            hook=packageData.hook,
            description=packageData.description,
            hashtags=tags,
            scenes=packageData.scenes,
            visualPrompts=prompts
        )
    }

    private data class Package(
        val title:String,
        val hook:String,
        val description:String,
        val scenes:List<String>
    )

    private fun classify(topic:String):Kind {
        val p=topic.lowercase()
        return when {
            listOf("انترنت","إنترنت","شبكة","هاتف","ذكاء","تقنية","تكنولوجيا","internet","network","phone","ai","technology").any { p.contains(it) } -> Kind.TECH
            listOf("مال","اقتصاد","بنك","راتب","سوق","عملات","money","economy","bank","market","business").any { p.contains(it) } -> Kind.FINANCE
            listOf("فضاء","قمر","مريخ","كوكب","شمس","space","moon","mars","planet").any { p.contains(it) } -> Kind.SPACE
            listOf("تاريخ","قديم","حضارة","امبراطورية","إمبراطورية","history","ancient","empire").any { p.contains(it) } -> Kind.HISTORY
            listOf("نجاح","تحفيز","عادة","هدف","دراسة","success","motivation","habit","goal").any { p.contains(it) } -> Kind.MOTIVATION
            else -> Kind.GENERAL
        }
    }

    private fun tech(t:String)=Package(
        title=t,
        hook="لو حدث هذا فجأة، أول ساعة وحدها ستغيّر تفاصيل يومنا أكثر مما نتوقع.",
        description="سيناريو تقني قصير يشرح التأثيرات الأولى ثم البدائل التي قد تظهر.",
        scenes=listOf(
            "تبدأ القصة لحظة حدوث السيناريو: $t",
            "التواصل والخدمات المتصلة ستكون أول ما يشعر به الناس مباشرة.",
            "بعدها يظهر الأثر على العمل والدفع والخدمات التي تعتمد على الاتصال المستمر.",
            "الناس والمؤسسات سيبحثون سريعًا عن بدائل محلية وطرق تواصل أبسط.",
            "المفاجأة ليست في الانقطاع نفسه، بل في الأشياء التي اكتشفنا أننا نعتمد عليها يوميًا."
        )
    )

    private fun finance(t:String)=Package(
        title=t,
        hook="تغيير مالي صغير قد يبدو عاديًا، لكنه يستطيع تحريك سلسلة كاملة من القرارات.",
        description="شرح مبسط لسيناريو اقتصادي وتأثيره المحتمل على الحياة اليومية.",
        scenes=listOf(
            "نبدأ بالسؤال الاقتصادي: $t",
            "أول رد فعل يظهر عادة في قرارات الشراء والادخار والتأجيل.",
            "ثم ينتقل التأثير إلى الشركات والأسعار والسيولة داخل السوق.",
            "مع الوقت يبدأ الناس بتغيير أولوياتهم والبحث عن بدائل أقل تكلفة.",
            "الأهم هو أن الأثر لا يصل للجميع بنفس السرعة ولا بنفس الحجم."
        )
    )

    private fun space(t:String)=Package(
        title=t,
        hook="في الفضاء، تغيير واحد فقط قد يقلب المشهد بالكامل.",
        description="سيناريو فضائي مبسط يحول الفكرة إلى سلسلة مشاهد قصيرة.",
        scenes=listOf(
            "تخيل المشهد من البداية: $t",
            "أول تغير سنلاحظه سيكون مرتبطًا بالحركة والضوء والظروف المحيطة.",
            "بعد ذلك تبدأ النتائج الأكبر بالظهور على الأجرام القريبة والبيئة المحيطة.",
            "بعض التأثيرات قد تكون سريعة، بينما يحتاج بعضها وقتًا طويلًا ليظهر.",
            "وهنا يصبح السؤال الحقيقي: أي نتيجة ستكون الأكثر وضوحًا بالنسبة لنا؟"
        )
    )

    private fun history(t:String)=Package(
        title=t,
        hook="تفصيلة واحدة في التاريخ كانت كافية أحيانًا لتغيير ما جاء بعدها لسنوات.",
        description="قالب سرد تاريخي قصير يركز على السبب والتحول والنتيجة.",
        scenes=listOf(
            "نضع الفكرة في سياقها أولًا: $t",
            "في البداية تكون هناك أسباب متراكمة لا يلاحظها الجميع.",
            "ثم تأتي لحظة التحول التي تجعل الأحداث تتسارع بشكل واضح.",
            "بعدها تتغير التحالفات والقرارات وحياة الناس العاديين.",
            "ولهذا تبقى هذه اللحظة مهمة: أثرها لا ينتهي بانتهاء الحدث نفسه."
        )
    )

    private fun motivation(t:String)=Package(
        title=t,
        hook="النتيجة الكبيرة غالبًا لا تبدأ بخطوة كبيرة، بل بتصرف صغير يتكرر.",
        description="فيديو تحفيزي قصير يحول الفكرة إلى خطوات بسيطة قابلة للتطبيق.",
        scenes=listOf(
            "ابدأ من الفكرة الأساسية: $t",
            "حوّلها إلى خطوة صغيرة جدًا تستطيع تنفيذها بدون مقاومة كبيرة.",
            "اربط الخطوة بوقت أو موقف ثابت بدل الاعتماد على الحماس فقط.",
            "راقب التقدم أسبوعيًا وعدّل ما لا يناسبك بدل ترك الخطة بالكامل.",
            "الهدف ليس الكمال؛ الهدف أن يصبح التقدم أسهل من التوقف."
        )
    )

    private fun general(t:String):Package {
        val variant=Math.floorMod(t.hashCode(),3)
        return when(variant) {
            0 -> Package(
                t,
                "السؤال يبدو بسيطًا، لكن عندما نتابع نتائجه خطوة بخطوة يصبح أكثر إثارة.",
                "سيناريو قصير يشرح الفكرة من البداية حتى النتيجة.",
                listOf(
                    "نبدأ من السؤال نفسه: $t",
                    "أول نتيجة ستكون مباشرة وواضحة للناس في حياتهم اليومية.",
                    "بعدها تبدأ التأثيرات غير المباشرة بالظهور واحدة تلو الأخرى.",
                    "مع الوقت تظهر حلول جديدة، لكنها قد تصنع تحديات مختلفة.",
                    "وفي النهاية يبقى السؤال: أي نتيجة تتوقع أن تظهر أولًا؟"
                )
            )
            1 -> Package(
                t,
                "هناك نتيجة نتوقعها جميعًا… لكن النتيجة التالية هي الأكثر إثارة.",
                "شرح بصري سريع لفكرة افتراضية وتأثيراتها المتسلسلة.",
                listOf(
                    "تخيل أن الفكرة أصبحت حقيقة: $t",
                    "في الساعات الأولى يحاول الجميع فهم ما تغير فعلًا.",
                    "بعد ذلك تتغير العادات والقرارات مع ظهور معلومات جديدة.",
                    "بعض الناس سيتكيف بسرعة بينما يحتاج آخرون وقتًا أطول.",
                    "الجزء المثير هو أن النتيجة النهائية قد تختلف تمامًا عن التوقع الأول."
                )
            )
            else -> Package(
                t,
                "ماذا لو حدث هذا غدًا؟ لنفكك السيناريو في خمس لقطات سريعة.",
                "فيديو قصير منظم حول سؤال افتراضي أو فكرة عامة.",
                listOf(
                    "اللقطة الأولى: $t",
                    "نحدد أول شيء سيتغير مباشرة بعد حدوثه.",
                    "ثم ننظر إلى الأثر على الحياة اليومية والقرارات المعتادة.",
                    "بعدها نبحث عن البدائل التي قد تظهر مع استمرار الوضع.",
                    "وأخيرًا نقارن بين ما توقعناه في البداية وما قد يحدث فعلًا."
                )
            )
        }
    }

    private fun visualPrompt(kind:Kind,topic:String,text:String,index:Int):String {
        val style=when(kind) {
            Kind.TECH -> "futuristic technology, elegant network light, premium dark blue and teal"
            Kind.FINANCE -> "premium finance editorial, elegant charts and city lights, deep green and gold"
            Kind.SPACE -> "cinematic deep space, planet light, violet and blue"
            Kind.HISTORY -> "cinematic historical editorial, warm dramatic light, rich amber and burgundy"
            Kind.MOTIVATION -> "clean inspirational editorial, modern architecture and soft sunrise"
            Kind.GENERAL -> "cinematic editorial abstract scene, premium dark gradient and soft light"
        }
        return "Vertical 9:16, no text, no watermark, "+style+", scene "+(index+1)+", topic: "+topic+", concept: "+text
    }
}
