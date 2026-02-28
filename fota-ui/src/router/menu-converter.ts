import type { RouteRecordRaw } from 'vue-router'
import type { MenuDTO } from '@/stores/user'
import {componentMap, isLayout, isValidComponentKey} from './component-whitelist'

function convertSingleMenu(menu: MenuDTO): RouteRecordRaw | null {
  const route: RouteRecordRaw = {
    path: menu.path,
    name: menu.name,
    meta: {
      ...(menu.meta || {}),
      sort: menu.sort,
    },
  } as RouteRecordRaw

  if (menu.componentKey && !isLayout(menu.componentKey)) {
    if (!isValidComponentKey(menu.componentKey)) {
      console.error(`[dynamic-menu] invalid component key: ${menu.componentKey}`)
      return null
    }
    route.component = componentMap[menu.componentKey]
  }

  if (menu.redirect) {
    route.redirect = menu.redirect
  }

  if (menu.children?.length) {
    route.children = convertMenusToRoutes(menu.children)
  }

  return route
}

export function convertMenusToRoutes(menus: MenuDTO[]): RouteRecordRaw[] {
  return (menus || [])
    .map(convertSingleMenu)
    .filter((item): item is RouteRecordRaw => item !== null)
}
