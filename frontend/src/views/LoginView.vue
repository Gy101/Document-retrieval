<template>
  <div class="login-page">
    <section class="login-visual">
      <div class="visual-glow glow-a"></div>
      <div class="visual-glow glow-b"></div>
      <div class="grid-texture"></div>

      <div class="visual-top">
        <div class="brand">
          <div class="brand-mark"><el-icon><DocumentChecked /></el-icon></div>
          <div>
            <div class="brand-name">格式智检</div>
            <div class="brand-en">DOC FORMAT AUDIT</div>
          </div>
        </div>
        <span class="powered"><i></i> DSH POWERED</span>
      </div>

      <div class="visual-copy">
        <div class="visual-kicker">INTELLIGENT DOCUMENT COMPLIANCE</div>
        <h1>让每一份文档，<br />都符合格式规范。</h1>
        <p>
          以模板为基准，对字符、段落、编号、表格、图片、页面与页眉页脚
          进行全维度智能比对，把格式问题定位到每一个段落。
        </p>
        <div class="feature-row">
          <div class="feature"><b>7</b><span>校验维度</span></div>
          <div class="feature"><b>字符级</b><span>差异定位</span></div>
          <div class="feature"><b>异步</b><span>任务队列</span></div>
        </div>
      </div>

      <div class="doc-stage">
        <div class="doc-sheet">
          <div class="sheet-head">
            <div class="sheet-seal">DOC</div>
            <div class="sheet-lines">
              <span style="width: 46%"></span>
              <span style="width: 68%"></span>
            </div>
          </div>
          <div class="sheet-title"></div>
          <div class="sheet-line"></div>
          <div class="sheet-line short"></div>
          <div class="sheet-line highlight">
            <span class="hl-text"></span>
            <span class="hl-tag">字体不一致</span>
          </div>
          <div class="sheet-line"></div>
          <div class="sheet-line short"></div>
          <div class="sheet-table">
            <span></span><span></span><span></span>
          </div>
        </div>
        <div class="float-card card-a">
          <div class="float-icon"><el-icon><Warning /></el-icon></div>
          <div>
            <b>行距偏差</b>
            <small>应为 1.5 倍行距</small>
          </div>
        </div>
        <div class="float-card card-b">
          <div class="float-icon teal"><el-icon><CircleCheck /></el-icon></div>
          <div>
            <b>页面设置</b>
            <small>A4 · 纵向 · 页边距合规</small>
          </div>
        </div>
      </div>

      <div class="visual-foot">
        <span>Apache POI 深度解析</span>
        <span>·</span>
        <span>结构化格式元数据</span>
        <span>·</span>
        <span>可追溯差异报告</span>
      </div>
    </section>

    <section class="login-panel">
      <div class="login-box">
        <div class="mobile-brand">
          <div class="brand-mark"><el-icon><DocumentChecked /></el-icon></div>
          <span>格式智检</span>
        </div>

        <div class="mode-switch" role="tablist" aria-label="登录或注册">
          <button
            type="button"
            class="mode-btn"
            :class="{ active: !isRegister }"
            role="tab"
            :aria-selected="!isRegister"
            @click="switchMode('login')"
          >
            登录
          </button>
          <button
            type="button"
            class="mode-btn"
            :class="{ active: isRegister }"
            role="tab"
            :aria-selected="isRegister"
            @click="switchMode('register')"
          >
            注册
          </button>
          <span class="mode-glider" :class="{ right: isRegister }" />
        </div>

        <div class="login-head">
          <div class="login-eyebrow">{{ isRegister ? 'CREATE YOUR ACCOUNT' : 'WELCOME BACK' }}</div>
          <h2>{{ isRegister ? '创建你的账号' : '登录工作台' }}</h2>
          <p v-if="isRegister">注册后即可管理个人模板、发起校验并沉淀任务记录。</p>
          <p v-else>选择模板、上传文档，开始一次格式校验。</p>
        </div>

        <el-form
          v-if="!isRegister"
          ref="loginFormRef"
          :model="loginForm"
          :rules="loginRules"
          label-position="top"
          class="auth-form"
          @submit.prevent="onLogin"
        >
          <el-form-item label="账号" prop="username">
            <el-input
              v-model="loginForm.username"
              size="large"
              placeholder="请输入用户名"
              :prefix-icon="User"
              autocomplete="username"
              @keyup.enter="onLogin"
            />
          </el-form-item>
          <el-form-item label="密码" prop="password">
            <el-input
              v-model="loginForm.password"
              size="large"
              type="password"
              show-password
              placeholder="请输入密码"
              :prefix-icon="Lock"
              autocomplete="current-password"
              @keyup.enter="onLogin"
            />
          </el-form-item>

          <div class="form-aux">
            <el-checkbox v-model="remember">记住账号</el-checkbox>
            <span class="help-text">忘记密码请联系管理员</span>
          </div>

          <el-button class="login-btn" type="primary" size="large" :loading="loading" native-type="submit">
            {{ loading ? '正在验证…' : '进入工作台' }}
            <el-icon v-if="!loading" class="btn-arrow"><Right /></el-icon>
          </el-button>
        </el-form>

        <el-form
          v-else
          ref="registerFormRef"
          :model="registerForm"
          :rules="registerRules"
          label-position="top"
          class="auth-form register-form"
          @submit.prevent="onRegister"
        >
          <div class="form-row">
            <el-form-item label="显示名" prop="displayName">
              <el-input
                v-model="registerForm.displayName"
                size="large"
                placeholder="怎么称呼你"
                :prefix-icon="UserFilled"
                maxlength="32"
                autocomplete="name"
              />
            </el-form-item>
            <el-form-item label="用户名" prop="username">
              <el-input
                v-model="registerForm.username"
                size="large"
                placeholder="4-32 位字母/数字"
                :prefix-icon="User"
                maxlength="32"
                autocomplete="username"
              />
            </el-form-item>
          </div>

          <el-form-item label="邮箱（选填）" prop="email">
            <el-input
              v-model="registerForm.email"
              size="large"
              placeholder="用于接收校验通知"
              :prefix-icon="Message"
              autocomplete="email"
            />
          </el-form-item>

          <el-form-item label="密码" prop="password">
            <el-input
              v-model="registerForm.password"
              size="large"
              type="password"
              show-password
              placeholder="至少 8 位，建议字母 + 数字"
              :prefix-icon="Lock"
              autocomplete="new-password"
            />
          </el-form-item>

          <div v-if="registerForm.password" class="strength-row">
            <div class="strength-bars">
              <span
                v-for="index in 4"
                :key="index"
                class="strength-bar"
                :class="{ on: strengthScore >= index, weak: strengthScore <= 1, strong: strengthScore >= 3 }"
              />
            </div>
            <span class="strength-label" :class="{ strong: strengthScore >= 3 }">
              密码强度：{{ strengthLabel }}
            </span>
          </div>

          <el-form-item label="确认密码" prop="confirmPassword">
            <el-input
              v-model="registerForm.confirmPassword"
              size="large"
              type="password"
              show-password
              placeholder="请再次输入密码"
              :prefix-icon="Lock"
              autocomplete="new-password"
              @keyup.enter="onRegister"
            />
          </el-form-item>

          <el-checkbox v-model="agreement" class="agree-row">
            我已阅读并同意
            <span class="agree-link">服务条款</span>
            与
            <span class="agree-link">隐私说明</span>
          </el-checkbox>

          <el-button class="login-btn register-btn" type="primary" size="large" :loading="loading" native-type="submit">
            {{ loading ? '正在创建…' : '创建账号并登录' }}
            <el-icon v-if="!loading" class="btn-arrow"><Right /></el-icon>
          </el-button>
        </el-form>

        <div v-if="!isRegister" class="demo-box">
          <div class="demo-head">
            <span>演示环境</span>
            <button type="button" @click="fillDemo">一键填入</button>
          </div>
          <div class="demo-cred">
            <span>账号 <code>admin</code></span>
            <span>密码 <code>admin123</code></span>
          </div>
        </div>

        <div class="auth-switch">
          <template v-if="isRegister">
            已有账号？
            <button type="button" @click="switchMode('login')">直接登录</button>
          </template>
          <template v-else>
            还没有账号？
            <button type="button" @click="switchMode('register')">免费注册</button>
          </template>
        </div>

        <div class="login-foot">
          <span>© {{ year }} 格式智检</span>
          <span class="dot">·</span>
          <span>通用文档格式智能校验系统</span>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Lock, Message, User, UserFilled } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const loginFormRef = ref(null)
