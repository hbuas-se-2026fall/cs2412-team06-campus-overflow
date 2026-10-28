import { useEffect, useState } from 'react'
import type { CSSProperties } from 'react'
import { Layout } from 'antd'
import { Link, Outlet, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth'
import { api } from '../api'

const { Header, Content } = Layout

const navLinkStyle: CSSProperties = { color: 'rgba(255,255,255,0.85)' }

/**
 * 全局应用外壳（antd 布局）：顶部导航（品牌 / 问题 / 排行榜 / 通知 / 用户区）+ 内容区（Outlet）。
 * 路由在 App.tsx 中以嵌套形式接入。
 */
export function AppShell() {
  const { me, logout } = useAuth()
  const navigate = useNavigate()
  const [unread, setUnread] = useState(0)

  useEffect(() => {
    if (!me) return
    const load = () => api.unreadCount().then((r) => setUnread(r.count)).catch(() => undefined)
    load()
    const timer = setInterval(load, 30_000)
    return () => clearInterval(timer)
  }, [me])

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Header style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
        <Link to="/" style={{ color: 'white', fontSize: 18, fontWeight: 'bold' }}>CampusOverflow</Link>
        <nav style={{ display: 'flex', gap: 16 }}>
          <Link to="/" style={navLinkStyle}>问题</Link>
          <Link to="/leaderboard" style={navLinkStyle}>排行榜</Link>
          {me && <Link to="/notifications" style={navLinkStyle}>通知{unread > 0 && <span className="badge">{unread}</span>}</Link>}
        </nav>
        <div style={{ flex: 1 }} />
        {me ? (
          <div style={{ color: 'rgba(255,255,255,0.85)', display: 'flex', gap: 12, alignItems: 'center' }}>
            <span>{me.displayName}</span>
            <span style={{ fontSize: 12 }}>{me.role}</span>
            <button onClick={() => logout().then(() => navigate('/'))}>退出</button>
          </div>
        ) : (
          <Link className="primary" to="/login" style={{ color: 'white' }}>登录 / 注册</Link>
        )}
      </Header>
      <Content style={{ padding: 24 }}>
        <Outlet />
      </Content>
    </Layout>
  )
}
