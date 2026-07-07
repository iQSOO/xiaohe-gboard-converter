/*
 * SPDX-FileCopyrightText: 2026 iQSOO
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.osfans.trime.ui.main

import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MnemonicActivity : AppCompatActivity() {
    private val horizontalPadding by lazy { dp(20) }
    private val verticalPadding by lazy { dp(14) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "双拼与五笔助记"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(horizontalPadding, verticalPadding, horizontalPadding, dp(32))
        }

        content.addTitle("Rime 助记输入法")
        content.addParagraph(
            "内置三套主要方案：小鹤双拼·鹤形辅码、小鹤双拼·五笔86辅码、五笔86。" +
                "在主界面进入“输入方案”，勾选需要的方案后执行“部署”。全部输入与词库匹配均在本机完成。",
        )

        content.addSection(
            "辅助码用法",
            "先按小鹤双拼正常输入，再输入分号“;”进入辅码筛选。\n" +
                "• 鹤形方案：分号后输入 1～2 位小鹤音形辅码。\n" +
                "• 五笔86方案：分号后输入候选字五笔86编码的前 1～2 位。\n" +
                "• 未输入分号时保持普通双拼候选，不增加操作负担。\n" +
                "• 输入分号后，候选栏会显示对应辅码，便于边用边记。",
        )

        content.addSection(
            "小鹤双拼韵母键位",
            "Q iu　W ei　E e　R uan　T ue/ve\n" +
                "Y un　U sh　I ch　O uo/o　P ie\n" +
                "A a　S ong/iong　D ai　F en　G eng\n" +
                "H ang　J an　K ing/uai　L iang/uang\n" +
                "Z ou　X ia/ua　C ao　V ui/zh\n" +
                "B in　N iao　M ian\n\n" +
                "顺口记：秋娃软月云梳翅，我月；阿松呆分更航安，快两；走夏草追滨鸟眠。\n" +
                "声母提示：V=zh，I=ch，U=sh。",
        )

        content.addSection(
            "五笔86一区：横",
            "G　王旁青头戋五一\n" +
                "F　土士二干十寸雨\n" +
                "D　大犬三羊古石厂\n" +
                "S　木丁西\n" +
                "A　工戈草头右框七",
        )

        content.addSection(
            "五笔86二区：竖",
            "H　目具上止卜虎皮\n" +
                "J　日早两竖与虫依\n" +
                "K　口与川，字根稀\n" +
                "L　田甲方框四车力\n" +
                "M　山由贝，下框几",
        )

        content.addSection(
            "五笔86三区：撇",
            "T　禾竹一撇双人立，反文条头共三一\n" +
                "R　白手看头三二斤\n" +
                "E　月彡乃用家衣底\n" +
                "W　人和八，三四里\n" +
                "Q　金勺缺点无尾鱼，犬旁留叉儿一点夕，氏无七",
        )

        content.addSection(
            "五笔86四区：捺",
            "Y　言文方广在四一，高头一捺谁人去\n" +
                "U　立辛两点六门疒\n" +
                "I　水旁兴头小倒立\n" +
                "O　火业头，四点米\n" +
                "P　之字军盖建道底，摘礻衤",
        )

        content.addSection(
            "五笔86五区：折",
            "N　已半巳满不出己，左框折尸心和羽\n" +
                "B　子耳了也框向上\n" +
                "V　女刀九臼山朝西\n" +
                "C　又巴马，丢矢矣\n" +
                "X　慈母无心弓和匕，幼无力",
        )

        content.addSection(
            "拆字与末笔识别",
            "五笔编码通常取第一、第二、第三及末字根。少于四码且仍有重码时，补末笔识别码。\n" +
                "末笔分区：横 G/F/D，竖 H/J/K，撇 T/R/E，捺 Y/U/I，折 N/B/V；" +
                "左右型取每区第一键，上下型取第二键，杂合型取第三键。",
        )

        content.addSection(
            "开源说明",
            "应用基于 Trime 与 librime，输入方案采用 Rime 配置格式；雾凇拼音、小鹤辅码及五笔86数据分别保留原项目许可和来源说明。" +
                "本页面为学习辅助，不替代各输入方案的原始文档。",
        )

        setContentView(ScrollView(this).apply { addView(content) })
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun LinearLayout.addTitle(text: String) {
        addView(TextView(context).apply {
            this.text = text
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.START
            setPadding(0, dp(6), 0, dp(8))
        })
    }

    private fun LinearLayout.addParagraph(text: String) {
        addView(TextView(context).apply {
            this.text = text
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setLineSpacing(0f, 1.25f)
            setTextIsSelectable(true)
            setPadding(0, 0, 0, dp(16))
        })
    }

    private fun LinearLayout.addSection(title: String, body: String) {
        addView(TextView(context).apply {
            text = title
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(14), 0, dp(7))
        })
        addView(TextView(context).apply {
            text = body
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15.5f)
            setLineSpacing(0f, 1.32f)
            setTextIsSelectable(true)
            setPadding(0, 0, 0, dp(8))
        })
    }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            resources.displayMetrics,
        ).toInt()
}
