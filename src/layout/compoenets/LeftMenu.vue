<script lang="ts" setup>
import { IconifyIconOffline } from "@/components/IconifyIcon";
import { ipcRouters } from "@/core/IpcRouter";
import Intro from "@/intro";
import router from "@/router";
import { on, send } from "@/utils/ipcUtils";
import { useI18n } from "vue-i18n";
import confetti from "canvas-confetti/src/confetti.js";
import "intro.js/introjs.css";
import { computed, defineComponent, onMounted, onUnmounted, ref } from "vue";
import { RouteRecordRaw } from "vue-router";

defineComponent({
  name: "BottomNav"
});

const { t } = useI18n();
const routes = ref<Array<RouteRecordRaw>>([]);
const guideSteps = ref({
  Home: {
    step: "1",
    intro: "此功能用于控制frpc的连接状态，您可以轻松地断开或重新连接。"
  },
  Proxy: {
    step: "2",
    intro:
      "在这里，您可以方便地配置和管理代理设置。无论是添加、修改还是删除代理，您都可以轻松完成。"
  },
  Config: {
    step: "3",
    intro:
      "此功能允许您设置软件的各种配置选项，包括连接方式等。根据您的需求进行个性化设置，以优化使用体验。"
  },
  Logger: {
    step: "4",
    intro:
      "在日志查看功能中，您可以实时查看FRP连接的日志信息。这有助于您监控连接状态，及时排查可能出现的问题。"
  }
});
const currentRoute = computed(() => {
  return router.currentRoute.value;
});

/**
 * 菜单切换
 * @param mi 菜单索引
 */
const handleMenuChange = (route: any) => {
  if (currentRoute.value.name === route.name) {
    return;
  }
  router.push({
    path: route.path
  });
};

const handleCompleteGuide: () => boolean = () => {
  // 礼花
  confetti({
    zIndex: 12002,
    particleCount: 200,
    spread: 70,
    origin: { y: 0.6 }
  });
  send(ipcRouters.SYSTEM.completeGuide);
  return true; // 确保返回 boolean
};

let offGuideStatus: (() => void) | undefined;

onMounted(() => {
  routes.value = router.options.routes[0].children?.filter(
    f => !f.meta?.hidden
  ) as Array<RouteRecordRaw>;

  offGuideStatus = on(ipcRouters.SYSTEM.getGuideStatus, (completed: boolean) => {
    if (completed) {
      return;
    }
    // 兼容旧版本保存在 localStorage 中的引导状态
    if (localStorage.getItem("guide")) {
      send(ipcRouters.SYSTEM.completeGuide);
      localStorage.removeItem("guide");
      return;
    }
    // 开始
    Intro.onBeforeExit(handleCompleteGuide).start();
  });
  send(ipcRouters.SYSTEM.getGuideStatus);
});

onUnmounted(() => {
  offGuideStatus?.();
});
</script>

<template>
  <div class="left-menu-container">
    <ul class="menu-container">
      <li
        v-for="r in routes"
        :key="r.name"
        :data-step="guideSteps[r.name]?.step"
        :data-intro="guideSteps[r.name]?.intro"
        :data-disable-interaction="true"
        class="menu animate__animated"
        :class="currentRoute?.name === r.name ? 'menu-selected' : ''"
        @click="handleMenuChange(r)"
      >
        <IconifyIconOffline
          class="animate__animated"
          :icon="r?.meta?.icon as string"
        ></IconifyIconOffline>
        <span class="menu-label">{{ t(r?.meta?.title as string) }}</span>
      </li>
    </ul>
  </div>
</template>
