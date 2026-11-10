<template>
  <div class="preview-shell">
    <div class="preview-toolbar">
      <div class="doc-info">
        <div class="doc-icon"><el-icon><Document /></el-icon></div>
        <div class="doc-meta">
          <div class="doc-name" :title="document?.fileName">{{ document?.fileName || '文档预览' }}</div>
          <div class="doc-stats" v-if="stats">
            {{ stats.paragraphCount || 0 }} 段落 · {{ stats.tableCount || 0 }} 表格 ·
            {{ stats.imageCount || 0 }} 图片 · {{ stats.characterCount || 0 }} 字符
          </div>
          <div class="doc-stats" v-else>正在等待文档解析结果</div>
        </div>
      </div>
      <div class="toolbar-right">
        <div class="preview-mode">
          <button
            class="mode-btn"
            :class="{ active: previewMode === 'wps' }"
            title="按原始 DOCX 还原分页、字体、表格与页眉页脚"
            @click="setMode('wps')"
          >WPS 版式</button>
          <button
            class="mode-btn"
            :class="{ active: previewMode === 'structure' }"
            title="结构预览，可精确定位每一个差异锚点"
            @click="setMode('structure')"
          >结构标注</button>
        </div>
        <div v-if="diffs.length" class="legend">
          <span><i class="legend-dot high"></i>高危</span>
          <span><i class="legend-dot middle"></i>中危</span>
          <span><i class="legend-dot low"></i>低危</span>
        </div>
        <button
          v-if="totalCommentCount"
          class="comment-toggle"
          :class="{ active: commentsOpen }"
          title="查看模板要求与文档批注"
          @click="commentsOpen = !commentsOpen"
        >
          <el-icon><ChatDotRound /></el-icon>
          批注 {{ totalCommentCount }}
        </button>
        <div class="zoom-group">
          <button class="zoom-btn" title="缩小" @click="changeZoom(-1)">−</button>
          <span class="zoom-value">{{ Math.round(zoom * 100) }}%</span>
          <button class="zoom-btn" title="放大" @click="changeZoom(1)">+</button>
          <button class="zoom-reset" title="重置" @click="zoom = 1">重置</button>
        </div>
      </div>
    </div>

    <DocumentCommentPanel
      v-if="totalCommentCount && commentsOpen"
      :comments="comments"
      :template-comments="templateComments"
      :template-id="templateId"
      :tab="activeCommentTab"
      @update:tab="activeCommentTab = $event"
      @close="commentsOpen = false"
      @focus="focusComment"
    />

    <div v-if="!document" class="preview-empty">
      <div class="empty-illustration">
        <el-icon><DocumentCopy /></el-icon>
      </div>
      <h3>暂无可预览的文档</h3>
      <p>任务解析完成后，这里将按版面结构还原文档并高亮差异位置。</p>
    </div>

    <div
      v-else-if="previewMode === 'wps'"
      class="wps-canvas"
      ref="wpsCanvas"
      v-loading="wpsLoading"
      element-loading-text="正在还原 WPS 版式..."
      element-loading-background="rgba(238, 241, 247, 0.72)"
    >
      <div v-if="wpsError" class="wps-error">
        <el-icon><WarningFilled /></el-icon>
        <div>
          <strong>原始版式渲染失败</strong>
          <p>{{ wpsError }}</p>
        </div>
        <div class="wps-error-actions">
          <el-button size="small" @click="renderWps">重试</el-button>
          <el-button size="small" type="primary" plain @click="setMode('structure')">切换到结构标注</el-button>
        </div>
      </div>
      <div ref="wpsStyleHost" class="wps-style-host"></div>
      <div ref="wpsBody" class="wps-body" :style="{ zoom }" @click="onWpsClick"></div>
    </div>

    <div v-else class="preview-canvas" ref="structureCanvas">
      <div
        v-for="section in sections"
        :key="section.index"
        class="section-wrap"
        :style="{ zoom }"
      >
        <div class="section-label">
          <span>第 {{ section.index + 1 }} 节</span>
          <span class="section-page">
            {{ formatPage(section.pageSetup) }}
          </span>
        </div>

        <div
          class="paper"
          :data-anchor="`body.section[${section.index}]`"
          :class="markClass(`body.section[${section.index}]`)"
          :style="paperStyle(section.pageSetup)"
        >
          <div v-if="sectionHeader(section)" class="paper-header" :style="headerFooterStyle(section.pageSetup)">
            <div
              v-for="p in sectionHeader(section).paragraphs"
              :key="p.index"
              :data-anchor="`header[${sectionHeader(section).index}].p[${p.index}]`"
              class="hf-line"
              :class="markClass(`header[${sectionHeader(section).index}].p[${p.index}]`)"
              :style="paragraphStyle(p)"
            >
              <span v-for="(run, ri) in p.runs" :key="ri" :style="runStyle(run)">{{ run.text }}</span>
              <span v-if="!p.runs?.length" class="empty-text">{{ p.text || ' ' }}</span>
              <img
                v-for="img in paragraphImages(p, 'header', sectionHeader(section).index)"
                :key="`h-${img.index}`"
                :src="imageUrl(img.index)"
                class="doc-inline-image hf-image"
                :alt="imageAlt(img)"
                @click.stop
              />
              <span v-if="diffCount(`header[${sectionHeader(section).index}].p[${p.index}]`)" class="diff-pin">
                {{ diffCount(`header[${sectionHeader(section).index}].p[${p.index}]`) }}
              </span>
            </div>
          </div>

          <div class="paper-body" :style="bodyStyle(section.pageSetup)">
            <template v-for="block in section.blocks" :key="block.blockIndex">
              <template v-if="block.type === 'paragraph' && block.paragraph">
                <p
                  class="doc-paragraph"
                  :data-anchor="`body.p[${block.paragraph.index}]`"
                  :class="markClass(`body.p[${block.paragraph.index}]`)"
                  :style="paragraphStyle(block.paragraph)"
                  @click="onParagraphClick(block.paragraph)"
                >
                  <span v-if="block.paragraph.numbering?.label" class="numbering-label">
                    {{ block.paragraph.numbering.label }}
                  </span>
                  <span
                    v-for="(run, ri) in block.paragraph.runs"
                    :key="ri"
                    class="run"
                    :style="runStyle(run)"
                  >{{ run.text }}</span>
                  <span v-if="!block.paragraph.runs?.length" class="empty-text">
                    {{ block.paragraph.text || '（空段落）' }}
                  </span>
                  <img
                    v-for="img in paragraphImages(block.paragraph, 'body')"
                    :key="`b-${img.index}`"
                    :src="imageUrl(img.index)"
                    class="doc-inline-image"
                    :alt="imageAlt(img)"
                    @click.stop
                  />
                  <span
                    v-if="diffCount(`body.p[${block.paragraph.index}]`)"
                    class="diff-pin"
                    :title="`${diffCount(`body.p[${block.paragraph.index}]`)} 处格式差异`"
                  >
                    {{ diffCount(`body.p[${block.paragraph.index}]`) }}
                  </span>
                </p>
              </template>

              <div
                v-else-if="block.type === 'table' && block.table"
                class="doc-table-wrap"
                :data-anchor="`body.table[${block.table.index}]`"
                :class="markClass(`body.table[${block.table.index}]`)"
              >
                <div class="table-caption">
                  <span>表 {{ block.table.index + 1 }}</span>
                  <span v-if="block.table.columnCount">{{ block.table.columnCount }} 列 · {{ block.table.rowCount }} 行</span>
                  <span
                    v-if="diffCount(`body.table[${block.table.index}]`)"
                    class="caption-diff"
                  >{{ diffCount(`body.table[${block.table.index}]`) }} 处差异</span>
                </div>
                <table class="doc-table">
                  <tbody>
                    <tr v-for="row in block.table.rows" :key="row.index">
                      <td
                        v-for="cell in row.cells"
                        :key="cell.index"
                        :colspan="cell.gridSpan || 1"
                        :data-anchor="`body.table[${block.table.index}].r[${row.index}].c[${cell.index}]`"
                        :class="[
                          markClass(`body.table[${block.table.index}].r[${row.index}].c[${cell.index}]`),
                          { 'is-vmerge': cell.vMerge === 'continue' }
                        ]"
                        :style="cellStyle(cell)"
                      >
                        <div v-if="cell.images?.length" class="cell-images">
                          <img
                            v-for="img in cell.images"
                            :key="`c-${img.index}`"
                            :src="imageUrl(img.index)"
                            class="doc-inline-image cell-image"
                            :alt="imageAlt(img)"
                            @click.stop
                          />
                        </div>
                        <span>{{ cell.text || '' }}</span>
                        <span
                          v-if="diffCount(`body.table[${block.table.index}].r[${row.index}].c[${cell.index}]`)"
                          class="diff-pin cell-pin"
                        >{{ diffCount(`body.table[${block.table.index}].r[${row.index}].c[${cell.index}]`) }}</span>
                      </td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </template>

            <div v-if="!section.blocks?.length" class="empty-section">本节暂无正文内容</div>
          </div>

          <div v-if="sectionFooter(section)" class="paper-footer" :style="headerFooterStyle(section.pageSetup)">
            <div
              v-for="p in sectionFooter(section).paragraphs"
              :key="p.index"
              :data-anchor="`footer[${sectionFooter(section).index}].p[${p.index}]`"
              class="hf-line"
              :class="markClass(`footer[${sectionFooter(section).index}].p[${p.index}]`)"
              :style="paragraphStyle(p)"
            >
              <span v-for="(run, ri) in p.runs" :key="ri" :style="runStyle(run)">{{ run.text }}</span>
              <span v-if="!p.runs?.length" class="empty-text">{{ p.text || ' ' }}</span>
              <img
                v-for="img in paragraphImages(p, 'footer', sectionFooter(section).index)"
                :key="`f-${img.index}`"
                :src="imageUrl(img.index)"
                class="doc-inline-image hf-image"
                :alt="imageAlt(img)"
                @click.stop
              />
              <span v-if="diffCount(`footer[${sectionFooter(section).index}].p[${p.index}]`)" class="diff-pin">
                {{ diffCount(`footer[${sectionFooter(section).index}].p[${p.index}]`) }}
              </span>
            </div>
          </div>

          <div class="page-number">{{ section.index + 1 }}</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { renderAsync } from 'docx-preview'
