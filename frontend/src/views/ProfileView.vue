<template>
  <div class="dc-page profile-page" v-loading="loading">
    <section class="profile-hero">
      <div class="hero-glow glow-one" />
      <div class="hero-glow glow-two" />
      <div class="hero-grid" />

      <div class="hero-content">
        <div class="hero-avatar">{{ initials }}</div>
        <div class="hero-identity">
          <div class="hero-eyebrow">ACCOUNT CENTER</div>
          <h2>
            {{ profile.displayName || auth.displayName }}
            <span class="role-pill">{{ roleLabel }}</span>
          </h2>
          <div class="hero-meta">
            <span><el-icon><User /></el-icon> @{{ profile.username }}</span>
            <span><el-icon><Calendar /></el-icon> {{ joinedDate }} 加入</span>
            <span><el-icon><CircleCheck /></el-icon> 账号正常</span>
          </div>
        </div>
        <div class="hero-side">
          <div class="success-ring">
            <span class="ring-value">{{ successRate }}<small>%</small></span>
            <span class="ring-label">任务成功率</span>
          </div>
        </div>
      </div>

      <div class="hero-foot">
        <span>个人模板与校验记录均归属于当前账号</span>
        <span class="hero-id">UID {{ profile.id || '—' }}</span>
      </div>
    </section>

    <div class="stat-grid">
      <div v-for="item in statCards" :key="item.label" class="stat-card" :class="item.tone">
        <div class="stat-icon"><el-icon><component :is="item.icon" /></el-icon></div>
        <div class="stat-body">
          <div class="stat-value">{{ item.value }}</div>
          <div class="stat-label">{{ item.label }}</div>
        </div>
        <span class="stat-hint">{{ item.hint }}</span>
      </div>
    </div>

    <div class="profile-grid">
      <section class="dc-card profile-card">
        <div class="card-head">
          <div>
            <div class="card-eyebrow">PROFILE</div>
            <h3>基本资料</h3>
          </div>
          <span class="card-mark"><el-icon><EditPen /></el-icon></span>
        </div>

        <el-form ref="profileFormRef" :model="profileForm" :rules="profileRules" label-position="top">
          <el-form-item label="显示名" prop="displayName">
            <el-input v-model="profileForm.displayName" size="large" maxlength="32" placeholder="请输入显示名" />
          </el-form-item>
          <el-form-item label="邮箱" prop="email">
            <el-input v-model="profileForm.email" size="large" placeholder="选填，用于接收通知" />
          </el-form-item>
          <div class="readonly-grid">
            <div class="readonly-item">
              <span>登录账号</span>
              <b>{{ profile.username || '—' }}</b>
            </div>
            <div class="readonly-item">
              <span>账号角色</span>
              <b>{{ roleLabel }}</b>
            </div>
          </div>
          <el-button type="primary" class="save-btn" :loading="savingProfile" @click="saveProfile">
            保存资料
          </el-button>
        </el-form>
      </section>

      <section class="dc-card profile-card">
        <div class="card-head">
          <div>
            <div class="card-eyebrow">SECURITY</div>
            <h3>账号安全</h3>
          </div>
          <span class="card-mark teal"><el-icon><Lock /></el-icon></span>
        </div>

        <el-form ref="passwordFormRef" :model="passwordForm" :rules="passwordRules" label-position="top">
          <el-form-item label="原密码" prop="oldPassword">
            <el-input
              v-model="passwordForm.oldPassword"
              size="large"
              type="password"
              show-password
              placeholder="请输入当前密码"
              autocomplete="current-password"
            />
          </el-form-item>
          <el-form-item label="新密码" prop="newPassword">
            <el-input
              v-model="passwordForm.newPassword"
              size="large"
              type="password"
              show-password
              placeholder="至少 8 位，建议字母 + 数字"
              autocomplete="new-password"
            />
          </el-form-item>
          <el-form-item label="确认新密码" prop="confirmPassword">
            <el-input
              v-model="passwordForm.confirmPassword"
              size="large"
              type="password"
              show-password
              placeholder="请再次输入新密码"
              autocomplete="new-password"
            />
          </el-form-item>
          <el-button type="primary" plain class="save-btn" :loading="savingPassword" @click="savePassword">
            更新密码
          </el-button>
          <p class="security-tip">
            <el-icon><InfoFilled /></el-icon>
            修改密码后当前登录状态仍然有效，建议同时妥善保管账号信息。
          </p>
        </el-form>
      </section>
    </div>

    <section class="dc-card recent-card">
      <div class="card-head recent-head">
        <div>
          <div class="card-eyebrow">RECENT AUDITS</div>
          <h3>最近校验</h3>
        </div>
        <el-button text type="primary" @click="router.push('/tasks')">
          查看全部
          <el-icon><ArrowRight /></el-icon>
        </el-button>
      </div>

      <div v-if="profile.recentTasks?.length" class="recent-list">
        <router-link
          v-for="task in profile.recentTasks"
          :key="task.id"
          :to="`/tasks/${task.id}`"
          class="recent-item"
        >
          <div class="recent-icon">
            <el-icon><Document /></el-icon>
          </div>
          <div class="recent-main">
            <div class="recent-title">{{ task.docName }}</div>
            <div class="recent-sub">
              {{ task.templateName || '模板已删除' }} · {{ task.taskNo }}
            </div>
          </div>
          <div class="recent-diff">
            <b>{{ task.totalDiffCount || 0 }}</b>
            <span>处差异</span>
          </div>
          <div class="recent-high" :class="{ zero: !task.highCount }">
            高危 {{ task.highCount || 0 }}
          </div>
          <el-tag :type="statusMeta(task.status).type" size="small" effect="light">
            {{ statusMeta(task.status).label }}
          </el-tag>
          <span class="recent-date">{{ shortDate(task.createdAt) }}</span>
          <el-icon class="recent-arrow"><ArrowRight /></el-icon>
        </router-link>
      </div>
      <div v-else class="dc-empty recent-empty">
        <el-icon><DocumentAdd /></el-icon>
        <h3>还没有校验记录</h3>
        <p>上传一份文档，开始第一次格式校验。</p>
        <el-button type="primary" class="empty-btn" @click="router.push('/submit')">新建校验</el-button>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { changePasswordApi, profileApi, updateProfileApi } from '@/api/auth'
