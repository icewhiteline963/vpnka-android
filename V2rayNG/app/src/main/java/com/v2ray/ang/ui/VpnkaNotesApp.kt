package com.v2ray.ang.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.v2ray.ang.handler.SmartDeskStore

/**
 * «Заметки» — a SmartDesk note-taking app. Two note kinds:
 *  • "text"  — rich text: the toolbar toggles bold / italic / underline /
 *    strikethrough over the current selection (styles stored as offset spans).
 *  • "list"  — a checklist / shopping list of checkable rows.
 * Storage + sync go through SmartDeskStore.notes(), exactly like the other apps.
 */
@Composable
fun VpnkaNotesApp(syncTick: Int, onChanged: () -> Unit) {
    var notes by remember(syncTick) { mutableStateOf(SmartDeskStore.notes().sortedByDescending { it.updatedAt }) }
    var editing by remember { mutableStateOf<SmartDeskStore.Note?>(null) }
    var creating by remember { mutableStateOf(false) }

    fun reload() { notes = SmartDeskStore.notes().sortedByDescending { it.updatedAt }; onChanged() }

    // «Назад» из редактора возвращает к списку, а не выбрасывает из заметок.
    SmartDeskBackHandler {
        when {
            editing != null -> { editing = null; true }
            creating -> { creating = false; true }
            else -> false
        }
    }

    editing?.let { note ->
        NoteEditor(
            note = note,
            onBack = { editing = null },
            onSave = { updated -> SmartDeskStore.saveNote(updated); reload(); editing = null },
            onDelete = { SmartDeskStore.deleteNote(note.id); reload(); editing = null },
        )
        return
    }

    if (creating) {
        NewNoteSheet(
            onPick = { kind ->
                creating = false
                editing = SmartDeskStore.Note(id = SmartDeskStore.newId(), kind = kind)
            },
            onDismiss = { creating = false },
        )
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)
                .clip(RoundedCornerShape(11.dp)).background(VpnkaColors.Accent)
                .clickable { creating = true }.padding(14.dp),
        ) { Text("＋ Новая заметка", fontFamily = VpnkaFonts.nunito800, fontSize = 14.sp, color = Color.White) }

        if (notes.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("📝", fontSize = 44.sp)
                Spacer(Modifier.height(8.dp))
                Text("Пока пусто", fontFamily = VpnkaFonts.nunito800, fontSize = 16.sp, color = VpnkaColors.TextStrong)
                Text("Заметки, списки покупок и всё под рукой.", fontFamily = VpnkaFonts.manrope600,
                    fontSize = 13.sp, color = VpnkaColors.TextMuted)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(notes, key = { it.id }) { n ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(12.dp)).background(VpnkaColors.CardServer).border(1.dp, VpnkaColors.Hairline, RoundedCornerShape(12.dp))
                            .clickable { editing = n }.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(if (n.kind == "list") "☑️" else "📄", fontSize = 20.sp)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                n.title.ifBlank { notePreview(n).ifBlank { "Без названия" } },
                                fontFamily = VpnkaFonts.nunito800, fontSize = 15.sp, color = VpnkaColors.TextStrong,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                            val sub = if (n.kind == "list") "${n.items.count { it.done }}/${n.items.size} куплено" else notePreview(n)
                            if (sub.isNotBlank()) {
                                Text(sub, fontFamily = VpnkaFonts.manrope600, fontSize = 12.sp,
                                    color = VpnkaColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun notePreview(n: SmartDeskStore.Note): String =
    if (n.kind == "list") n.items.joinToString(", ") { it.text }.take(60)
    else n.body.replace("\n", " ").take(60)

@Composable
private fun NewNoteSheet(onPick: (String) -> Unit, onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Отмена") } },
        title = { Text("Что создать?", fontFamily = VpnkaFonts.nunito800, color = VpnkaColors.TextStrong) },
        text = {
            Column {
                NoteTypeRow("📄  Текстовая заметка", "Форматируемый текст") { onPick("text") }
                Spacer(Modifier.height(8.dp))
                NoteTypeRow("☑️  Список / покупки", "Пункты с галочками") { onPick("list") }
            }
        },
        containerColor = VpnkaColors.BgOffCentre,
    )
}

@Composable
private fun NoteTypeRow(title: String, sub: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(VpnkaColors.CardServer)
            .border(1.dp, VpnkaColors.Hairline, RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(title, fontFamily = VpnkaFonts.nunito800, fontSize = 15.sp, color = VpnkaColors.TextStrong)
            Text(sub, fontFamily = VpnkaFonts.manrope600, fontSize = 12.sp, color = VpnkaColors.TextMuted)
        }
    }
}

// --- Editor ---------------------------------------------------------------

@Composable
private fun NoteEditor(
    note: SmartDeskStore.Note,
    onBack: () -> Unit,
    onSave: (SmartDeskStore.Note) -> Unit,
    onDelete: () -> Unit,
) {
    var title by remember(note.id) { mutableStateOf(note.title) }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("‹", fontSize = 24.sp, color = VpnkaColors.TextStrong,
                modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onBack).padding(horizontal = 8.dp, vertical = 4.dp))
            Spacer(Modifier.width(6.dp))
            BasicTextField(
                value = title,
                onValueChange = { title = it },
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(
                    color = VpnkaColors.TextStrong, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                ),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(VpnkaColors.Accent),
                decorationBox = { inner ->
                    if (title.isEmpty()) Text("Заголовок", color = VpnkaColors.TextMuted, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    inner()
                },
                modifier = Modifier.weight(1f),
            )
            Text("🗑", fontSize = 18.sp, color = VpnkaColors.TextMuted,
                modifier = Modifier.clip(CircleShape).clickable(onClick = onDelete).padding(6.dp))
        }

        if (note.kind == "list") {
            ChecklistBody(note, title, onSave)
        } else {
            TextNoteBody(note, title, onSave)
        }
    }
}

