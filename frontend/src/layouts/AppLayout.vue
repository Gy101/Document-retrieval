<template>
  <div class="app-shell">
    <aside class="sidebar">
      <router-link to="/templates" class="brand">
        <div class="brand-mark">
          <el-icon><DocumentChecked /></el-icon>
        </div>
        <div>
          <div class="brand-name">格式智检</div>
          <div class="brand-sub">DOC FORMAT AUDIT</div>
        </div>
      </router-link>

      <div class="nav-group-label">工作台</div>
      <nav class="nav">
        <router-link
          v-for="item in navItems"
          :key="item.path"
          :to="item.path"
          class="nav-item"
          :class="{ active: isActive(item) }"
        >
          <el-icon><component :is="item.icon" /></el-icon>
          <span>{{ item.label }}</span>
          <span v-if="item.badge" class="nav-badge">{{ item.badge }}</span>
        </router-link>
      </nav>

      <div class="sidebar-spacer" />

      <div class="sidebar-tip">
        <div class="tip-icon"><el-icon><MagicStick /></el-icon></div>
        <div class="tip-title">DSH 智能比对</div>
        <p>当前默认演示模式，接入真实模型只需修改 <code>dsh.mock=false</code>。</p>
      </div>

      <div class="user-card">
        <div class="user-main" @click="router.push('/profile')">
          <div class="avatar">{{ initials }}</div>
          <div class="user-meta">
            <div class="user-name">{{ auth.displayName }}</div>
            <div class="user-role">{{ auth.isAdmin ? '系统管理员' : '普通用户' }}</div>
          </div>
        </div>
        <el-dropdown trigger="click" @command="onCommand">
          <el-icon class="user-more"><MoreFilled /></el-icon>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="profile">
                <el-icon><User /></el-icon> 个人中心
              </el-dropdown-item>
              <el-dropdown-item command="logout" divided>
                <el-icon><SwitchButton /></el-icon> 退出登录
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </aside>

    <main class="main">
      <header class="topbar">
        <div class="topbar-title">
          <span class="topbar-eyebrow">{{ route.meta.eyebrow }}</span>
          <h1>{{ route.meta.title }}</h1>
        </div>
        <div class="topbar-actions">
          <el-button class="ghost-btn" @click="router.push('/tasks')">
            <el-icon><Tickets /></el-icon>
            任务记录
          </el-button>
          <el-button type="primary" class="primary-btn" @click="router.push('/submit')">
            <el-icon><Plus /></el-icon>
            新建校验
          </el-button>
        </div>
      </header>
      <router-view v-slot="{ Component }">
        <transition name="dc-fade" mode="out-in">
          <component :is="Component" />
        </transition>
      </router-view>
    </main>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const navItems = [
  { path: '/templates', label: '模板库', icon: 'Files', match: '/templates' },
  { path: '/submit', label: '新建校验', icon: 'CirclePlus', match: '/submit' },
  { path: '/tasks', label: '任务记录', icon: 'DataLine', match: '/tasks' },
  { path: '/profile', label: '个人中心', icon: 'User', match: '/profile' }
]

const initials = computed(() => {
  const name = auth.displayName || 'U'
  return name.slice(0, 1).toUpperCase()
})

function isActive(item) {
  if (item.match === '/tasks') {
    return route.path === '/tasks' || route.path.startsWith('/tasks/')
  }
  return route.path === item.match
}

async function onCommand(command) {
  if (command === 'profile') {
    router.push('/profile')
    return
  }
  if (command === 'logout') {
    await auth.logout()
    router.push('/login')
  }
}
</script>

<style scoped>
.app-shell {
  min-height: 100vh;
  display: grid;
  grid-template-columns: 246px minmax(0, 1fr);
  background:
    radial-gradient(1100px 500px at 70% -10%, rgba(99, 102, 241, 0.08), transparent 60%),
    var(--dc-bg);
}

