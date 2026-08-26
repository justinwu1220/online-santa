import { Route, Routes } from 'react-router-dom'
import { Layout } from './components/Layout'
import { MyClaims } from './routes/MyClaims'
import { NotFound } from './routes/NotFound'
import { OrgConsole } from './routes/OrgConsole'
import { WishWall } from './routes/WishWall'

export default function App() {
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route index element={<WishWall />} />
        <Route path="me/claims" element={<MyClaims />} />
        <Route path="org" element={<OrgConsole />} />
        <Route path="*" element={<NotFound />} />
      </Route>
    </Routes>
  )
}