import { useAuthStore } from '@/stores/auth'
import { formatDateTime, statusMeta } from '@/utils/format'

const router = useRouter()
const auth = useAuthStore()
const loading = ref(false)
const savingProfile = ref(false)
const savingPassword = ref(false)
const profileFormRef = ref(null)
const passwordFormRef = ref(null)
const profile = ref({})

const profileForm = reactive({
  displayName: '',
  email: ''
})

const passwordForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

const profileRules = {
  displayName: [
    { required: true, message: '请输入显示名', trigger: 'blur' },
    { min: 2, max: 32, message: '显示名需为 2-32 个字符', trigger: 'blur' }
  ],
  email: [{ type: 'email', message: '邮箱格式不正确', trigger: ['blur', 'change'] }]
}

const passwordRules = {
  oldPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 8, max: 64, message: '新密码长度需为 8-64 位', trigger: 'blur' },
    {
      validator: (_rule, value, callback) => {
        if (value && (!/[A-Za-z]/.test(value) || !/\d/.test(value))) {
          callback(new Error('新密码需同时包含字母和数字'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      validator: (_rule, value, callback) => {
        if (value !== passwordForm.newPassword) {
          callback(new Error('两次输入的新密码不一致'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ]
}

const initials = computed(() => {
  const name = profile.value.displayName || profile.value.username || 'U'
  return name.slice(0, 1).toUpperCase()
})

const roleLabel = computed(() => (profile.value.role === 'ADMIN' ? '系统管理员' : '普通用户'))

const joinedDate = computed(() => {
  if (!profile.value.createdAt) return '—'
  return String(profile.value.createdAt).slice(0, 10)
})

const successRate = computed(() => {
  const stats = profile.value.stats
  if (!stats || !stats.taskCount) return 0
  return Math.round((stats.successTaskCount / stats.taskCount) * 100)
})

const statCards = computed(() => {
  const stats = profile.value.stats || {}
  return [
    { label: '我的模板', value: stats.templateCount || 0, hint: '公共模板不计入', icon: 'Files', tone: 'indigo' },
    { label: '校验任务', value: stats.taskCount || 0, hint: '累计提交', icon: 'DataLine', tone: 'teal' },
    { label: '累计差异', value: stats.diffCount || 0, hint: '全部任务汇总', icon: 'Aim', tone: 'amber' },
    { label: '高危问题', value: stats.highCount || 0, hint: '需优先处理', icon: 'Warning', tone: 'rose' }
  ]
})

function shortDate(value) {
  if (!value) return '—'
  return formatDateTime(value).slice(0, 10)
}

async function loadProfile() {
  loading.value = true
  try {
    const data = await profileApi()
    profile.value = data || {}
    profileForm.displayName = data?.displayName || ''
    profileForm.email = data?.email || ''
  } finally {
    loading.value = false
  }
}

async function saveProfile() {
  const valid = await profileFormRef.value?.validate().catch(() => false)
  if (!valid) return
  savingProfile.value = true
  try {
    const user = await updateProfileApi({
      displayName: profileForm.displayName.trim(),
      email: profileForm.email.trim() || null
    })
    auth.setUser(user)
    profile.value = { ...profile.value, ...user }
    ElMessage.success('个人资料已更新')
  } finally {
    savingProfile.value = false
  }
}

async function savePassword() {
  const valid = await passwordFormRef.value?.validate().catch(() => false)
  if (!valid) return
  savingPassword.value = true
  try {
    await changePasswordApi({
      oldPassword: passwordForm.oldPassword,
      newPassword: passwordForm.newPassword
    })
    passwordFormRef.value?.resetFields()
    ElMessage.success('密码已更新')
  } finally {
    savingPassword.value = false
  }
}

onMounted(loadProfile)
</script>

<style scoped>
.profile-page {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.profile-hero {
  position: relative;
  overflow: hidden;
  border-radius: 18px;
  padding: 28px 30px 18px;
  color: #e2e8f0;
  background:
    radial-gradient(700px 300px at 80% -20%, rgba(99, 102, 241, 0.55), transparent 65%),
    linear-gradient(135deg, #0d1527 0%, #17233d 58%, #111c32 100%);
  box-shadow: 0 16px 40px rgba(15, 23, 42, 0.16);
}

.hero-glow {
  position: absolute;
  border-radius: 50%;
  pointer-events: none;
  filter: blur(4px);
}

.glow-one {
  width: 340px;
  height: 340px;
  right: -100px;
  top: -170px;
  background: radial-gradient(circle, rgba(139, 92, 246, 0.3), transparent 70%);
}

.glow-two {
  width: 300px;
  height: 300px;
  left: 25%;
  bottom: -220px;
  background: radial-gradient(circle, rgba(20, 184, 166, 0.2), transparent 70%);
}

.hero-grid {
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(rgba(148, 163, 184, 0.06) 1px, transparent 1px),
    linear-gradient(90deg, rgba(148, 163, 184, 0.06) 1px, transparent 1px);
  background-size: 42px 42px;
  mask-image: linear-gradient(90deg, rgba(0, 0, 0, 0.9), transparent 75%);
  pointer-events: none;
}

.hero-content {
  position: relative;
  z-index: 2;
  display: flex;
  align-items: center;
  gap: 20px;
}

.hero-avatar {
  flex: 0 0 auto;
  width: 74px;
  height: 74px;
  border-radius: 22px;
  display: grid;
  place-items: center;
  font-size: 30px;
  font-weight: 750;
  color: #fff;
  background: linear-gradient(135deg, #6366f1, #8b5cf6 60%, #0f9f8f);
  border: 1px solid rgba(255, 255, 255, 0.22);
  box-shadow: 0 12px 28px rgba(2, 6, 23, 0.35);
}

.hero-identity { flex: 1; min-width: 0; }

.hero-eyebrow {
  font-size: 9px;
  font-weight: 750;
  letter-spacing: 0.2em;
  color: #818cf8;
  margin-bottom: 7px;
}

.hero-identity h2 {
  margin: 0;
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  font-size: 26px;
  font-weight: 740;
  letter-spacing: -0.02em;
  color: #fff;
}

.role-pill {
  padding: 4px 9px;
  border-radius: 999px;
  border: 1px solid rgba(129, 140, 248, 0.35);
  background: rgba(99, 102, 241, 0.18);
  color: #c7d2fe;
  font-size: 10px;
  font-weight: 600;
  letter-spacing: 0.02em;
}

.hero-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  margin-top: 10px;
  color: #94a3b8;
  font-size: 11.5px;
}

.hero-meta span {
  display: inline-flex;
  align-items: center;
  gap: 5px;
}

.hero-side {
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  justify-content: center;
  padding-right: 12px;
}

.success-ring {
  width: 98px;
  height: 98px;
  border-radius: 50%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  border: 2px solid rgba(45, 212, 191, 0.45);
  background: radial-gradient(circle, rgba(15, 159, 143, 0.2), transparent 70%);
}

.ring-value {
  font-size: 25px;
  font-weight: 750;
  color: #5eead4;
  line-height: 1;
}

.ring-value small { font-size: 11px; margin-left: 1px; }

.ring-label {
  margin-top: 5px;
  font-size: 9px;
  color: #8fb8b3;
  letter-spacing: 0.04em;
}

.hero-foot {
  position: relative;
  z-index: 2;
  display: flex;
  justify-content: space-between;
  gap: 16px;
  margin-top: 22px;
  padding-top: 14px;
  border-top: 1px solid rgba(148, 163, 184, 0.14);
  font-size: 10px;
  color: #64748b;
}

.hero-id { font-family: "SFMono-Regular", Consolas, monospace; letter-spacing: 0.04em; }

.stat-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}

.stat-card {
  position: relative;
  overflow: hidden;
  display: flex;
  align-items: center;
  gap: 13px;
  padding: 18px;
  border: 1px solid var(--dc-line);
  border-radius: 14px;
  background: #fff;
  box-shadow: 0 6px 20px rgba(15, 23, 42, 0.04);
}

.stat-icon {
  flex: 0 0 auto;
  width: 40px;
  height: 40px;
  border-radius: 12px;
  display: grid;
  place-items: center;
  font-size: 19px;
}

.stat-card.indigo .stat-icon { background: #eef0ff; color: #5b5bd6; }
.stat-card.teal .stat-icon { background: #e9f9f6; color: #0f9f8f; }
.stat-card.amber .stat-icon { background: #fff7e8; color: #d97706; }
.stat-card.rose .stat-icon { background: #fef0f2; color: #dc2626; }

.stat-value {
  font-size: 25px;
  font-weight: 760;
  line-height: 1;
  letter-spacing: -0.02em;
  color: var(--dc-ink);
}

.stat-label {
  margin-top: 5px;
  font-size: 11px;
  color: var(--dc-muted);
}

.stat-hint {
  position: absolute;
  right: 14px;
  top: 15px;
  font-size: 9px;
  color: #b3bfd0;
}

.profile-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.05fr) minmax(0, 0.95fr);
  gap: 18px;
}

.profile-card { padding: 22px; }

.card-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 18px;
}

.card-eyebrow {
  font-size: 9px;
  font-weight: 750;
  letter-spacing: 0.16em;
  color: #a3b0c2;
  margin-bottom: 4px;
}

.card-head h3 {
  margin: 0;
  font-size: 17px;
  font-weight: 700;
  letter-spacing: -0.01em;
}

.card-mark {
  width: 32px;
  height: 32px;
  border-radius: 9px;
  display: grid;
  place-items: center;
  background: #eef0ff;
  color: #5b5bd6;
  font-size: 15px;
}

.card-mark.teal { background: #e9f9f6; color: #0f9f8f; }

.readonly-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
  margin: 2px 0 18px;
}

.readonly-item {
  padding: 11px 12px;
  border: 1px solid #edf0f6;
  border-radius: 9px;
  background: #f8fafc;
}

.readonly-item span { display: block; font-size: 10px; color: #94a3b8; margin-bottom: 4px; }
.readonly-item b { font-size: 12.5px; color: #334155; font-weight: 600; }

.save-btn { min-width: 118px; }

.security-tip {
  display: flex;
  align-items: flex-start;
  gap: 5px;
  margin: 14px 0 0;
  font-size: 10px;
  color: #94a3b8;
  line-height: 1.6;
}

.security-tip .el-icon { margin-top: 1px; flex: 0 0 auto; }

.recent-card { padding: 22px; }

.recent-head { margin-bottom: 8px; }

.recent-list { display: flex; flex-direction: column; }

.recent-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 6px;
  border-bottom: 1px solid #f0f3f8;
  transition: background 0.16s ease;
}

.recent-item:last-child { border-bottom: none; }

.recent-item:hover { background: #fafbfe; }

.recent-icon {
  flex: 0 0 auto;
  width: 34px;
  height: 34px;
  border-radius: 9px;
  display: grid;
  place-items: center;
  background: #f1f4fa;
  color: #64748b;
  font-size: 15px;
}

.recent-main { flex: 1; min-width: 0; }

.recent-title {
  font-size: 12.5px;
  font-weight: 600;
  color: #1e293b;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.recent-sub {
  margin-top: 3px;
  font-size: 10.5px;
  color: #94a3b8;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.recent-diff {
  width: 54px;
  text-align: right;
  flex: 0 0 auto;
}

.recent-diff b { display: block; font-size: 15px; color: #334155; line-height: 1; }
.recent-diff span { font-size: 9px; color: #94a3b8; }

.recent-high {
  flex: 0 0 auto;
  padding: 3px 7px;
  border-radius: 5px;
  background: #fef2f2;
  color: #dc2626;
  font-size: 9.5px;
  font-weight: 600;
}

.recent-high.zero { background: #f0fdf9; color: #0f9f8f; }

.recent-date {
  flex: 0 0 78px;
  text-align: right;
  font-size: 10px;
  color: #94a3b8;
}

.recent-arrow { color: #cbd5e1; font-size: 13px; }

.recent-empty { padding: 36px 20px 40px; }
.recent-empty .el-icon { font-size: 30px; color: #cbd5e1; }
.recent-empty h3 { font-size: 14px; margin: 10px 0 4px; }
.recent-empty p { font-size: 11.5px; }
.empty-btn { margin-top: 14px; }

:deep(.el-form-item__label) {
  font-size: 11.5px;
  font-weight: 600;
  color: #475569;
  padding-bottom: 5px;
}

:deep(.el-input__wrapper) {
  box-shadow: 0 0 0 1px #e2e8f0 inset;
  border-radius: 8px;
  background: #fbfcfe;
}

:deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 1.5px var(--dc-primary) inset, 0 0 0 3px rgba(91, 91, 214, 0.08);
  background: #fff;
}

@media (max-width: 1080px) {
  .stat-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .profile-grid { grid-template-columns: 1fr; }
}

@media (max-width: 720px) {
  .profile-hero { padding: 22px 20px 16px; }
  .hero-avatar { width: 58px; height: 58px; border-radius: 17px; font-size: 24px; }
  .hero-identity h2 { font-size: 21px; }
  .hero-side { display: none; }
  .hero-meta { gap: 9px; font-size: 10.5px; }
  .hero-foot { flex-direction: column; gap: 5px; }
  .stat-grid { grid-template-columns: 1fr 1fr; gap: 10px; }
  .stat-card { padding: 14px; }
  .stat-hint { display: none; }
  .recent-date { display: none; }
  .recent-high { display: none; }
}
</style>