import { getTaskDocument, getTaskImage } from '@/api/task'
import { getTemplateDocument, getTemplateImage } from '@/api/template'

const props = defineProps({
  document: { type: Object, default: null },
  diffs: { type: Array, default: () => [] },
  activeDiff: { type: Object, default: null },
  /** 任务详情页传入，用于拉取受保护的 DOCX 与内嵌图片 */
  taskId: { type: [Number, String], default: null },
  /** 模板库预览传入，用于拉取模板 DOCX 与内嵌图片 */
  templateId: { type: [Number, String], default: null },
  /** 任务详情页传入，展示模板本身的要求批注（与目标文档批注分开） */
  templateComments: { type: Array, default: () => [] }
})

const emit = defineEmits(['select-diff'])
const zoom = ref(1)
const previewMode = ref('wps')
const imageUrlMap = ref({})
const wpsBody = ref(null)
const wpsStyleHost = ref(null)
const wpsCanvas = ref(null)
const wpsLoading = ref(false)
const wpsError = ref('')
const structureCanvas = ref(null)
const commentsOpen = ref(false)
const activeCommentTab = ref('document')
let imageLoadToken = 0
let wpsToken = 0
let wpsTimer = null
let wpsParagraphIndexMap = new Map()
const wpsDiffBuckets = new WeakMap()

