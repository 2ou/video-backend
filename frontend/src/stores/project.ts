import {defineStore} from 'pinia'
import {createProjectApi, listProjectsApi} from '../api/projects'

export const useProjectStore = defineStore('project', {
  state: () => ({ projects: [] as any[] }),
  actions: {
    async fetchProjects() {
      const res = await listProjectsApi()
      this.projects = res.data.data
    },
    async createProject(name: string, description: string) {
      await createProjectApi({ name, description })
      await this.fetchProjects()
    }
  }
})