@Composable
private fun TextNoteBody(note: SmartDeskStore.Note, title: String, onSave: (SmartDeskStore.Note) -> Unit) {
    var tfv by remember(note.id) { mutableStateOf(TextFieldValue(note.body, TextRange(note.body.length))) }
    var spans by remember(note.id) { mutableStateOf(note.spans) }

    // Сохраняем при УХОДЕ, а не только по кнопке «Готово».
    //
    // Раньше «назад» — и системная, и «‹» в шапке — молча стирала
    // написанное. Человек, который просто вышел из заметки, терял её: он не
    // сделал ничего необычного, а текста больше нет.
    val latest by rememberUpdatedState(Triple(title, tfv.text, spans))
    DisposableEffect(note.id) {
        onDispose {
            val (t, body, sp) = latest
            if (t != note.title || body != note.body || sp != note.spans) {
                onSave(
                    note.copy(
                        title = t, kind = "text", body = body, spans = sp,
                        updatedAt = System.currentTimeMillis(),
                    )
                )
            }
        }
    }

    fun toggle(style: String) {
        val sel = tfv.selection
        if (sel.collapsed) return
        val a = minOf(sel.start, sel.end)
        val b = maxOf(sel.start, sel.end)
        spans = toggleSpan(spans, a, b, style)
    }

    val transform = remember(spans) { SpanTransformation(spans) }

    // Formatting toolbar.
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StyleBtn("B", bold = true) { toggle("bold") }
        StyleBtn("I", italic = true) { toggle("italic") }
        StyleBtn("U", underline = true) { toggle("underline") }
        StyleBtn("S", strike = true) { toggle("strike") }
        Spacer(Modifier.weight(1f))
        Box(
            modifier = Modifier.clip(RoundedCornerShape(11.dp)).background(VpnkaColors.Accent)
                .clickable {
                    onSave(note.copy(title = title, kind = "text", body = tfv.text, spans = spans,
                        updatedAt = System.currentTimeMillis()))
                }
                .padding(horizontal = 16.dp, vertical = 9.dp),
        ) { Text("Готово", fontFamily = VpnkaFonts.nunito800, fontSize = 13.sp, color = Color.White) }
    }
    Text("Выделите текст и нажмите B / I / U / S", fontFamily = VpnkaFonts.manrope600,
        fontSize = 11.sp, color = VpnkaColors.TextMuted, modifier = Modifier.padding(bottom = 6.dp))

    BasicTextField(
        value = tfv,
        onValueChange = { new ->
            if (new.text != tfv.text) spans = remapSpans(spans, tfv.text, new.text)
            tfv = new
        },
        textStyle = androidx.compose.ui.text.TextStyle(color = VpnkaColors.TextStrong, fontSize = 16.sp),
        cursorBrush = androidx.compose.ui.graphics.SolidColor(VpnkaColors.Accent),
        visualTransformation = transform,
        decorationBox = { inner ->
            if (tfv.text.isEmpty()) Text("Текст заметки…", color = VpnkaColors.TextMuted, fontSize = 16.sp)
            inner()
        },
        modifier = Modifier.fillMaxSize().padding(top = 4.dp),
    )
}

