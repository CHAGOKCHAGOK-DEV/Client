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
        findViewById<TextView>(R.id.tv_terms_content)?.let { markwon(context).setMarkdown(it, markdown) }

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
                }
            })
            .build()
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
    }
}