const registerFormRef = ref(null)
const loading = ref(false)
const remember = ref(Boolean(localStorage.getItem('dc_remember')))
const agreement = ref(false)
const year = new Date().getFullYear()

const isRegister = computed(() => route.name === 'register')

const loginForm = reactive({
  username: localStorage.getItem('dc_remember') || '',
  password: ''
})

const registerForm = reactive({
  displayName: '',
  username: '',
  email: '',
  password: '',
  confirmPassword: ''
})

const loginRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

const registerRules = {
  displayName: [
    { required: true, message: '请输入显示名', trigger: 'blur' },
    { min: 2, max: 32, message: '显示名需为 2-32 个字符', trigger: 'blur' }
  ],
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    {
      pattern: /^[a-zA-Z0-9_]{4,32}$/,
      message: '用户名需为 4-32 位字母、数字或下划线',
      trigger: 'blur'
    }
  ],
  email: [{ type: 'email', message: '邮箱格式不正确', trigger: ['blur', 'change'] }],
  password: [
    { required: true, message: '请设置密码', trigger: 'blur' },
    { min: 8, max: 64, message: '密码长度需为 8-64 位', trigger: 'blur' },
    {
      validator: (_rule, value, callback) => {
        if (value && (!/[A-Za-z]/.test(value) || !/\d/.test(value))) {
          callback(new Error('密码需同时包含字母和数字'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ],
  confirmPassword: [
    { required: true, message: '请再次输入密码', trigger: 'blur' },
    {
      validator: (_rule, value, callback) => {
        if (value !== registerForm.password) {
          callback(new Error('两次输入的密码不一致'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ]
}

const strengthScore = computed(() => {
  const value = registerForm.password
  if (!value) return 0
  let score = 0
  if (value.length >= 8) score += 1
  if (/[a-z]/.test(value) && /[A-Z]/.test(value)) score += 1
  if (/\d/.test(value)) score += 1
  if (/[^A-Za-z0-9]/.test(value)) score += 1
  return score
})

const strengthLabel = computed(() => ['未设置', '较弱', '一般', '较强', '很强'][strengthScore.value])

watch(isRegister, () => {
  loginFormRef.value?.clearValidate()
  registerFormRef.value?.clearValidate()
})

function switchMode(mode) {
  if (route.name === mode) return
  const query = typeof route.query.redirect === 'string' ? { redirect: route.query.redirect } : {}
  router.push({ name: mode, query })
}

function fillDemo() {
  loginForm.username = 'admin'
  loginForm.password = 'admin123'
}

async function onLogin() {
  if (loading.value) return
  const valid = await loginFormRef.value?.validate().catch(() => false)
  if (!valid) return
  loading.value = true
  try {
    await auth.login({ username: loginForm.username.trim(), password: loginForm.password })
    if (remember.value) {
      localStorage.setItem('dc_remember', loginForm.username.trim())
    } else {
      localStorage.removeItem('dc_remember')
    }
    ElMessage.success('登录成功，欢迎回来')
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/templates'
    router.replace(redirect)
  } catch (e) {
    // 错误提示由请求拦截器统一处理
  } finally {
    loading.value = false
  }
}

async function onRegister() {
  if (loading.value) return
  const valid = await registerFormRef.value?.validate().catch(() => false)
  if (!valid) return
  if (!agreement.value) {
    ElMessage.warning('请先阅读并同意服务条款与隐私说明')
    return
  }
  loading.value = true
  try {
    await auth.register({
      displayName: registerForm.displayName.trim(),
      username: registerForm.username.trim(),
      email: registerForm.email.trim() || null,
      password: registerForm.password
    })
    ElMessage.success('注册成功，已为你自动登录')
    router.replace('/templates')
  } catch (e) {
    // 错误提示由请求拦截器统一处理
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  display: grid;
  grid-template-columns: minmax(0, 1.08fr) minmax(430px, 0.92fr);
  background: #fff;
}

.login-visual {
  position: relative;
  overflow: hidden;
  padding: 34px 48px 26px;
  display: flex;
  flex-direction: column;
  color: #e2e8f0;
  background:
    radial-gradient(900px 600px at 15% 10%, rgba(79, 70, 229, 0.32), transparent 60%),
    linear-gradient(150deg, #0b1222 0%, #101a32 52%, #0b1324 100%);
}

.visual-glow {
  position: absolute;
  border-radius: 50%;
  filter: blur(2px);
  pointer-events: none;
}
.glow-a {
  width: 520px;
  height: 520px;
  right: -180px;
  top: -160px;
  background: radial-gradient(circle, rgba(99, 102, 241, 0.26), transparent 68%);
}
.glow-b {
  width: 460px;
  height: 460px;
  left: -160px;
  bottom: -180px;
  background: radial-gradient(circle, rgba(20, 184, 166, 0.14), transparent 68%);
}

.grid-texture {
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(rgba(148, 163, 184, 0.055) 1px, transparent 1px),
    linear-gradient(90deg, rgba(148, 163, 184, 0.055) 1px, transparent 1px);
  background-size: 46px 46px;
  mask-image: linear-gradient(180deg, rgba(0, 0, 0, 0.8), transparent 88%);
  pointer-events: none;
}

.visual-top {
  position: relative;
  z-index: 2;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.brand { display: flex; align-items: center; gap: 11px; }

.brand-mark {
  width: 42px;
  height: 42px;
  border-radius: 12px;
  display: grid;
  place-items: center;
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  color: #fff;
  font-size: 22px;
  box-shadow: 0 10px 24px rgba(99, 102, 241, 0.38);
}

.brand-name { font-size: 18px; font-weight: 750; color: #fff; letter-spacing: 0.02em; }
.brand-en { margin-top: 2px; font-size: 8.5px; letter-spacing: 0.22em; color: #8394b0; }

.powered {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 10px;
  border-radius: 999px;
  border: 1px solid rgba(148, 163, 184, 0.22);
  background: rgba(148, 163, 184, 0.08);
  font-size: 9px;
  letter-spacing: 0.12em;
  color: #a5b4cc;
}
.powered i { width: 5px; height: 5px; border-radius: 50%; background: #34d399; box-shadow: 0 0 8px #34d399; }

.visual-copy {
  position: relative;
  z-index: 2;
  margin-top: clamp(40px, 7vh, 82px);
  max-width: 560px;
}

.visual-kicker {
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.2em;
  color: #818cf8;
  margin-bottom: 14px;
}

.visual-copy h1 {
  margin: 0;
  font-size: clamp(34px, 4.1vw, 52px);
  line-height: 1.22;
  letter-spacing: -0.035em;
  font-weight: 760;
  color: #fff;
}

.visual-copy p {
  margin: 20px 0 0;
  max-width: 475px;
  color: #9aabc4;
  font-size: 13.5px;
  line-height: 1.9;
}

.feature-row {
  display: flex;
  gap: 34px;
  margin-top: 28px;
}

.feature { display: flex; flex-direction: column; gap: 4px; }
.feature b { font-size: 20px; color: #f8fafc; letter-spacing: -0.02em; }
.feature span { font-size: 10.5px; color: #7f91ad; letter-spacing: 0.06em; }

.doc-stage {
  position: relative;
  z-index: 2;
  margin: 34px auto 0;
  width: min(420px, 76%);
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
}

.doc-sheet {
  width: 300px;
  height: 360px;
  background: #fff;
  border-radius: 4px;
  padding: 30px 28px;
  box-shadow: 0 30px 70px rgba(2, 6, 23, 0.55), 0 0 0 1px rgba(255, 255, 255, 0.08);
  transform: rotate(-2.2deg);
  position: relative;
}

.sheet-head { display: flex; gap: 10px; align-items: center; margin-bottom: 26px; }
.sheet-seal {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  border: 2px solid #ef4444;
  color: #ef4444;
  display: grid;
  place-items: center;
  font-size: 8px;
  font-weight: 800;
  letter-spacing: 0.05em;
  transform: rotate(-8deg);
  flex: 0 0 auto;
}
.sheet-lines { flex: 1; display: flex; flex-direction: column; gap: 5px; }
.sheet-lines span { display: block; height: 5px; border-radius: 2px; background: #e2e8f0; }

.sheet-title { height: 12px; width: 56%; margin: 0 auto 22px; border-radius: 3px; background: #1e293b; }
.sheet-line { height: 5px; border-radius: 2px; background: #e8edf5; margin-bottom: 9px; }
.sheet-line.short { width: 72%; }
.sheet-line.highlight { position: relative; margin: 15px 0; background: #fef3c7; height: 22px; border-radius: 3px; padding: 8px 7px; }
.hl-text { display: block; width: 62%; height: 5px; background: #f59e0b; border-radius: 2px; }
.hl-tag {
  position: absolute;
  right: -46px;
  top: 2px;
  padding: 3px 6px;
  border-radius: 4px;
  background: #dc2626;
  color: #fff;
  font-size: 7px;
  font-weight: 600;
  white-space: nowrap;
  box-shadow: 0 3px 9px rgba(220, 38, 38, 0.3);
}
.sheet-table { margin-top: 20px; border: 1px solid #e2e8f0; border-radius: 2px; overflow: hidden; }
.sheet-table span { display: block; height: 18px; border-bottom: 1px solid #eef2f7; background: #f8fafc; }
.sheet-table span:last-child { border-bottom: none; }

.float-card {
  position: absolute;
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 10px 12px;
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.97);
  box-shadow: 0 14px 34px rgba(2, 6, 23, 0.35);
  animation: float 4.4s ease-in-out infinite;
}
.float-card b { display: block; font-size: 10.5px; color: #1e293b; }
.float-card small { display: block; margin-top: 2px; font-size: 9px; color: #94a3b8; }
.float-icon {
  width: 27px;
  height: 27px;
  border-radius: 8px;
  display: grid;
  place-items: center;
  background: #fffbeb;
  color: #d97706;
  font-size: 14px;
}
.float-icon.teal { background: #ecfdf5; color: #0f9f8f; }
.card-a { right: -22px; top: 66px; }
.card-b { left: -38px; bottom: 58px; animation-delay: -2.1s; }

@keyframes float {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-7px); }
}

.visual-foot {
  position: relative;
  z-index: 2;
  display: flex;
  justify-content: center;
  gap: 9px;
  padding-top: 14px;
  font-size: 9px;
  letter-spacing: 0.05em;
  color: #5e718e;
}

.login-panel {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px 48px;
  background: #fff;
}

.login-box { width: 100%; max-width: 372px; }

.mobile-brand { display: none; align-items: center; gap: 9px; font-weight: 700; margin-bottom: 30px; }
.mobile-brand .brand-mark { width: 34px; height: 34px; font-size: 18px; border-radius: 10px; }

.login-eyebrow {
  font-size: 10px;
  font-weight: 750;
  letter-spacing: 0.18em;
  color: var(--dc-primary);
  margin-bottom: 9px;
}

.login-head h2 {
  margin: 0;
  font-size: 28px;
  letter-spacing: -0.03em;
  font-weight: 760;
  color: #0f172a;
}

.login-head p { margin: 9px 0 30px; color: #8492a6; font-size: 12.5px; }

:deep(.el-form-item__label) {
  font-size: 12px;
  font-weight: 600;
  color: #475569;
  padding-bottom: 6px;
}

:deep(.el-input__wrapper) {
  box-shadow: 0 0 0 1px #e2e8f0 inset;
  border-radius: 9px;
  padding: 2px 12px;
  background: #fbfcfe;
}
:deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 1.5px var(--dc-primary) inset, 0 0 0 3px rgba(91, 91, 214, 0.08);
  background: #fff;
}
:deep(.el-input__inner) { font-size: 13px; }

.form-aux {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 2px 0 20px;
}
:deep(.el-checkbox__label) { font-size: 11.5px; color: #64748b; }
.help-text { font-size: 10.5px; color: #a0aec0; }

.login-btn {
  width: 100%;
  height: 43px;
  border: none;
  border-radius: 9px;
  font-size: 13.5px;
  font-weight: 600;
  letter-spacing: 0.02em;
  background: linear-gradient(135deg, #5b5bd6, #6d5ce8);
  box-shadow: 0 9px 20px rgba(91, 91, 214, 0.24);
}
.login-btn:hover { background: linear-gradient(135deg, #5050c9, #624fdd); }
.btn-arrow { margin-left: 5px; }

.demo-box {
  margin-top: 26px;
  padding: 13px 14px;
  border: 1px dashed #dde3ee;
  border-radius: 9px;
  background: #fafbfe;
}
.demo-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 10.5px;
  color: #8593a8;
}
.demo-head button {
  border: none;
  background: transparent;
  color: var(--dc-primary);
  font-size: 10.5px;
  cursor: pointer;
  font-weight: 600;
  padding: 0;
}
.demo-head button:hover { text-decoration: underline; }
.demo-cred { display: flex; gap: 18px; margin-top: 7px; font-size: 10.5px; color: #9aa7ba; }
.demo-cred code {
  color: #475569;
  background: #eef2f7;
  padding: 1px 4px;
  border-radius: 3px;
  font-size: 10px;
}

.login-foot {
  margin-top: 30px;
  display: flex;
  justify-content: center;
  gap: 6px;
  font-size: 9.5px;
  color: #b6c0ce;
}
.login-foot .dot { color: #d5dce7; }

/* ===== 登录 / 注册切换与注册表单 ===== */
.login-box { max-width: 404px; }

.login-panel {
  overflow-y: auto;
}

.mode-switch {
  position: relative;
  display: flex;
  width: 100%;
  height: 40px;
  padding: 3px;
  margin-bottom: 26px;
  border-radius: 10px;
  background: #f1f4fa;
  border: 1px solid #e7ecf5;
}

.mode-btn {
  position: relative;
  z-index: 2;
  flex: 1;
  border: none;
  background: transparent;
  border-radius: 8px;
  font-size: 13px;
  font-weight: 650;
  color: #7b8aa3;
  cursor: pointer;
  transition: color 0.2s ease;
}

.mode-btn.active { color: var(--dc-primary); }

.mode-glider {
  position: absolute;
  z-index: 1;
  top: 3px;
  left: 3px;
  width: calc(50% - 3px);
  height: 32px;
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 3px 10px rgba(15, 23, 42, 0.08);
  transition: transform 0.24s cubic-bezier(0.4, 0, 0.2, 1);
}

.mode-glider.right { transform: translateX(100%); }

.auth-form .el-form-item { margin-bottom: 16px; }

.register-form .el-form-item { margin-bottom: 13px; }

.form-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.form-row .el-form-item { min-width: 0; }

.strength-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin: -5px 0 12px;
}

.strength-bars {
  display: flex;
  gap: 4px;
  flex: 1;
}

.strength-bar {
  height: 4px;
  flex: 1;
  border-radius: 999px;
  background: #e8edf5;
  transition: background 0.2s ease;
}

.strength-bar.on { background: #f59e0b; }
.strength-bar.on.strong { background: #10b981; }

.strength-label {
  flex: 0 0 auto;
  font-size: 10.5px;
  color: #d97706;
  white-space: nowrap;
}

.strength-label.strong { color: #0f9f8f; }

.agree-row {
  margin: -3px 0 14px;
  height: auto;
  align-items: flex-start;
}

.agree-row :deep(.el-checkbox__label) {
  white-space: normal;
  line-height: 1.65;
  font-size: 11px;
}

.agree-link {
  color: var(--dc-primary);
  cursor: pointer;
}

.agree-link:hover { text-decoration: underline; }

.register-btn { margin-top: 2px; }

.auth-switch {
  margin-top: 20px;
  text-align: center;
  font-size: 11.5px;
  color: #94a3b8;
}

.auth-switch button {
  border: none;
  background: transparent;
  padding: 0 2px;
  color: var(--dc-primary);
  font-size: 11.5px;
  font-weight: 650;
  cursor: pointer;
}

.auth-switch button:hover { text-decoration: underline; }

.login-foot { margin-top: 24px; }

@media (max-width: 980px) {
  .login-page { grid-template-columns: 1fr; }
  .login-visual { display: none; }
  .login-panel { min-height: 100vh; background: linear-gradient(180deg, #f7f8fd, #fff); }
  .mobile-brand { display: flex; }
  .login-box {
    background: #fff;
    border: 1px solid #e8ecf4;
    border-radius: 18px;
    padding: 30px 26px;
    box-shadow: 0 20px 50px rgba(15, 23, 42, 0.07);
  }
}

@media (max-width: 480px) {
  .login-panel { padding: 20px 16px; }
  .login-head h2 { font-size: 24px; }
  .form-row { grid-template-columns: 1fr; gap: 0; }
  .form-row .el-form-item { margin-bottom: 13px; }
  .login-head p { margin-bottom: 22px; }
}

</style>
