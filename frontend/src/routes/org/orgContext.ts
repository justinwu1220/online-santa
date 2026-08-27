import { useOutletContext } from 'react-router-dom'
import type { OrganizationView } from '../../lib/types'

export interface OrgContext {
  organization: OrganizationView
}

/** 由 OrgConsole 提供，子頁面不必各自再查一次機構資料。 */
export function useOrgContext() {
  return useOutletContext<OrgContext>()
}
