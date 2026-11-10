<template>
  <aside class="comment-panel">
    <div class="comment-panel-head">
      <div>
        <strong>{{ tab === 'template' ? '模板要求' : (templateId ? '模板批注' : '文档批注') }}</strong>
        <span>{{ activeComments.length }} 条{{ tab === 'template' ? '要求' : '批注' }}</span>
      </div>
      <button class="comment-close" title="关闭" @click="$emit('close')">×</button>
    </div>
    <div v-if="templateCommentCount && comments.length" class="comment-tabs">
      <button class="comment-tab" :class="{ active: tab === 'template' }"
              @click="$emit('update:tab', 'template')">模板要求 {{ templateCommentCount }}</button>
      <button class="comment-tab" :class="{ active: tab === 'document' }"
              @click="$emit('update:tab', 'document')">文档批注 {{ comments.length }}</button>
    </div>
    <div class="comment-panel-body">
      <div v-for="comment in activeComments" :key="`${tab}-${comment.id}`" class="comment-item">
        <div class="comment-meta">
          <span class="comment-avatar">{{ (comment.author || '批').slice(0, 1) }}</span>
          <span class="comment-author">{{ comment.author || '未知作者' }}</span>
          <span v-if="comment.date" class="comment-date">{{ formatCommentDate(comment.date) }}</span>
        </div>
        <button v-if="!externalTemplate" class="comment-anchor" @click="$emit('focus', comment)">
          {{ comment.anchorPath || '文档批注' }}<span>定位 →</span>
        </button>
        <span v-else class="comment-anchor static">
          {{ comment.anchorPath || '模板批注' }}<span>模板位置</span>
        </span>
        <p class="comment-text">{{ comment.text || '（空批注）' }}</p>
      </div>
    </div>
  </aside>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  comments: { type: Array, default: () => [] },
  templateComments: { type: Array, default: () => [] },
  templateId: { type: [String, Number], default: null },
  tab: { type: String, default: 'document' }
})
defineEmits(['update:tab', 'close', 'focus'])

const templateCommentCount = computed(() => props.templateComments.length)
const activeComments = computed(() => props.tab === 'template' ? props.templateComments : props.comments)
const externalTemplate = computed(() => props.tab === 'template' && templateCommentCount.value > 0)

function formatCommentDate(value) {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ''
  const pad = n => String(n).padStart(2, '0')
  return `${pad(date.getMonth() + 1)}/${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}
</script>

<style scoped>
.comment-panel {
  position: absolute; top: 58px; right: 14px; bottom: 14px; z-index: 40;
  width: 330px; max-width: calc(100% - 28px);
  display: flex; flex-direction: column;
  background: rgba(255, 255, 255, 0.98);
  border: 1px solid #eadfca; border-radius: 12px;
  box-shadow: 0 18px 45px rgba(30, 41, 59, 0.2); overflow: hidden;
}
.comment-panel-head {
  display: flex; align-items: flex-start; justify-content: space-between;
  padding: 14px 15px 11px; border-bottom: 1px solid #f0e8d9;
  background: linear-gradient(135deg, #fffbeb, #fff);
}
.comment-panel-head strong { display: block; font-size: 13px; color: #3f3a2f; }
.comment-panel-head span { display: block; margin-top: 3px; font-size: 10px; color: #a08a5f; }
.comment-tabs {
  display: flex; gap: 4px; margin: 10px 12px 0; padding: 3px;
  border-radius: 8px; background: #f6f1e5;
}
.comment-tab {
  flex: 1; height: 27px; border: none; border-radius: 6px;
  background: transparent; color: #8b7b5c; font-size: 10.5px; cursor: pointer;
}
.comment-tab:hover { color: #a16207; }
.comment-tab.active { background: #fff; color: #a16207; font-weight: 600; box-shadow: 0 1px 4px rgba(120, 90, 30, 0.14); }
.comment-close { border: none; background: transparent; color: #a8a29e; font-size: 20px; line-height: 1; cursor: pointer; padding: 0 2px; }
.comment-panel-body { flex: 1; min-height: 0; overflow-y: auto; padding: 12px; }
.comment-item { padding: 11px; border: 1px solid #efe8da; border-radius: 9px; background: #fffdf8; }
.comment-item + .comment-item { margin-top: 10px; }
.comment-meta { display: flex; align-items: center; gap: 6px; }
.comment-avatar {
  display: inline-flex; align-items: center; justify-content: center;
  width: 20px; height: 20px; border-radius: 50%;
  background: #f4d58d; color: #714d0a; font-size: 10px; font-weight: 700;
}
.comment-author { font-size: 11px; font-weight: 600; color: #4b4538; }
.comment-date { margin-left: auto; font-size: 9px; color: #b0a48c; }
.comment-anchor {
  display: flex; align-items: center; justify-content: space-between;
  width: 100%; margin: 8px 0 6px; padding: 5px 7px;
  border: 1px solid #f1e6cf; border-radius: 5px;
  background: #fffaf0; color: #9a6b13; font-size: 9.5px;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace; cursor: pointer;
}
.comment-anchor:hover { background: #fef3c7; }
.comment-anchor span { font-family: inherit; color: #b9892d; }
.comment-anchor.static { cursor: default; border-style: dashed; color: #9a8a68; background: #fcfaf4; }
.comment-anchor.static span { color: #b3a485; }
.comment-text { margin: 0; color: #5c5648; font-size: 11px; line-height: 1.75; white-space: pre-wrap; word-break: break-word; }
</style>