const sections = computed(() => props.document?.sections || [])
const stats = computed(() => props.document?.statistics || null)
const comments = computed(() => props.document?.comments || [])
const templateCommentCount = computed(() => props.templateComments?.length || 0)
const totalCommentCount = computed(() => comments.value.length + templateCommentCount.value)
const activeComments = computed(() => (
  activeCommentTab.value === 'template' ? props.templateComments : comments.value
))
/** 任务页展示的是模板批注，锚点属于另一份 DOCX，不能在当前预览里定位。 */
const isExternalTemplateComment = computed(() => (
  activeCommentTab.value === 'template' && templateCommentCount.value > 0
))

/**
 * docx-preview 直接解析原始 DOCX，保留分页、页边距、字体、表格和页眉页脚，
 * 比按元数据自绘更接近 WPS/维普的阅读效果；差异高亮在渲染完成后叠加。
 */
const WPS_OPTIONS = {
  className: 'docx',
  inWrapper: true,
  hideWrapperOnPrint: true,
  ignoreWidth: false,
  ignoreHeight: false,
  ignoreFonts: false,
  breakPages: true,
  ignoreLastRenderedPageBreak: true,
  renderHeaders: true,
  renderFooters: true,
  renderFootnotes: true,
  renderEndnotes: true,
  useBase64URL: true,
  experimental: true,
  trimXmlDeclaration: true,
  debug: false
}

function setMode(mode) {
  if (!['wps', 'structure'].includes(mode) || previewMode.value === mode) return
  previewMode.value = mode
  if (mode === 'wps') scheduleWpsRender()
  else loadTaskImages()
}

