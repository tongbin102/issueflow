import { defineStore } from 'pinia'

const APP_KEY = 'if_app'

function loadApp() {
  try {
    return JSON.parse(localStorage.getItem(APP_KEY) || 'null') || {}
  } catch (e) {
    return {}
  }
}

const persisted = loadApp()

export const useAppStore = defineStore('app', {
  state: () => ({
    sidebarCollapsed: !!(persisted && persisted.sidebarCollapsed),
    device: 'desktop' // desktop | mobile
  }),
  getters: {
    isMobile: (state) => state.device === 'mobile'
  },
  actions: {
    toggleSidebar() {
      this.sidebarCollapsed = !this.sidebarCollapsed
      this.persist()
    },
    setSidebarCollapsed(value) {
      this.sidebarCollapsed = !!value
      this.persist()
    },
    setDevice(device) {
      this.device = device
    },
    persist() {
      localStorage.setItem(
        APP_KEY,
        JSON.stringify({ sidebarCollapsed: this.sidebarCollapsed })
      )
    }
  }
})
