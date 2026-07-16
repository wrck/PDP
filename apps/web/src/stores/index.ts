import { defineStore } from 'pinia'

export const usePlatformStore = defineStore('platform', {
  state: () => ({
    workspaceId: null as string | null,
  }),
  actions: {
    selectWorkspace(workspaceId: string) {
      this.workspaceId = workspaceId
    },
  },
})