function scheduleWpsRender() {
  if (previewMode.value !== 'wps') return
  if (wpsTimer) clearTimeout(wpsTimer)
  // 等 v-else-if 的容器真正挂载后再渲染，避免 ref 为空
  wpsTimer = setTimeout(renderWps, 0)
}

async function renderWps() {
  if (previewMode.value !== 'wps') return
  if (!props.document) return
  const taskId = props.taskId
  const templateId = props.templateId
  if (!taskId && !templateId) {
    wpsError.value = '缺少文档 ID，无法读取原始 DOCX'
    return
  }
  const token = ++wpsToken
  wpsLoading.value = true
  wpsError.value = ''
  await nextTick()
  if (!wpsBody.value) {
    if (token === wpsToken) wpsLoading.value = false
    return
  }
  try {
    const response = templateId
      ? await getTemplateDocument(templateId)
      : await getTaskDocument(taskId)
    if (token !== wpsToken) return
    const data = response.data
    wpsParagraphIndexMap = new Map()
    wpsBody.value.innerHTML = ''
    if (wpsStyleHost.value) wpsStyleHost.value.innerHTML = ''
    await renderAsync(data, wpsBody.value, wpsStyleHost.value, WPS_OPTIONS)
    if (token !== wpsToken) return
    await nextTick()
    wpsParagraphIndexMap = buildWpsParagraphIndexMap()
    fitWpsToWidth()
    await nextTick()
    decorateWpsDiffs()
    updateWpsActive()
    await nextTick()
    scrollToWpsActive()
  } catch (error) {
    if (token !== wpsToken) return
    console.warn('WPS 版式渲染失败', error)
    const status = error.response?.status
    const label = templateId ? '模板' : '任务'
    wpsError.value = status === 404
      ? `${label}原始 DOCX 已丢失，无法还原原始版式。可切换到结构标注继续查看。`
      : status === 403
        ? `无权读取该${label}的原始文档。`
        : '原始 DOCX 解析失败，可能是文件损坏或包含暂不支持的版式。'
  } finally {
    if (token === wpsToken) wpsLoading.value = false
  }
}

/**
 * 维普/ WPS 阅读器默认按可用宽度铺满页面，避免 A4 在窄侧栏里被横向裁切。
 * 只在新文档渲染完成时计算一次，用户随后仍可用缩放按钮微调。
 */
function fitWpsToWidth() {
  const canvas = wpsCanvas.value
  const page = wpsBody.value?.querySelector('section.docx')
  if (!canvas || !page) return
  const currentZoom = zoom.value || 1
  const pageWidth = page.getBoundingClientRect().width / currentZoom
  const available = canvas.clientWidth - 48
  if (pageWidth <= 0 || available <= 0) return
  const fit = Math.min(1, Math.max(0.55, available / pageWidth))
  zoom.value = Number(fit.toFixed(2))
}

function normalizeText(value) {
  return String(value || '')
    .replace(/\s+/g, '')
    .replace(/[“”‘’]/g, '"')
}

function diffAnchor(diff) {
  return diff?.anchorPath || diff?.location || ''
}

function diffSnippet(diff) {
  const raw = diff?.contentSnippet || diff?.actualFormat || diff?.templateFormat || ''
  return normalizeText(raw).slice(0, 30)
}

function wpsParagraphs() {
  const root = wpsBody.value
  if (!root) return []
  return [...root.querySelectorAll('section.docx > article > p')]
}

function wpsTables() {
  const root = wpsBody.value
  if (!root) return []
  return [...root.querySelectorAll('section.docx > article > table')]
}

function pickElement(list, index, snippet) {
  const candidate = Number.isInteger(index) ? list[index] : null
  if (candidate && (!snippet || normalizeText(candidate.innerText).includes(snippet))) {
    return candidate
  }
  if (snippet) {
    const matched = list.find(el => normalizeText(el.innerText).includes(snippet))
    if (matched) return matched
  }
  return candidate || null
}

function metadataParagraphs() {
  const list = []
  for (const section of props.document?.sections || []) {
    for (const block of section.blocks || []) {
      if (block.type === 'paragraph' && block.paragraph) list.push(block.paragraph)
    }
  }
  return list
}

/**
 * 解析元数据会跳过目录域（TOC）的中间结果，而 docx-preview 会把这些目录段落渲染出来，
 * 导致目录之后的 body.p[n] 直接按数组下标定位会整体偏移。这里按文本顺序做一次对齐，
 * 把元数据段落索引映射到真实渲染的 <p> 上；空段落用前后非空段落锚定。
 */