.sidebar {
  position: sticky;
  top: 0;
  height: 100vh;
  padding: 24px 16px 18px;
  display: flex;
  flex-direction: column;
  background:
    radial-gradient(500px 300px at 20% 10%, rgba(99, 102, 241, 0.32), transparent 60%),
    linear-gradient(180deg, #111a2e 0%, #0b1222 100%);
  color: #e2e8f0;
  overflow-y: auto;
}

.brand {
  display: flex;
  align-items: center;
  gap: 11px;
  padding: 0 8px 22px;
}

.brand-mark {
  width: 40px;
  height: 40px;
  border-radius: 12px;
  display: grid;
  place-items: center;
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  box-shadow: 0 8px 20px rgba(99, 102, 241, 0.4);
  font-size: 21px;
  color: #fff;
}

.brand-name {
  font-size: 17px;
  font-weight: 750;
  letter-spacing: 0.02em;
  color: #fff;
}

.brand-sub {
  margin-top: 2px;
  font-size: 8.5px;
  letter-spacing: 0.19em;
  color: #8fa0bd;
}

.nav-group-label {
  padding: 0 12px 9px;
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.16em;
  color: #64748b;
}

.nav {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 11px;
  padding: 11px 12px;
  border-radius: 10px;
  color: #a9b6cc;
  font-size: 13.5px;
  transition: 0.18s ease;
  border: 1px solid transparent;
}

.nav-item:hover {
  color: #fff;
  background: rgba(148, 163, 184, 0.1);
}

.nav-item.active {
  color: #fff;
  background: linear-gradient(90deg, rgba(99, 102, 241, 0.28), rgba(99, 102, 241, 0.08));
  border-color: rgba(129, 140, 248, 0.28);
}

.nav-item .el-icon { font-size: 17px; }

.nav-badge {
  margin-left: auto;
  min-width: 20px;
  height: 18px;
  padding: 0 6px;
  border-radius: 999px;
  background: rgba(148, 163, 184, 0.2);
  color: #cbd5e1;
  font-size: 10px;
  display: grid;
  place-items: center;
}

.sidebar-spacer { flex: 1; min-height: 30px; }

.sidebar-tip {
  margin: 0 4px 16px;
  padding: 14px;
  border-radius: 12px;
  background: rgba(99, 102, 241, 0.12);
  border: 1px solid rgba(129, 140, 248, 0.2);
}

.tip-icon {
  width: 26px;
  height: 26px;
  border-radius: 8px;
  display: grid;
  place-items: center;
  background: rgba(99, 102, 241, 0.28);
  color: #c7d2fe;
  margin-bottom: 9px;
}

.tip-title {
  font-size: 12px;
  font-weight: 650;
  color: #e0e7ff;
}

.sidebar-tip p {
  margin: 5px 0 0;
  font-size: 11px;
  line-height: 1.65;
  color: #94a3b8;
}

.sidebar-tip code {
  color: #c7d2fe;
  font-size: 10px;
}

.user-card {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 8px 6px;
  border-top: 1px solid rgba(148, 163, 184, 0.14);
}

.user-main {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 10px;
  cursor: pointer;
}

.avatar {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  background: linear-gradient(135deg, #0f9f8f, #14b8a6);
  color: #fff;
  font-size: 14px;
  font-weight: 700;
  flex-shrink: 0;
}

.user-meta { flex: 1; min-width: 0; }

.user-name {
  font-size: 12.5px;
  color: #f1f5f9;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user-role {
  font-size: 10.5px;
  color: #718096;
  margin-top: 2px;
}

.user-more {
  color: #64748b;
  cursor: pointer;
  font-size: 16px;
}

.main { min-width: 0; }

.topbar {
  height: 78px;
  padding: 0 30px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  background: rgba(255, 255, 255, 0.78);
  backdrop-filter: blur(14px);
  border-bottom: 1px solid rgba(226, 232, 240, 0.9);
  position: sticky;
  top: 0;
  z-index: 20;
}

.topbar-eyebrow {
  display: block;
  font-size: 9.5px;
  letter-spacing: 0.18em;
  color: #94a3b8;
  font-weight: 700;
  text-transform: uppercase;
  margin-bottom: 2px;
}

.topbar-title h1 {
  margin: 0;
  font-size: 19px;
  font-weight: 700;
  letter-spacing: -0.01em;
}

.topbar-actions { display: flex; gap: 10px; }

.ghost-btn {
  border-color: #e2e8f0;
  color: #475569;
  background: #fff;
}

.primary-btn {
  background: linear-gradient(135deg, #5b5bd6, #6d6de8);
  border: none;
  box-shadow: 0 6px 16px rgba(91, 91, 214, 0.24);
}

@media (max-width: 860px) {
  .app-shell { grid-template-columns: 1fr; }
  .sidebar {
    position: fixed;
    z-index: 50;
    left: 0;
    top: 0;
    width: 246px;
    transform: translateX(-100%);
    transition: transform 0.2s ease;
  }
  .sidebar:hover { transform: translateX(0); }
  .topbar { padding: 0 16px; height: 70px; }
  .ghost-btn { display: none; }
}
</style>
