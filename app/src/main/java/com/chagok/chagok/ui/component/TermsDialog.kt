package com.chagok.chagok.ui.component

import android.content.Context
import android.graphics.Paint
import android.os.Bundle
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.text.style.LineHeightSpan
import android.text.style.TypefaceSpan
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.TextView
import androidx.appcompat.app.AppCompatDialog
import androidx.core.view.isVisible
import com.chagok.chagok.R
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.MarkwonSpansFactory
import io.noties.markwon.core.CoreProps
import io.noties.markwon.core.MarkwonTheme
import org.commonmark.node.Heading
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt
import io.noties.markwon.MarkwonVisitor
import org.commonmark.node.ListItem
import org.commonmark.node.Node
import org.commonmark.node.Paragraph

/* 약관 보기 모달 (S-03 [보기]) */
class TermsDialog(
    context: Context,
    private val title: String,
    private val markdown: String,
    private val effectiveAt: String?,   // ISO-8601 (서버 effectiveAt)
) : AppCompatDialog(context) {

    override fun onCreate(savedInstanceState: Bundle?) {
        supportRequestWindowFeature(Window.FEATURE_NO_TITLE)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.dialog_terms)

        window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        }
        setCancelable(true)

        // 둥근 모서리 밖으로 본문·흐려짐이 삐져나오지 않게
        findViewById<View>(R.id.terms_modal)?.clipToOutline = true

        findViewById<TextView>(R.id.tv_terms_title)?.text = title
        findViewById<TextView>(R.id.tv_terms_date)?.apply {
            val date = formatDate(effectiveAt)
            isVisible = date != null
            text = date?.let { context.getString(R.string.terms_effective, it) }
        }
        findViewById<TextView>(R.id.tv_terms_content)?.let {
            markwon(context).setMarkdown(it, normalizeHeadings(stripHeader(markdown)))
        }

        findViewById<View>(R.id.dim)?.setOnClickListener { dismiss() }
        findViewById<View>(R.id.btn_terms_close)?.setOnClickListener { dismiss() }
    }

    private fun markwon(context: Context): Markwon {
        val density = context.resources.displayMetrics.density
        fun px(dp: Float) = (dp * density).roundToInt()

        val chapterColor = context.getColor(R.color.brand_pressed)
        val articleColor = context.getColor(R.color.gray_600)
        val bold = context.astaSans(700)
        val semibold = context.astaSans(600)

        return Markwon.builder(context)
            .usePlugin(io.noties.markwon.ext.tables.TablePlugin.create(context))
            .usePlugin(object : AbstractMarkwonPlugin() {
                override fun configureTheme(builder: MarkwonTheme.Builder) {
                    builder.headingBreakHeight(0)   // 제목 아래 기본 밑줄 없애기
                }

                override fun configureSpansFactory(builder: MarkwonSpansFactory.Builder) {
                    builder.setFactory(Heading::class.java) { _, props ->
                        if (CoreProps.HEADING_LEVEL.require(props) == 1) {
                            arrayOf(   // 장
                                AbsoluteSizeSpan(px(36f)),
                                ForegroundColorSpan(chapterColor),
                                TypefaceSpan(bold),
                                FixedLineHeightSpan(px(50.4f)),
                            )
                        } else {
                            arrayOf(   // 조
                                AbsoluteSizeSpan(px(32f)),
                                ForegroundColorSpan(articleColor),
                                TypefaceSpan(semibold),
                                FixedLineHeightSpan(px(44.8f)),
                            )
                        }
                    }
                    // 본문 문단·목록 글자: 줄 높이 160% (44.8). 간격용 빈 줄에는 안 붙음
                    builder.setFactory(Paragraph::class.java) { _, _ ->
                        FixedLineHeightSpan(px(44.8f))
                    }
                }

                /** 블록 사이 간격: 조 제목 아래 12, 그 밖 40, 목록 항목끼리는 0 */
                override fun configureVisitor(builder: MarkwonVisitor.Builder) {
                    builder.blockHandler(object : MarkwonVisitor.BlockHandler {
                        override fun blockStart(visitor: MarkwonVisitor, node: Node) {
                            visitor.ensureNewLine()
                        }

                        override fun blockEnd(visitor: MarkwonVisitor, node: Node) {
                            if (!visitor.hasNext(node)) return
                            visitor.ensureNewLine()
                            val gap = gapAfter(node)
                            // 빈 줄 하나를 넣고 그 줄 높이를 간격 값으로 고정
                            val start = visitor.length()
                            visitor.forceNewLine()
                            visitor.setSpans(start, FixedLineHeightSpan(px(gap)))
                        }

                        /** 다음 블록이 제목(장·조)이면 40, 그 밖은 모두 12 */
                        private fun gapAfter(node: Node): Float =
                            if (node.next is Heading) 40f else 12f
                    })
                }
            })
            .build()
    }

    /**
     * 본문 맨 앞의 문서 제목(# …)과 "시행일: …" 줄을 뺀다. 헤더에 이미 보여 주므로.
     * 장(#)이 제목 다음에 나오는 건 그대로 둔다.
     */
    private fun stripHeader(md: String): String {
        val lines = md.lines().toMutableList()
        fun dropBlank() { while (lines.isNotEmpty() && lines.first().isBlank()) lines.removeAt(0) }

        dropBlank()
        if (lines.firstOrNull()?.trimStart()?.startsWith("# ") == true) lines.removeAt(0)
        dropBlank()
        if (lines.firstOrNull()?.trimStart()?.startsWith("시행일") == true) lines.removeAt(0)
        dropBlank()
        return lines.joinToString("\n")
    }

    /**
     * 제목 단계를 글자로 맞춘다. 서버가 장·조를 몇 단계(#, ##, ###)로 쓰든
     * - "제N장 …" → # (장: 초록)
     * - 그 밖의 제목 → ## (조: 회색)
     */
    private fun normalizeHeadings(md: String): String =
        md.lines().joinToString("\n") { line ->
            val m = HEADING.matchEntire(line) ?: return@joinToString line
            val text = m.groupValues[1]
            if (CHAPTER.containsMatchIn(text)) "# $text" else "## $text"
        }

    /** "2026-11-15T00:00:00Z" → "2026년 11월 15일" (한국 시간) */
    private fun formatDate(iso: String?): String? = iso?.let {
        runCatching {
            Instant.parse(it).atZone(SEOUL).format(DateTimeFormatter.ofPattern("yyyy년 M월 d일"))
        }.getOrNull()
    }

    /** 문단 줄 높이 고정 */
    private class FixedLineHeightSpan(private val height: Int) : LineHeightSpan {
        override fun chooseHeight(
            text: CharSequence, start: Int, end: Int, spanstartv: Int, lineHeight: Int, fm: Paint.FontMetricsInt,
        ) {
            val original = fm.descent - fm.ascent
            if (original <= 0) return
            val ratio = height / original.toFloat()
            fm.descent = (fm.descent * ratio).roundToInt()
            fm.ascent = fm.descent - height
        }
    }

    private companion object {
        val SEOUL: ZoneId = ZoneId.of("Asia/Seoul")
        /** 마크다운 제목 줄: "## 제1조 목적" → "제1조 목적" */
        val HEADING = Regex("^\\s*#{1,6}\\s+(.+)$")
        /** "제1장", "제 2 장" 등 */
        val CHAPTER = Regex("^제\\s*\\d+\\s*장")
    }
}