function buildWpsParagraphIndexMap() {
  const meta = metadataParagraphs()
  const rendered = wpsParagraphs()
  const map = new Map()
  let i = 0
  let j = 0
  const textOf = (value) => normalizeText(value)
  while (i < meta.length && j < rendered.length) {
    const metaText = textOf(meta[i].text)
    const renderText = textOf(rendered[j].innerText)
    if (metaText === renderText) {
      map.set(meta[i].index, rendered[j])
      i++
      j++
      continue
    }
    if (!metaText) {
      // 下一段有文字的元数据，若在渲染结果中更靠后，说明中间是目录等解析器未收录的段落。
      let next = i + 1
      while (next < meta.length && !textOf(meta[next].text)) next++
      if (next < meta.length) {
        const target = textOf(meta[next].text)
        let k = j + 1
        while (k < rendered.length && textOf(rendered[k].innerText) !== target) k++
        if (k < rendered.length) {
          j = k
          continue
        }
      }
      map.set(meta[i].index, rendered[j])
      i++
      j++
      continue
    }
    // 元数据有文字但渲染结果前插了额外段落（目录、域结果等），向后寻找同一段文本。
    const needle = metaText.slice(0, 28)
    let k = j + 1
    while (k < Math.min(rendered.length, j + 120) && !textOf(rendered[k].innerText).includes(needle)) k++
    if (k < rendered.length && textOf(rendered[k].innerText).includes(needle)) {
      j = k
      continue
    }
    map.set(meta[i].index, rendered[j])
    i++
    j++
  }
  return map
}

function resolveWpsTarget(anchor, diff = null) {
  if (!anchor || !wpsBody.value) return null
  if (anchor.startsWith('body.section[')) {
    return wpsBody.value.querySelector('section.docx') || wpsBody.value
  }
  let match = /^body\.p\[(\d+)\]$/.exec(anchor)
  if (match) {
    const index = Number(match[1])
    const snippet = diffSnippet(diff)
    const mapped = wpsParagraphIndexMap.get(index)
    // 同文段落很多（目录、页眉页脚、重复标题）。映射结果完整命中时优先使用，
    // 否则再全局找完整文本一致的段落，避免“绪论”落到包含该词的正文。
    if (mapped && snippet && normalizeText(mapped.innerText) === snippet) return mapped
    if (snippet) {
      const exact = wpsParagraphs().find(el => normalizeText(el.innerText) === snippet)
      if (exact) return exact
    }
    if (mapped && (!snippet || normalizeText(mapped.innerText).startsWith(snippet))) {
      return mapped
    }
    return pickElement(wpsParagraphs(), index, snippet)
  }
  match = /^body\.table\[(\d+)\](?:\.r\[(\d+)\]\.c\[(\d+)\])?$/.exec(anchor)
  if (match) {
    const table = pickElement(wpsTables(), Number(match[1]), diffSnippet(diff))
    if (!table) return null
    if (match[2] === undefined) return table
    const row = table.rows?.[Number(match[2])]
    return row?.cells?.[Number(match[3])] || table
  }
  match = /^(header|footer)\[(\d+)\]\.p\[(\d+)\]$/.exec(anchor)
  if (match) {
    const groups = [...wpsBody.value.querySelectorAll(`section.docx > ${match[1]}`)]
    if (!groups.length) return null
    const group = groups[Number(match[2])] || groups[0]
    const paragraphs = [...group.querySelectorAll('p')]
    return paragraphs[Number(match[3])] || paragraphs[0] || group
  }
  return null
}

function decorateWpsDiffs() {
  const root = wpsBody.value
  if (!root) return
  root.querySelectorAll('.wps-diff').forEach((el) => {
    el.classList.remove('wps-diff', 'wps-diff-high', 'wps-diff-middle', 'wps-diff-low', 'is-active')
    delete el.dataset.diffCount
    delete el.dataset.wpsAnchors
  })

  const buckets = new Map()
  for (const diff of props.diffs || []) {
    if (isSummaryDiff(diff)) continue
    const anchor = diffAnchor(diff)
    const target = resolveWpsTarget(anchor, diff)
    if (!target) continue
    if (!buckets.has(target)) buckets.set(target, { anchors: [], diffs: [] })
    const bucket = buckets.get(target)
    if (anchor && !bucket.anchors.includes(anchor)) bucket.anchors.push(anchor)
    bucket.diffs.push(diff)
  }

  for (const [el, bucket] of buckets) {
    const severity = bucket.diffs.some(d => d.severity === 'high')
      ? 'high'
      : bucket.diffs.some(d => d.severity === 'middle') ? 'middle' : 'low'
    el.classList.add('wps-diff', `wps-diff-${severity}`)
    el.dataset.diffCount = String(bucket.diffs.length)
    el.dataset.wpsAnchors = bucket.anchors.join('|')
    wpsDiffBuckets.set(el, bucket.diffs)
  }
}

