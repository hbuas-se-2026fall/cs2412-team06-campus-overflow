import { Layout, Menu } from 'antd'
import { Outlet, useNavigate, useLocation } from 'react-router-dom'
import { Home, PlusCircleOutlined } from '@ant-design/icons'

const { Header, Sider, Content } = Layout

export function AppShell() {
  const navigate = useNavigate()
  const location = useLocation()

  const menuItems = [
    { key: '/questions', icon: <Home />, label: '问题列表' },
    { key: '/questions/new', icon: <PlusCircleOutlined />, label: '提问' },
  ]

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Header style={{ display: 'flex', alignItems: 'center', color: 'white', fontSize: 18, fontWeight: 'bold' }}>
        CampusOverflow
      </Header>
      <Layout>
        <Sider width={200} theme="light">
          <Menu
            mode="inline"
            selectedKeys={[location.pathname]}
            items={menuItems}
            onClick={({ key }) => navigate(key)}
          />
        </Sider>
        <Content style={{ padding: 24, background: '#f5f5f5' }}>
          <Outlet />
        </Content>
      </Layout>
    </Layout>
  )
}
