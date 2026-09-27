package com.example.tiktokai

import android.content.Context
import android.graphics.*
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.util.Random
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

object LocalVisualGenerator {
    private const val W=720
    private const val H=1280

    private enum class Theme { TECH, FINANCE, SPACE, STORY, KNOWLEDGE, TRAVEL, GENERAL }

    fun generate(context:Context,prompts:List<String>):List<Uri> {
        val dir=File(context.getExternalFilesDir("visuals"),"local_pro").apply { mkdirs() }
        dir.listFiles()?.forEach { if(it.isFile) it.delete() }
        return prompts.take(9).mapIndexed { index,prompt ->
            val file=File(dir,"scene_"+(index+1)+".png")
            val bitmap=createVisual(prompt,index)
            FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG,96,it) }
            bitmap.recycle()
            Uri.fromFile(file)
        }
    }

    private fun createVisual(prompt:String,index:Int):Bitmap {
        val bitmap=Bitmap.createBitmap(W,H,Bitmap.Config.ARGB_8888)
        val canvas=Canvas(bitmap)
        val seed=(prompt.hashCode().toLong() shl 13) xor (index*7919L)
        val rnd=Random(seed)
        val theme=classify(prompt)
        val palette=palette(theme,index)

        val background=Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader=LinearGradient(
                0f,0f,W.toFloat(),H.toFloat(),
                intArrayOf(palette[0],palette[1],palette[2]),
                floatArrayOf(0f,0.50f,1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f,0f,W.toFloat(),H.toFloat(),background)

        drawGlow(canvas,W*0.18f,H*0.22f,360f,palette[3],95)
        drawGlow(canvas,W*0.88f,H*0.56f,460f,palette[4],75)
        drawGlow(canvas,W*0.38f,H*0.93f,390f,Color.WHITE,24)
        drawLightBeams(canvas,rnd,palette[3])
        drawFineGrain(canvas,rnd)

        when(theme) {
            Theme.TECH -> drawTech(canvas,rnd,palette,index)
            Theme.FINANCE -> drawFinance(canvas,rnd,palette)
            Theme.SPACE -> drawSpace(canvas,rnd,palette)
            Theme.STORY -> drawStory(canvas,rnd,palette)
            Theme.KNOWLEDGE -> drawKnowledge(canvas,rnd,palette)
            Theme.TRAVEL -> drawTravel(canvas,rnd,palette)
            Theme.GENERAL -> drawFlow(canvas,rnd,palette)
        }

        val vignette=Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader=RadialGradient(
                W/2f,H*0.48f,820f,
                intArrayOf(Color.TRANSPARENT,Color.argb(55,0,0,0),Color.argb(205,0,0,0)),
                floatArrayOf(0.36f,0.72f,1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f,0f,W.toFloat(),H.toFloat(),vignette)

        val frame=Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color=Color.argb(35,255,255,255)
            style=Paint.Style.STROKE
            strokeWidth=2f
        }
        canvas.drawRoundRect(RectF(28f,28f,W-28f,H-28f),34f,34f,frame)
        return bitmap
    }

    private fun classify(prompt:String):Theme {
        val p=prompt.lowercase()
        return when {
            listOf("internet","network","technology","phone","mobile","ai","إنترنت","تقنية","هاتف","ذكاء").any { p.contains(it) } -> Theme.TECH
            listOf("money","finance","bank","economy","business","مال","اقتصاد","بنك","عمل").any { p.contains(it) } -> Theme.FINANCE
            listOf("space","planet","moon","mars","فضاء","كوكب","قمر","مريخ").any { p.contains(it) } -> Theme.SPACE
            listOf("story","mystery","history","قصة","غموض","تاريخ").any { p.contains(it) } -> Theme.STORY
            listOf("learn","science","education","fact","تعلم","علم","معلومة","حقائق").any { p.contains(it) } -> Theme.KNOWLEDGE
            listOf("travel","city","country","trip","سفر","مدينة","دولة","رحلة").any { p.contains(it) } -> Theme.TRAVEL
            else -> Theme.GENERAL
        }
    }

    private fun palette(theme:Theme,index:Int):IntArray {
        val palettes=when(theme) {
            Theme.TECH -> arrayOf(
                intArrayOf(Color.rgb(5,12,28),Color.rgb(7,43,62),Color.rgb(7,13,29),Color.rgb(45,212,191),Color.rgb(59,130,246)),
                intArrayOf(Color.rgb(10,12,32),Color.rgb(28,32,88),Color.rgb(8,12,27),Color.rgb(96,165,250),Color.rgb(168,85,247))
            )
            Theme.FINANCE -> arrayOf(
                intArrayOf(Color.rgb(12,18,16),Color.rgb(21,75,49),Color.rgb(7,15,13),Color.rgb(52,211,153),Color.rgb(250,204,21)),
                intArrayOf(Color.rgb(24,17,8),Color.rgb(91,58,14),Color.rgb(18,12,7),Color.rgb(251,191,36),Color.rgb(245,158,11))
            )
            Theme.SPACE -> arrayOf(
                intArrayOf(Color.rgb(8,7,24),Color.rgb(39,18,78),Color.rgb(4,5,16),Color.rgb(129,140,248),Color.rgb(217,70,239)),
                intArrayOf(Color.rgb(5,12,30),Color.rgb(15,41,91),Color.rgb(3,7,20),Color.rgb(56,189,248),Color.rgb(139,92,246))
            )
            Theme.STORY -> arrayOf(
                intArrayOf(Color.rgb(24,8,20),Color.rgb(80,24,64),Color.rgb(16,6,15),Color.rgb(251,113,133),Color.rgb(192,132,252)),
                intArrayOf(Color.rgb(26,13,8),Color.rgb(88,41,18),Color.rgb(16,8,5),Color.rgb(251,146,60),Color.rgb(244,63,94))
            )
            Theme.KNOWLEDGE -> arrayOf(
                intArrayOf(Color.rgb(6,18,31),Color.rgb(10,70,84),Color.rgb(5,13,25),Color.rgb(34,211,238),Color.rgb(45,212,191)),
                intArrayOf(Color.rgb(14,14,31),Color.rgb(48,36,95),Color.rgb(8,8,22),Color.rgb(167,139,250),Color.rgb(96,165,250))
            )
            Theme.TRAVEL -> arrayOf(
                intArrayOf(Color.rgb(7,24,35),Color.rgb(11,78,88),Color.rgb(6,15,25),Color.rgb(45,212,191),Color.rgb(251,191,36)),
                intArrayOf(Color.rgb(20,16,31),Color.rgb(77,40,90),Color.rgb(10,9,20),Color.rgb(251,113,133),Color.rgb(96,165,250))
            )
            Theme.GENERAL -> arrayOf(
                intArrayOf(Color.rgb(9,12,25),Color.rgb(31,41,83),Color.rgb(7,9,20),Color.rgb(94,234,212),Color.rgb(244,63,94)),
                intArrayOf(Color.rgb(18,10,28),Color.rgb(69,28,90),Color.rgb(9,8,20),Color.rgb(192,132,252),Color.rgb(45,212,191))
            )
        }
        return palettes[index % palettes.size]
    }

    private fun drawGlow(c:Canvas,x:Float,y:Float,radius:Float,color:Int,alpha:Int) {
        val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader=RadialGradient(
                x,y,radius,
                intArrayOf(Color.argb(alpha,Color.red(color),Color.green(color),Color.blue(color)),Color.TRANSPARENT),
                null,Shader.TileMode.CLAMP
            )
        }
        c.drawCircle(x,y,radius,paint)
    }

    private fun drawLightBeams(c:Canvas,rnd:Random,color:Int) {
        val p=Paint(Paint.ANTI_ALIAS_FLAG)
        repeat(4) { i ->
            val path=Path()
            val x=-160f+i*220f+rnd.nextFloat()*80f
            path.moveTo(x,-60f)
            path.lineTo(x+210f,-60f)
            path.lineTo(x+560f,H+80f)
            path.lineTo(x+300f,H+80f)
            path.close()
            p.color=Color.argb(9+rnd.nextInt(10),Color.red(color),Color.green(color),Color.blue(color))
            c.drawPath(path,p)
        }
    }

    private fun drawFineGrain(c:Canvas,rnd:Random) {
        val p=Paint(Paint.ANTI_ALIAS_FLAG)
        repeat(260) {
            val a=5+rnd.nextInt(18)
            val v=180+rnd.nextInt(76)
            p.color=Color.argb(a,v,v,v)
            c.drawCircle(rnd.nextFloat()*W,rnd.nextFloat()*H,0.6f+rnd.nextFloat()*1.8f,p)
        }
    }

    private fun drawTech(c:Canvas,rnd:Random,palette:IntArray,index:Int) {
        val accent=palette[3]
        val accent2=palette[4]
        val white=Color.argb(220,245,250,255)
        val thin=Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color=Color.argb(72,220,245,255)
            style=Paint.Style.STROKE
            strokeWidth=3f
        }
        val strong=Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color=accent
            style=Paint.Style.STROKE
            strokeWidth=7f
            strokeCap=Paint.Cap.ROUND
        }

        when(index % 6) {
            0 -> {
                // Phone + lost signal: opening scene.
                val body=RectF(205f,210f,515f,800f)
                val glass=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.argb(32,255,255,255) }
                val border=Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color=Color.argb(145,255,255,255)
                    style=Paint.Style.STROKE
                    strokeWidth=5f
                }
                c.drawRoundRect(body,46f,46f,glass)
                c.drawRoundRect(body,46f,46f,border)
                c.drawRoundRect(RectF(230f,280f,490f,720f),28f,28f,Paint().apply {
                    shader=LinearGradient(230f,280f,490f,720f,
                        intArrayOf(Color.argb(80,accent),Color.argb(18,accent2)),
                        null,Shader.TileMode.CLAMP)
                })
                c.drawRoundRect(RectF(306f,235f,414f,246f),6f,6f,Paint().apply { color=white })

                // Wi-Fi arcs.
                val wifi=Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color=Color.argb(220,255,255,255)
                    style=Paint.Style.STROKE
                    strokeWidth=8f
                    strokeCap=Paint.Cap.ROUND
                }
                val cx=360f; val cy=485f
                c.drawArc(RectF(cx-115f,cy-80f,cx+115f,cy+150f),215f,110f,false,wifi)
                c.drawArc(RectF(cx-75f,cy-40f,cx+75f,cy+110f),215f,110f,false,wifi)
                c.drawCircle(cx,cy+88f,9f,Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.WHITE })

                // Red/rose disconnect slash.
                val slash=Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color=Color.rgb(251,113,133)
                    strokeWidth=14f
                    strokeCap=Paint.Cap.ROUND
                }
                c.drawLine(270f,375f,455f,590f,slash)
                drawGlassOrb(c,535f,205f,62f,accent2)
            }

            1 -> {
                // Global network outage.
                val cx=360f; val cy=470f; val r=205f
                val globe=Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color=Color.argb(165,225,248,255)
                    style=Paint.Style.STROKE
                    strokeWidth=3f
                }
                c.drawCircle(cx,cy,r,globe)
                for(offset in listOf(-90f,0f,90f)) {
                    c.drawOval(RectF(cx-r,cy-55f+offset/4,cx+r,cy+55f+offset/4),globe)
                }
                c.drawOval(RectF(cx-80f,cy-r,cx+80f,cy+r),globe)
                c.drawOval(RectF(cx-145f,cy-r,cx+145f,cy+r),globe)

                val nodes=arrayOf(
                    PointF(220f,390f),PointF(312f,325f),PointF(470f,365f),
                    PointF(515f,500f),PointF(385f,585f),PointF(240f,555f)
                )
                val connection=Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color=Color.argb(115,94,234,212)
                    strokeWidth=4f
                }
                for(i in 0 until nodes.size-1) {
                    if(i==2) continue // deliberate broken link.
                    c.drawLine(nodes[i].x,nodes[i].y,nodes[i+1].x,nodes[i+1].y,connection)
                }
                nodes.forEachIndexed { i,p ->
                    val pnt=Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color=if(i==2||i==3) Color.rgb(251,113,133) else Color.WHITE
                    }
                    c.drawCircle(p.x,p.y,9f,pnt)
                }
                val breakPaint=Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color=Color.rgb(251,113,133)
                    strokeWidth=7f
                    strokeCap=Paint.Cap.ROUND
                }
                c.drawLine(456f,420f,490f,455f,breakPaint)
                c.drawLine(490f,420f,456f,455f,breakPaint)
                drawGlassOrb(c,585f,220f,55f,accent)
            }

            2 -> {
                // Offline workstations.
                val desk=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.argb(55,255,255,255) }
                c.drawRoundRect(RectF(92f,705f,628f,735f),14f,14f,desk)
                for(i in 0..1) {
                    val left=105f+i*280f
                    val monitor=RectF(left,285f,left+230f,610f)
                    c.drawRoundRect(monitor,28f,28f,Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color=Color.argb(34,255,255,255)
                    })
                    c.drawRoundRect(monitor,28f,28f,Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color=Color.argb(100,255,255,255)
                        style=Paint.Style.STROKE
                        strokeWidth=3f
                    })
                    c.drawLine(left+115f,610f,left+115f,690f,thin)
                    c.drawRoundRect(RectF(left+60f,685f,left+170f,700f),7f,7f,Paint().apply { color=Color.argb(90,255,255,255) })

                    val centerX=left+115f
                    c.drawCircle(centerX,445f,45f,Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color=Color.argb(28,251,113,133)
                    })
                    val xpaint=Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color=Color.rgb(251,113,133);strokeWidth=10f;strokeCap=Paint.Cap.ROUND
                    }
                    c.drawLine(centerX-24f,421f,centerX+24f,469f,xpaint)
                    c.drawLine(centerX+24f,421f,centerX-24f,469f,xpaint)
                }
                drawGlassOrb(c,570f,190f,58f,accent2)
            }

            3 -> {
                // Alternative communication / local mesh.
                val points=arrayOf(
                    PointF(140f,520f),PointF(270f,365f),PointF(445f,380f),
                    PointF(575f,535f),PointF(360f,665f)
                )
                val mesh=Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color=Color.argb(95,94,234,212)
                    strokeWidth=5f
                }
                val links=arrayOf(0 to 1,1 to 2,2 to 3,3 to 4,4 to 0,1 to 4,2 to 4)
                links.forEach { (a,b) -> c.drawLine(points[a].x,points[a].y,points[b].x,points[b].y,mesh) }
                points.forEachIndexed { i,p ->
                    drawGlassOrb(c,p.x,p.y,if(i==4)45f else 32f,if(i%2==0) accent else accent2)
                }

                // Radio waves on both sides.
                val wave=Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color=Color.argb(140,255,255,255)
                    style=Paint.Style.STROKE
                    strokeWidth=4f
                }
                c.drawArc(RectF(50f,315f,250f,625f),280f,120f,false,wave)
                c.drawArc(RectF(470f,315f,670f,625f),140f,120f,false,wave)
            }

            4 -> {
                // Digital payment / service interruption.
                val terminal=RectF(190f,250f,530f,735f)
                c.drawRoundRect(terminal,48f,48f,Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color=Color.argb(35,255,255,255)
                })
                c.drawRoundRect(terminal,48f,48f,Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color=Color.argb(105,255,255,255)
                    style=Paint.Style.STROKE
                    strokeWidth=4f
                })
                c.drawRoundRect(RectF(235f,315f,485f,475f),24f,24f,Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color=Color.argb(40,accent)
                })
                val card=RectF(120f,520f,405f,700f)
                c.save(); c.rotate(-12f,card.centerX(),card.centerY())
                c.drawRoundRect(card,28f,28f,Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.argb(175,15,23,42) })
                c.drawRoundRect(card,28f,28f,Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color=Color.argb(120,255,255,255);style=Paint.Style.STROKE;strokeWidth=3f
                })
                c.drawRoundRect(RectF(150f,560f,230f,600f),9f,9f,Paint().apply { color=Color.rgb(250,204,21) })
                c.restore()
                val alert=Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color=Color.rgb(251,113,133);style=Paint.Style.STROKE;strokeWidth=9f;strokeCap=Paint.Cap.ROUND
                }
                c.drawCircle(400f,395f,48f,alert)
                c.drawLine(400f,366f,400f,406f,alert)
                c.drawCircle(400f,425f,6f,Paint().apply { color=Color.rgb(251,113,133) })
            }

            else -> {
                // Servers / infrastructure closing scene.
                for(row in 0..2) {
                    val top=270f+row*170f
                    val rack=RectF(150f,top,570f,top+125f)
                    c.drawRoundRect(rack,22f,22f,Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.argb(30,255,255,255) })
                    c.drawRoundRect(rack,22f,22f,Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color=Color.argb(72,255,255,255);style=Paint.Style.STROKE;strokeWidth=3f
                    })
                    for(i in 0..4) {
                        val dot=Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color=if(row==1 && i>=3) Color.rgb(251,113,133) else accent
                        }
                        c.drawCircle(205f+i*42f,top+63f,8f,dot)
                    }
                    c.drawRoundRect(RectF(430f,top+47f,530f,top+61f),7f,7f,Paint().apply { color=Color.argb(80,255,255,255) })
                }
                val line=Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color=accent2;strokeWidth=5f;strokeCap=Paint.Cap.ROUND
                }
                c.drawLine(360f,775f,360f,865f,line)
                c.drawLine(300f,865f,420f,865f,line)
            }
        }
    }

    private fun drawFinance(c:Canvas,rnd:Random,palette:IntArray) {
        val grid=Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color=Color.argb(28,255,255,255)
            strokeWidth=1f
        }
        for(i in 0..6) c.drawLine(70f,330f+i*92f,W-70f,330f+i*92f,grid)
        for(i in 0..5) c.drawLine(80f+i*112f,300f,80f+i*112f,900f,grid)

        val path=Path()
        path.moveTo(70f,820f)
        var x=70f
        var y=820f
        repeat(7) {
            x+=85f
            y-=35f+rnd.nextFloat()*80f
            path.lineTo(x,y)
        }
        val line=Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color=palette[3]
            style=Paint.Style.STROKE
            strokeWidth=8f
            strokeCap=Paint.Cap.ROUND
            strokeJoin=Paint.Join.ROUND
        }
        c.drawPath(path,line)
        drawGlassOrb(c,W*0.72f,H*0.30f,76f,palette[4])
    }

    private fun drawSpace(c:Canvas,rnd:Random,palette:IntArray) {
        val star=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.argb(150,255,255,255) }
        repeat(95) {
            val r=0.8f+rnd.nextFloat()*2.2f
            c.drawCircle(rnd.nextFloat()*W,rnd.nextFloat()*H*0.78f,r,star)
        }
        val planet=Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader=RadialGradient(
                W*0.67f,H*0.37f,150f,
                intArrayOf(palette[3],palette[4],Color.rgb(18,19,45)),
                floatArrayOf(0f,0.62f,1f),
                Shader.TileMode.CLAMP
            )
        }
        c.drawCircle(W*0.67f,H*0.37f,150f,planet)
        val orbit=Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color=Color.argb(115,255,255,255)
            style=Paint.Style.STROKE
            strokeWidth=4f
        }
        c.save()
        c.rotate(-18f,W*0.67f,H*0.37f)
        c.drawOval(RectF(W*0.35f,H*0.31f,W*0.99f,H*0.43f),orbit)
        c.restore()
    }

    private fun drawStory(c:Canvas,rnd:Random,palette:IntArray) {
        val spotlight=Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader=LinearGradient(
                W*0.48f,180f,W*0.52f,920f,
                intArrayOf(Color.argb(95,255,255,255),Color.argb(4,255,255,255)),
                null,Shader.TileMode.CLAMP
            )
        }
        val path=Path()
        path.moveTo(W*0.38f,120f)
        path.lineTo(W*0.62f,120f)
        path.lineTo(W*0.86f,940f)
        path.lineTo(W*0.14f,940f)
        path.close()
        c.drawPath(path,spotlight)
        val rail=Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color=Color.argb(80,255,255,255)
            strokeWidth=3f
        }
        c.drawLine(115f,350f,115f,880f,rail)
        repeat(5) {
            val y=360f+it*125f
            c.drawCircle(115f,y,8f,Paint(Paint.ANTI_ALIAS_FLAG).apply { color=if(it==2) palette[3] else Color.WHITE })
        }
    }

    private fun drawKnowledge(c:Canvas,rnd:Random,palette:IntArray) {
        val card=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.argb(25,255,255,255) }
        val stroke=Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color=Color.argb(55,255,255,255)
            style=Paint.Style.STROKE
            strokeWidth=2f
        }
        repeat(3) { i ->
            val left=105f+i*35f
            val top=285f+i*62f
            val rect=RectF(left,top,W-left,top+390f)
            c.drawRoundRect(rect,30f,30f,card)
            c.drawRoundRect(rect,30f,30f,stroke)
        }
        val accent=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=palette[3] }
        c.drawRoundRect(RectF(170f,470f,W-170f,486f),8f,8f,accent)
        c.drawRoundRect(RectF(205f,530f,W-205f,542f),6f,6f,Paint().apply { color=Color.argb(90,255,255,255) })
    }

    private fun drawTravel(c:Canvas,rnd:Random,palette:IntArray) {
        val sun=Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader=RadialGradient(
                W*0.72f,H*0.30f,100f,
                intArrayOf(Color.argb(220,255,233,170),Color.argb(0,255,190,90)),
                null,Shader.TileMode.CLAMP
            )
        }
        c.drawCircle(W*0.72f,H*0.30f,100f,sun)
        val mountain=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.argb(65,255,255,255) }
        val path=Path()
        path.moveTo(0f,830f)
        path.lineTo(135f,650f)
        path.lineTo(260f,790f)
        path.lineTo(405f,570f)
        path.lineTo(575f,790f)
        path.lineTo(W.toFloat(),680f)
        path.lineTo(W.toFloat(),H.toFloat())
        path.lineTo(0f,H.toFloat())
        path.close()
        c.drawPath(path,mountain)
        c.drawLine(60f,900f,W-60f,900f,Paint().apply { color=Color.argb(55,255,255,255);strokeWidth=3f })
    }

    private fun drawFlow(c:Canvas,rnd:Random,palette:IntArray) {
        repeat(4) { i ->
            val path=Path()
            val base=300f+i*155f
            path.moveTo(-80f,base)
            path.cubicTo(140f,base-150f+rnd.nextFloat()*80f,390f,base+140f-rnd.nextFloat()*80f,W+80f,base-20f)
            val p=Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color=Color.argb(48-i*6,Color.red(palette[3]),Color.green(palette[3]),Color.blue(palette[3]))
                style=Paint.Style.STROKE
                strokeWidth=28f-i*4f
                strokeCap=Paint.Cap.ROUND
            }
            c.drawPath(path,p)
        }
        drawGlassOrb(c,W*0.68f,H*0.33f,90f,palette[4])
    }

    private fun drawGlassOrb(c:Canvas,x:Float,y:Float,r:Float,color:Int) {
        val fill=Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader=RadialGradient(
                x-r*0.25f,y-r*0.25f,r,
                intArrayOf(Color.argb(120,255,255,255),Color.argb(80,Color.red(color),Color.green(color),Color.blue(color)),Color.argb(5,0,0,0)),
                null,Shader.TileMode.CLAMP
            )
        }
        c.drawCircle(x,y,r,fill)
        val stroke=Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color=Color.argb(65,255,255,255)
            style=Paint.Style.STROKE
            strokeWidth=2f
        }
        c.drawCircle(x,y,r,stroke)
    }
}