/** 点击 WPS 预览中的差异段落或右上角徽标，联动左侧差异列表。 */
function onWpsClick(event) {
  const target = event.target?.closest?.('.wps-diff')
  if (!target || !wpsBody.value?.contains(target)) return
  const bucket = wpsDiffBuckets.get(target)
  if (!bucket?.length) return
  const current = bucket.find(d => d.id === props.activeDiff?.id)
  emit('select-diff', current || bucket[0])
}

function findWpsTargetByAnchor(anchor) {
  const root = wpsBody.value
  if (!root || !anchor) return null
  const marked = [...root.querySelectorAll('.wps-diff')].find((el) =>
    (el.dataset.wpsAnchors || '').split('|').includes(anchor)
  )
  return marked || resolveWpsTarget(anchor)
}

function updateWpsActive() {
  const root = wpsBody.value
  if (!root) return
  root.querySelectorAll('.wps-diff.is-active').forEach(el => el.classList.remove('is-active'))
  const target = findWpsTargetByAnchor(activeAnchor.value)
  if (target) target.classList.add('is-active')
}

function scrollToWpsActive() {
  if (!activeAnchor.value) return
  const target = findWpsTargetByAnchor(activeAnchor.value)
  if (target) target.scrollIntoView({ behavior: 'smooth', block: 'center' })
}

/**
 * 只按容器类型 + 段落序号取图，避免把页眉图片渲染进正文。
 * 老缓存没有 containerType 时按正文处理，保持向后兼容。
 */
function paragraphImages(paragraph, containerType, containerIndex = null) {
  const images = props.document?.images || []
  return images.filter((image) => {
    const type = image.containerType || 'body'
    if (type !== containerType || image.paragraphIndex !== paragraph.index) return false
    if (type === 'header') return image.headerIndex === containerIndex
    if (type === 'footer') return image.footerIndex === containerIndex
    return true
  })
}

function imageUrl(index) {
  return imageUrlMap.value[index]
}

function imageAlt(image) {
  return image?.altText || image?.name || '文档图片'
}

function revokeImageUrls() {
  for (const url of Object.values(imageUrlMap.value)) {
    if (url) URL.revokeObjectURL(url)
  }
  imageUrlMap.value = {}
}

async function loadTaskImages() {
  const token = ++imageLoadToken
  revokeImageUrls()
  const taskId = props.taskId
  const templateId = props.templateId
  const images = props.document?.images || []
  if ((!taskId && !templateId) || !images.length) return
  const loaded = await Promise.all(images.map(async (image) => {
    try {
      const response = templateId
        ? await getTemplateImage(templateId, image.index)
        : await getTaskImage(taskId, image.index)
      const raw = response.data
      const blob = raw instanceof Blob ? raw : new Blob([raw], { type: image.contentType || 'image/png' })
      return [image.index, URL.createObjectURL(blob)]
    } catch (error) {
      console.warn(`图片 ${image.index} 加载失败`, error)
      return null
    }
  }))
  if (token !== imageLoadToken) {
    // 文档已切换，丢弃迟到的旧图片 URL
    loaded.filter(Boolean).forEach(([, url]) => URL.revokeObjectURL(url))
    return
  }
  const next = {}
  for (const item of loaded) {
    if (item) next[item[0]] = item[1]
  }
  imageUrlMap.value = next
}

let imageLoadTimer = null

function scheduleImageLoad() {
  if (imageLoadTimer) clearTimeout(imageLoadTimer)
  imageLoadTimer = setTimeout(loadTaskImages, 0)
}

watch(
  [() => props.taskId, () => props.templateId, () => props.document],
  () => {
    commentsOpen.value = false
    activeCommentTab.value = props.templateComments?.length ? 'template' : 'document'
    if (previewMode.value === 'wps') scheduleWpsRender()
    else scheduleImageLoad()
  }
)

watch(commentsOpen, (open) => {
  if (!open) return
  activeCommentTab.value = props.templateComments?.length ? 'template' : 'document'
})

watch([() => props.templateComments, comments], () => {
  if (activeCommentTab.value === 'template' && !templateCommentCount.value) {
    activeCommentTab.value = 'document'
  }
  if (activeCommentTab.value === 'document' && !comments.value.length && templateCommentCount.value) {
    activeCommentTab.value = 'template'
  }
})