@Composable
private fun ChecklistBody(note: SmartDeskStore.Note, title: String, onSave: (SmartDeskStore.Note) -> Unit) {
    val items = remember(note.id) {
        androidx.compose.runtime.mutableStateListOf<SmartDeskStore.CheckItem>().apply {
            addAll(if (note.items.isEmpty()) listOf(SmartDeskStore.CheckItem()) else note.items)
        }
    }

    fun persist() {
        onSave(note.copy(title = title, kind = "list",
            items = items.filter { it.text.isNotBlank() },
            updatedAt = System.currentTimeMillis()))
    }

    // Сохранение при уходе с экрана — как у текстовой заметки. Без него
    // «назад» из списка покупок молча терял правки, если не нажать «Готово»;
    // у текстовых заметок это починили, у списков забыли.
    val latest = rememberUpdatedState(::persist)
    DisposableEffect(note.id) { onDispose { latest.value.invoke() } }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        items.forEachIndexed { i, item ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = item.done,
                    onCheckedChange = { items[i] = items[i].copy(done = it) },
                    colors = CheckboxDefaults.colors(checkedColor = VpnkaColors.Accent),
                )
                BasicTextField(
                    value = item.text,
                    onValueChange = { items[i] = items[i].copy(text = it) },
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = if (item.done) VpnkaColors.TextMuted else VpnkaColors.TextStrong,
                        fontSize = 16.sp,
                        textDecoration = if (item.done) TextDecoration.LineThrough else TextDecoration.None,
                    ),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(VpnkaColors.Accent),
                    decorationBox = { inner ->
                        if (item.text.isEmpty()) Text("Пункт списка…", color = VpnkaColors.TextMuted, fontSize = 16.sp)
                        inner()
                    },
                    modifier = Modifier.weight(1f),
                )
                Text("✕", fontSize = 16.sp, color = VpnkaColors.TextMuted,
                    modifier = Modifier.clip(CircleShape).clickable { if (items.size > 1) items.removeAt(i) else items[0] = SmartDeskStore.CheckItem() }.padding(6.dp))
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                .clickable { items.add(SmartDeskStore.CheckItem()) }.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("＋", fontSize = 18.sp, color = VpnkaColors.Accent)
            Spacer(Modifier.width(8.dp))
            Text("Добавить пункт", fontFamily = VpnkaFonts.nunito800, fontSize = 14.sp, color = VpnkaColors.Accent)
        }
        Spacer(Modifier.height(10.dp))
        Box(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(11.dp)).background(VpnkaColors.Accent)
                .clickable { persist() }.padding(vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) { Text("Готово", fontFamily = VpnkaFonts.nunito800, fontSize = 14.sp, color = Color.White) }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun StyleBtn(label: String, bold: Boolean = false, italic: Boolean = false, underline: Boolean = false, strike: Boolean = false, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp))
            .background(VpnkaColors.CardServer)
            .border(1.dp, VpnkaColors.Hairline, RoundedCornerShape(10.dp)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontSize = 15.sp,
            color = VpnkaColors.TextStrong,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
            textDecoration = when {
                underline -> TextDecoration.Underline
                strike -> TextDecoration.LineThrough
                else -> TextDecoration.None
            },
        )
    }
}

// --- Rich-text span machinery --------------------------------------------

private fun styleFor(style: String): SpanStyle = when (style) {
    "bold" -> SpanStyle(fontWeight = FontWeight.Bold)
    "italic" -> SpanStyle(fontStyle = FontStyle.Italic)
    "underline" -> SpanStyle(textDecoration = TextDecoration.Underline)
    "strike" -> SpanStyle(textDecoration = TextDecoration.LineThrough)
    else -> SpanStyle()
}

/** Applies the stored spans as visual styling (identity offset — no length change). */
private class SpanTransformation(private val spans: List<SmartDeskStore.NoteSpan>) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val b = AnnotatedString.Builder(text.text)
        spans.forEach { sp ->
            val st = sp.start.coerceIn(0, text.length)
            val en = sp.end.coerceIn(st, text.length)
            if (en > st) b.addStyle(styleFor(sp.style), st, en)
        }
        return TransformedText(b.toAnnotatedString(), OffsetMapping.Identity)
    }
}

/** Add the style over [a,b), or remove it if the range is already fully styled. */
private fun toggleSpan(spans: List<SmartDeskStore.NoteSpan>, a: Int, b: Int, style: String): List<SmartDeskStore.NoteSpan> {
    val same = spans.filter { it.style == style }
    val covered = (a until b).all { pos -> same.any { pos >= it.start && pos < it.end } }
    return if (covered) {
        val others = spans.filter { it.style != style }
        others + same.flatMap { subtractRange(it, a, b) }
    } else {
        spans + SmartDeskStore.NoteSpan(a, b, style)
    }
}

/** A span minus the [a,b) window (0, 1, or 2 remaining pieces). */
private fun subtractRange(sp: SmartDeskStore.NoteSpan, a: Int, b: Int): List<SmartDeskStore.NoteSpan> {
    val out = mutableListOf<SmartDeskStore.NoteSpan>()
    if (sp.start < a) out.add(sp.copy(end = minOf(sp.end, a)))
    if (sp.end > b) out.add(sp.copy(start = maxOf(sp.start, b)))
    return out.filter { it.end > it.start }
}

/** Shift span offsets when the text is edited, so styling stays on its words. */
private fun remapSpans(spans: List<SmartDeskStore.NoteSpan>, old: String, new: String): List<SmartDeskStore.NoteSpan> {
    if (old == new) return spans
    val minLen = minOf(old.length, new.length)
    var p = 0
    while (p < minLen && old[p] == new[p]) p++
    var s = 0
    while (s < minLen - p && old[old.length - 1 - s] == new[new.length - 1 - s]) s++
    val delta = new.length - old.length
    val changeStart = p
    val oldChangeEnd = old.length - s
    return spans.mapNotNull { sp ->
        var st = sp.start
        var en = sp.end
        st = if (st >= oldChangeEnd) st + delta else minOf(st, changeStart)
        en = if (en >= oldChangeEnd) en + delta else minOf(en, changeStart)
        st = st.coerceIn(0, new.length)
        en = en.coerceIn(0, new.length)
        if (en > st) sp.copy(start = st, end = en) else null
    }
}
