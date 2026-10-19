import { lazy, Suspense } from 'react'
import { Navigate, Route, Routes, useLocation } from 'react-router-dom'
import { PublicLayout } from './components/layouts/PublicLayout'
import { Spinner } from './components/Feedback'
import { RequireRole } from './components/RequireRole'
import { AdminLayout } from './routes/admin/AdminLayout'
import { AdminOrganizations } from './routes/admin/AdminOrganizations'
import { AdminOverview } from './routes/admin/AdminOverview'
import { AdminWishes } from './routes/admin/AdminWishes'
import { AdminSystem } from './routes/admin/AdminSystem'
import { ClaimDetail } from './routes/ClaimDetail'
import { LoginPage } from './routes/LoginPage'
import { MyClaims } from './routes/MyClaims'
import { MyProfile } from './routes/MyProfile'
import { NotFound } from './routes/NotFound'
import { OrgApply } from './routes/org/OrgApply'
import { OrgLayout } from './routes/org/OrgLayout'
import { OrgSettings } from './routes/org/OrgSettings'
import { OrgTeamChat } from './routes/org/OrgTeamChat'
import { OrgWishes } from './routes/org/OrgWishes'
import { WishDetail } from './routes/WishDetail'
import { WishWall } from './routes/WishWall'

// 年度回顧兩頁用了 Recharts，體積不小又只有機構後台與監控中心用得到，
// lazy 載入避免它進到主網站的首頁 bundle
const OrgAnnualReview = lazy(() => import('./routes/org/OrgAnnualReview')
  .then((m) => ({ default: m.OrgAnnualReview })))
const AdminAnnualReview = lazy(() => import('./routes/admin/AdminAnnualReview')
  .then((m) => ({ default: m.AdminAnnualReview })))

/** 已併入 `/login` 的舊登入網址，保留 query（`?next=` 等）轉過去 */
function LegacyLoginRedirect({ to = '/login' }: { to?: string }) {
  const { search } = useLocation()
  const joiner = to.includes('?') ? '&' : '?'
  return <Navigate to={search ? `${to}${joiner}${search.slice(1)}` : to} replace />
}

/**
 * 三個區域共用同一套程式碼與身分系統，登入統一在 `/login`。
 *
 * - `/`      主網站：願望牆公開，認領與紀錄需登入
 * - `/org`   機構後台：機構成員從願望牆頁首的「機構後台」按鈕進入
 * - `/admin` 監控中心：管理員從願望牆頁首的「監控中心」按鈕進入
 *
 * 登入頁刻意放在 layout 之外——它有自己的全螢幕版面，而且必須在
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
          <RequireRole role="DONOR"><MyClaims /></RequireRole>} />
        <Route path="me/claims/:id" element={
          <RequireRole role="DONOR"><ClaimDetail /></RequireRole>} />

        {/* 不用 RequireRole：它只接受單一角色，會把機構成員與管理員也鎖在
            個人檔案外面，而任何登入者都該能編輯自己的資料 */}
        <Route path="me/profile" element={<MyProfile />} />
      </Route>

      {/* ---------------------------------------------------------- 機構後台 */}
      {/* 舊網址轉到統一登入頁，書籤與信件裡的連結才不會失效 */}
      <Route path="org/login" element={<LegacyLoginRedirect />} />
      <Route path="org/register" element={<LegacyLoginRedirect to="/login?tab=register&as=org" />} />
      {/* 申請的第二步：機構資料。帳號在 /login 註冊，未登入者會被導回去。
          角色分流由頁面自己處理，授權在後端 */}
      <Route path="org/apply" element={<OrgApply />} />

      <Route path="org" element={
        <RequireRole role="ORG_MEMBER" wrongRole="redirect">
          <OrgLayout />
        </RequireRole>}>
        <Route index element={<OrgWishes />} />
        <Route path="chat" element={<OrgTeamChat />} />
        <Route path="annual" element={
          <Suspense fallback={<Spinner label="載入年度回顧" />}>
            <OrgAnnualReview />
          </Suspense>} />
        <Route path="settings" element={<OrgSettings />} />
      </Route>

      {/* ---------------------------------------------------------- 監控中心 */}
      <Route path="admin/login" element={<LegacyLoginRedirect />} />

      <Route path="admin" element={
        <RequireRole role="ADMIN" wrongRole="redirect">
          <AdminLayout />
        </RequireRole>}>
        <Route index element={<AdminOverview />} />
        <Route path="orgs" element={<AdminOrganizations />} />
        <Route path="wishes" element={<AdminWishes />} />
        <Route path="annual" element={
          <Suspense fallback={<Spinner label="載入年度營運" />}>
            <AdminAnnualReview />
          </Suspense>} />
        <Route path="system" element={<AdminSystem />} />
      </Route>

      <Route element={<PublicLayout />}>
        <Route path="*" element={<NotFound />} />
      </Route>
    </Routes>
  )
}
