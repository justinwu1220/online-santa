import { Route, Routes } from 'react-router-dom'
import { Layout } from './components/Layout'
import { AdminOrganizations } from './routes/admin/AdminOrganizations'
import { ClaimDetail } from './routes/ClaimDetail'
import { MyClaims } from './routes/MyClaims'
import { NotFound } from './routes/NotFound'
import { OrgClaims } from './routes/org/OrgClaims'
import { OrgConsole } from './routes/org/OrgConsole'
import { OrgSettings } from './routes/org/OrgSettings'
import { OrgWishes } from './routes/org/OrgWishes'
import { WishDetail } from './routes/WishDetail'
import { WishWall } from './routes/WishWall'

export default function App() {
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route index element={<WishWall />} />
        <Route path="wishes/:id" element={<WishDetail />} />

        <Route path="me/claims" element={<MyClaims />} />
        <Route path="me/claims/:id" element={<ClaimDetail />} />

        <Route path="org" element={<OrgConsole />}>
          <Route index element={<OrgWishes />} />
          <Route path="claims" element={<OrgClaims />} />
          <Route path="overdue" element={<OrgClaims overdueOnly />} />
          <Route path="settings" element={<OrgSettings />} />
        </Route>

        <Route path="admin/organizations" element={<AdminOrganizations />} />

        <Route path="*" element={<NotFound />} />
      </Route>
    </Routes>
  )
}