watch(previewMode, (mode) => {
  if (mode === 'wps') scheduleWpsRender()
  else scheduleImageLoad()
})

function onWindowResize() {
  if (previewMode.value === 'wps') fitWpsToWidth()
}

onMounted(() => {
  window.addEventListener('resize', onWindowResize)
  if (previewMode.value === 'wps') scheduleWpsRender()
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', onWindowResize)
  if (imageLoadTimer) clearTimeout(imageLoadTimer)
  if (wpsTimer) clearTimeout(wpsTimer)
  imageLoadToken++
  wpsToken++
  revokeImageUrls()
})


/**
 * 数量类差异没有具体段落锚点，旧实现统一落在 body.p[0]，
 * 在 WPS 预览里会把“图片数量/段落数量/页眉页脚数量”的徽标错误地盖到封面上。
 * 这类汇总差异只保留在左侧列表，不再做正文内联标注。
 */
function isSummaryDiff(diff) {
  return diff?.anchorPath === 'body.p[0]'
    && !diff?.contentSnippet
    && ['图片', '段落数量', '页眉页脚'].includes(diff?.diffType)
}

const diffMap = computed(() => {
  const map = new Map()
  for (const diff of props.diffs || []) {
    if (isSummaryDiff(diff)) continue
    const key = diff.anchorPath || diff.location
    if (!key) continue
    if (!map.has(key)) map.set(key, [])
    map.get(key).push(diff)
  }
  return map
})

const activeAnchor = computed(() => {
  return props.activeDiff?.anchorPath || props.activeDiff?.location || ''
})

function diffCount(anchor) {
  return diffMap.value.get(anchor)?.length || 0
}

function markClass(anchor) {
  const count = diffCount(anchor)
  if (!count) return {}
  const list = diffMap.value.get(anchor)
  const top = list.some(d => d.severity === 'high') ? 'high' : list.some(d => d.severity === 'middle') ? 'middle' : 'low'
  return {
    'has-diff': true,
    [`diff-${top}`]: true,
    'is-active': activeAnchor.value === anchor || activeAnchor.value.startsWith(`${anchor}.`)
  }
}

function paperStyle(setup) {
  const width = Math.max(320, (setup?.widthPt || 595) * 1.333)
  return {
    width: `${width}px`,
    minHeight: `${Math.max(480, (setup?.heightPt || 842) * 1.333)}px`
  }
}

function bodyStyle(setup) {
  return {
    paddingTop: `${(setup?.topMarginPt ?? 72) * 1.05}px`,
    paddingBottom: `${(setup?.bottomMarginPt ?? 72) * 1.05}px`,
    paddingLeft: `${(setup?.leftMarginPt ?? 90) * 1.05}px`,
    paddingRight: `${(setup?.rightMarginPt ?? 90) * 1.05}px`
  }
}

function headerFooterStyle(setup) {
  return {
    paddingLeft: `${(setup?.leftMarginPt ?? 90) * 1.05}px`,
    paddingRight: `${(setup?.rightMarginPt ?? 90) * 1.05}px`
  }
}

function formatPage(setup) {
  if (!setup) return 'A4'
  const orientation = setup.orientation === 'landscape' ? '横向' : '纵向'
  return `${orientation} · ${formatPt(setup.widthPt)} × ${formatPt(setup.heightPt)}`
}

function formatPt(value) {
  if (!value) return '—'
  return `${Number(value).toFixed(0)}pt`
}

function sectionHeader(section) {
  const list = props.document?.headers || []
  if (!list.length) return null
  const types = section.pageSetup?.headerTypes || []
  return list.find(h => types.includes(h.headerFooterType)) || (section.index === 0 ? list[0] : null)
}

function sectionFooter(section) {
  const list = props.document?.footers || []
  if (!list.length) return null
  const types = section.pageSetup?.footerTypes || []
  return list.find(f => types.includes(f.headerFooterType)) || (section.index === 0 ? list[0] : null)
}

