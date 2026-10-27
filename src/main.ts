import { useFrpcStore } from "@/store/frpc";
import "animate.css";
import { createPinia } from "pinia";
import { createApp } from "vue";
import App from "./App.vue";
import {
  IconifyIconOffline,
  IconifyIconOnline
} from "./components/IconifyIcon";
import i18n from "./lang";
import { installElementPlus } from "./plugins/elementPlus";
import router from "./router";
import "./styles/index.scss";

const pinia = createPinia();

const app = createApp(App);
app.component("IconifyIconOffline", IconifyIconOffline);
app.component("IconifyIconOnline", IconifyIconOnline);
installElementPlus(app);

app
  .use(i18n)
  .use(router)
  .use(pinia)
  .mount("#app")
  .$nextTick(() => {
    const frpcStore = useFrpcStore();
    frpcStore.onListenerFrpcProcessRunning();
    frpcStore.refreshRunning();
    frpcStore.onListenerAppLastRelease();
    window.setTimeout(() => {
      frpcStore.checkNewVersion(false);
    }, 4000);
    frpcStore.onListenerFrpcLanguage();
    frpcStore.getLanguage();

    postMessage({ payload: "removeLoading" }, "*");
  })
  .then(r => {});
