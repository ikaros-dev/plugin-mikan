import { definePlugin } from "@runikaros/shared"
import HelloIkaros from '@/views/HelloIkaros.vue';
import { Files as FilesIcon } from '@element-plus/icons-vue';
import {  markRaw } from "vue"

export default definePlugin({
    name: 'PluginStarter',
    components: {},
    routes: [
      {
        parentName: "Root",
        route: {
          path: '/PluginStarter',
          component: HelloIkaros,
          name: "HelloIkaros",
          meta: {
            title: '示例页面',
            menu: {
              name: '示例页面',
              group: 'content',
              icon: markRaw(FilesIcon),
							priority: 2,
							mobile: true,
            }
          }
        }
      }
    ],
})