function paragraphStyle(p) {
  if (!p) return {}
  const first = p.runs?.[0] || {}
  const align = {
    both: 'justify',
    justify: 'justify',
    center: 'center',
    right: 'right',
    end: 'right',
    distribute: 'justify'
  }[p.alignment] || 'left'
  const spacing = p.spacing || {}
  const indent = p.indentation || {}
  return {
    textAlign: align,
    marginTop: `${(spacing.beforePt || 0) * 1.1}px`,
    marginBottom: `${(spacing.afterPt || 0) * 1.1}px`,
    marginLeft: `${(indent.leftPt || 0) * 1.1}px`,
    marginRight: `${(indent.rightPt || 0) * 1.1}px`,
    textIndent: indent.firstLinePt ? `${indent.firstLinePt * 1.1}px` : undefined,
    paddingLeft: indent.hangingPt ? `${indent.hangingPt * 1.1}px` : undefined,
    lineHeight: spacing.lineSpacingLines || (spacing.lineSpacingPt ? `${spacing.lineSpacingPt}px` : 1.55),
    fontFamily: first.fontEastAsia || first.fontAscii || undefined,
    fontSize: first.fontSizePt ? `${first.fontSizePt}px` : undefined,
    color: first.color ? `#${String(first.color).replace(/^#/, '')}` : undefined
  }
}

function runStyle(run) {
  if (!run) return {}
  const style = {}
  if (run.fontEastAsia || run.fontAscii) style.fontFamily = run.fontEastAsia || run.fontAscii
  if (run.fontSizePt) style.fontSize = `${run.fontSizePt}px`
  if (run.bold) style.fontWeight = 700
  if (run.italic) style.fontStyle = 'italic'
  if (run.underline && run.underline !== 'none') style.textDecoration = 'underline'
  if (run.strike) style.textDecoration = `${style.textDecoration || ''} line-through`.trim()
  if (run.color) style.color = `#${String(run.color).replace(/^#/, '')}`
  if (run.highlight && !['none', 'clear'].includes(run.highlight)) {
    style.backgroundColor = run.highlight.startsWith('#') ? run.highlight : run.highlight
  }
  if (run.characterSpacingPt) style.letterSpacing = `${run.characterSpacingPt}px`
  if (run.verticalAlign === 'superscript') style.verticalAlign = 'super'
  if (run.verticalAlign === 'subscript') style.verticalAlign = 'sub'
  return style
}

function cellStyle(cell) {
  return {
    textAlign: cell.alignment === 'center' ? 'center' : cell.alignment === 'right' ? 'right' : 'left',
    verticalAlign: cell.verticalAlign === 'center' ? 'middle' : cell.verticalAlign || 'top',
    fontWeight: cell.bold ? 700 : 400,
    fontSize: cell.fontSizePt ? `${cell.fontSizePt}px` : undefined,
    backgroundColor: cell.shading && cell.shading !== 'clear' ? normalizeShading(cell.shading) : undefined,
    width: cell.widthPt ? `${cell.widthPt * 1.1}px` : undefined
  }
}

function normalizeShading(value) {
  if (String(value).startsWith('#')) return value
  const parts = String(value).split(' ')
  const hex = parts.find(p => /^[0-9a-f]{6}$/i.test(p))
  return hex ? `#${hex}` : undefined
}

function onParagraphClick(p) {
  const anchor = `body.p[${p.index}]`
  const diff = diffMap.value.get(anchor)?.[0]
  if (diff) emit('select-diff', diff)
}

function changeZoom(delta) {
  zoom.value = Math.min(1.5, Math.max(0.7, Number((zoom.value + delta * 0.1).toFixed(2))))
}


async function focusComment(comment) {
  commentsOpen.value = false
  previewMode.value = 'structure'
  await nextTick()
  const target = findTarget(comment.anchorPath)
  if (!target) return
  target.scrollIntoView({ behavior: 'smooth', block: 'center' })
  target.classList.add('comment-focus')
  window.setTimeout(() => target.classList.remove('comment-focus'), 2400)
}

function findTarget(anchor) {
  if (!anchor) return null
  const root = structureCanvas.value
  if (!root) return null
  const exact = root.querySelector(`[data-anchor="${anchor}"]`)
  if (exact) return exact
  const candidates = [...root.querySelectorAll('[data-anchor]')]
    .filter(el => anchor.startsWith(el.dataset.anchor))
    .sort((a, b) => b.dataset.anchor.length - a.dataset.anchor.length)
  return candidates[0] || null
}

async function scrollToActive() {
  if (!activeAnchor.value) return
  await nextTick()
  if (previewMode.value === 'wps') {
    updateWpsActive()
    scrollToWpsActive()
    return
  }
  const target = findTarget(activeAnchor.value)
  if (target) {
    target.scrollIntoView({ behavior: 'smooth', block: 'center' })
  }
}

watch(
  [() => props.activeDiff?.id, () => props.activeDiff?.anchorPath, previewMode],
  scrollToActive
)
</script>

<style scoped src="./DocumentPreview.css"></style>
