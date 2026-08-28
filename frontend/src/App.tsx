import { Route, Routes } from 'react-router-dom'
import { PublicLayout } from './components/layouts/PublicLayout'
import { RequireRole } from './components/RequireRole'
import { AdminClaimDetail } from './routes/admin/AdminClaimDetail'
import { AdminClaims, AdminWishes } from './routes/admin/AdminCatalog'
import { AdminDashboard } from './routes/admin/AdminDashboard'
import { AdminLayout } from './routes/admin/AdminLayout'
import { AdminLogin } from './routes/admin/AdminLogin'
import { AdminOrganizations } from './routes/admin/AdminOrganizations'
import { AdminSystem } from './routes/admin/AdminSystem'
import { ClaimDetail } from './routes/ClaimDetail'
import { LoginPage } from './routes/LoginPage'
import { MyClaims } from './routes/MyClaims'
import { NotFound } from './routes/NotFound'
import { OrgClaims } from './routes/org/OrgClaims'
import { OrgDashboard } from './routes/org/OrgDashboard'
import { OrgLayout } from './routes/org/OrgLayout'
import { OrgLogin } from './routes/org/OrgLogin'
import { OrgRegister } from './routes/org/OrgRegister'
import { OrgSettings } from './routes/org/OrgSettings'
import { OrgWishes } from './routes/org/OrgWishes'
import { WishDetail } from './routes/WishDetail'
import { WishWall } from './routes/WishWall'

/**
 * 三個在使用者感受上獨立的網站，共用同一套程式碼與身分系統。
 *
 * - `/`      主網站：願望牆公開，認領與紀錄需登入
 * - `/org`   機構後台：從主網站頁尾的入口進入，導覽列沒有願望牆
 * - `/admin` 監控中心：任何地方都沒有連結，只能直接輸入網址
 *
 * 登入頁刻意放在各自的 layout 之外——它們有自己的全螢幕版面，而且必須在
 * 通過角色檢查之前就能顯示。
 */
export default function App() {
  return (
    <Routes>
      {/* 登入頁在 layout 之外：全螢幕版面，而且不該有導覽列 */}
      <Route path="login" element={<LoginPage />} />

      {/* ---------------------------------------------------------- 主網站 */}
      <Route element={<PublicLayout />}>
        <Route index element={<WishWall />} />
        <Route path="wishes/:id" element={<WishDetail />} />

        <Route path="me/claims" element={
          <RequireRole role="DONOR" loginPath="/login"><MyClaims /></RequireRole>} />
        <Route path="me/claims/:id" element={
          <RequireRole role="DONOR" loginPath="/login"><ClaimDetail /></RequireRole>} />
      </Route>

      {/* ---------------------------------------------------------- 機構後台 */}
      <Route path="org/login" element={<OrgLogin />} />
      <Route path="org/register" element={
        <RequireRole role="DONOR" loginPath="/org/login"><OrgRegister /></RequireRole>} />

      <Route path="org" element={
        <RequireRole role="ORG_MEMBER" loginPath="/org/login"><OrgLayout /></RequireRole>}>
        <Route index element={<OrgDashboard />} />
        <Route path="wishes" element={<OrgWishes />} />
        <Route path="claims" element={<OrgClaims />} />
        <Route path="overdue" element={<OrgClaims overdueOnly />} />
        <Route path="settings" element={<OrgSettings />} />
      </Route>

      {/* ---------------------------------------------------------- 監控中心 */}
      <Route path="admin/login" element={<AdminLogin />} />

      <Route path="admin" element={
        <RequireRole role="ADMIN" loginPath="/admin/login"><AdminLayout /></RequireRole>}>
        <Route index element={<AdminDashboard />} />
        <Route path="organizations" element={<AdminOrganizations />} />
        <Route path="wishes" element={<AdminWishes />} />
        <Route path="claims" element={<AdminClaims />} />
        <Route path="claims/:id" element={<AdminClaimDetail />} />
        <Route path="system" element={<AdminSystem />} />
      </Route>

      <Route element={<PublicLayout />}>
        <Route path="*" element={<NotFound />} />
      </Route>
    </Routes>
  )
}
