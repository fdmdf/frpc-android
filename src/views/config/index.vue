<script lang="ts" setup>
import IconifyIconOffline from "@/components/IconifyIcon/src/iconifyIconOffline";
import { useFrpcStore } from "@/store/frpc";
import { on, send } from "@/utils/ipcUtils";
import { bundledFrpcVersion } from "@/utils/platform";
import { useDebounceFn } from "@vueuse/core";
import { ElMessage, ElMessageBox, FormInstance, FormRules } from "element-plus";
import { Base64 } from "js-base64";
import _ from "lodash";
import {
  nextTick,
  onActivated,
  onDeactivated,
  onMounted,
  onUnmounted,
  reactive,
  ref,
  watch
} from "vue";
import { useI18n } from "vue-i18n";
import { ipcRouters } from "@/core/IpcRouter";
import ConfigToolbar from "./ConfigToolbar.vue";
import LogConfig from "./LogConfig.vue";
import SystemConfig from "./SystemConfig.vue";
import WebServerConfig from "./WebServerConfig.vue";

defineOptions({
  name: "ConfigPage"
});

// type ShareLinkConfig = {
//   serverAddr: string;
//   serverPort: number;
//   authMethod: string;
//   authToken: string;
//   transportHeartbeatInterval: number;
//   transportHeartbeatTimeout: number;
//   user: string;
//   metaToken: string;
// };

const { t } = useI18n();

const defaultFormData: OpenSourceFrpcServer = {
  _id: "",
  multiuser: false,
  loginFailExit: false,
  udpPacketSize: 1500,
  serverAddr: "",
  serverPort: 7000,
  auth: {
    method: "",
    token: ""
  },
  log: {
    to: "",
    level: "info",
    maxDays: 3,
    disablePrintColor: false
  },
  transport: {
    dialServerTimeout: 10,
    dialServerKeepalive: 7200,
    poolCount: 0,
    tcpMux: true,
    tcpMuxKeepaliveInterval: 30,
    protocol: "tcp",
    connectServerLocalIP: "",
    proxyURL: "",
    tls: {
      enable: true,
      certFile: "",
      keyFile: "",
      trustedCaFile: "",
      serverName: "",
      disableCustomTLSFirstByte: true
    },
    heartbeatInterval: 30,
    heartbeatTimeout: 90
  },
  metadatas: {
    token: ""
  },
  webServer: {
    enable: true,
    addr: "127.0.0.1",
    port: 57400,
    user: "",
    password: "",
    pprofEnable: false
  },
  system: {
    autoConnectOnStartup: false,
    language: "en-US"
  },
  user: ""
};
const formData = ref<OpenSourceFrpcServer>(defaultFormData);
const loading = ref(1);
const rules = reactive<FormRules>({
  serverAddr: [
    {
      required: true,
      message: t("config.form.serverAddr.requireMessage"),
      trigger: "blur"
    },
    {
      pattern: /^[\w-]+(\.[\w-]+)+$/,
      message: t("config.form.serverAddr.patternMessage"),
      trigger: "blur"
    }
  ],
  serverPort: [
    {
      required: true,
      message: t("config.form.serverPort.requireMessage"),
      trigger: "blur"
    }
  ],
  user: [
    {
      required: true,
      message: t("config.form.user.requireMessage"),
      trigger: "blur"
    }
  ],
  multiuser: [
    {
      required: true,
      message: t("config.form.multiuser.requireMessage"),
      trigger: "blur"
    }
  ],
  "metadatas.token": [
    {
      required: true,
      message: t("config.form.metadatasToken.requireMessage"),
      trigger: "blur"
    }
  ],
  "auth.method": [
    {
      required: true,
      message: t("config.form.authMethod.requireMessage"),
      trigger: "blur"
    }
  ],
  "auth.token": [
    {
      required: true,
      message: t("config.form.authToken.requireMessage"),
      trigger: "blur"
    }
  ],
  "log.level": [
    {
      required: true,
      message: t("config.form.logLevel.requireMessage"),
      trigger: "blur"
    }
  ],
  "log.maxDays": [
    {
      required: true,
      message: t("config.form.logMaxDays.requireMessage"),
      trigger: "blur"
    }
  ],
  "tls.enable": [
    {
      required: true,
      message: t("config.form.tlsEnable.requireMessage"),
      trigger: "change"
    }
  ],
  // tlsConfigCertFile: [
  //   { required: true, message: "请选择 TLS 证书文件", trigger: "change" }
  // ],
  // tlsConfigKeyFile: [
  //   { required: true, message: "请选择 TLS 密钥文件", trigger: "change" }
  // ],
  // tlsConfigTrustedCaFile: [
  //   { required: true, message: "请选择 CA 证书文件", trigger: "change" }
  // ],
  // tlsConfigServerName: [
  //   { required: true, message: "请输入 TLS Server 名称", trigger: "blur" }
  // ],
  // proxyConfigEnable: [
  //   { required: true, message: "请选择代理状态", trigger: "change" }
  // ],
  "transport.proxyURL": [
    {
      required: false,
      message: t("config.form.proxyURL.requireMessage"),
      trigger: "change"
    },
    {
      pattern: /^https?\:\/\/(\w+:\w+@)?([a-zA-Z0-9.-]+)(:\d+)?$/,
      message: t("config.form.proxyURL.patternMessage"),
      trigger: "blur"
    }
  ],
  "system.autoConnectOnStartup": [
    {
      required: true,
      message: t("config.form.systemAutoConnectOnStartup.requireMessage"),
      trigger: "change"
    }
  ],
  "transport.heartbeatInterval": [
    {
      required: true,
      message: t("config.form.heartbeatInterval.requireMessage"),
      trigger: "change"
    }
  ],
  "transport.heartbeatTimeout": [
    {
      required: true,
      message: t("config.form.heartbeatTimeout.requireMessage"),
      trigger: "change"
    }
  ],
  // webEnable: [
  //   { required: true, message: "web界面开关不能为空", trigger: "change" }
  // ],
  "webServer.port": [
    {
      required: true,
      message: t("config.form.webPort.requireMessage"),
      trigger: "change"
    }
  ],
  "transport.protocol": [
    {
      required: true,
      message: t("config.form.transportProtocol.requireMessage"),
      trigger: "change"
    }
  ],
  "transport.dialServerTimeout": [
    {
      required: true,
      message: t("config.form.dialServerTimeout.requireMessage"),
      trigger: "change"
    }
  ],
  "transport.dialServerKeepalive": [
    {
      required: true,
      message: t("config.form.dialServerKeepalive.requireMessage"),
      trigger: "change"
    }
  ],
  transportPoolCount: [
    {
      required: true,
      message: t("config.form.transportPoolCount.requireMessage"),
      trigger: "change"
    }
  ],
  "transport.tcpMux": [
    {
      required: true,
      message: t("config.form.transportTcpMux.requireMessage"),
      trigger: "change"
    }
  ],
  "transport.tcpMuxKeepaliveInterval": [
    {
      required: true,
      message: t("config.form.transportTcpMuxKeepaliveInterval.requireMessage"),
      trigger: "change"
    }
  ],
  "system.language": [
    {
      required: true,
      message: t("config.form.systemLanguage.requireMessage"),
      trigger: "change"
    }
  ]
});
const copyServerConfigBase64 = ref();
const pasteServerConfigBase64 = ref();
const formRef = ref<FormInstance>();
const protocol = ref("frp://");
const frpcStore = useFrpcStore();
const formDirty = ref(false);
let hydratingForm = true;
let hasActivatedOnce = false;
const listenerCleanups: Array<() => void> = [];

watch(
  formData,
  () => {
    if (!hydratingForm) formDirty.value = true;
  },
  { deep: true }
);

const visible = reactive({
  copyServerConfig: false,
  pasteServerConfig: false
});

// const exportConfigType = ref("toml");

const handleSubmit = useDebounceFn(() => {
  if (!formRef.value) return;
  formRef.value.validate(valid => {
    if (valid) {
      loading.value = 1;
      const data = _.cloneDeep(formData.value);
      send(ipcRouters.SERVER.saveConfig, data);
    }
  });
}, 300);

const handleKeydown = (e: KeyboardEvent) => {
  if ((e.ctrlKey || e.metaKey) && (e.key === "s" || e.key === "S")) {
    e.preventDefault();
    handleSubmit();
  }
};

const handleMultiuserChange = e => {
  if (e) {
    ElMessageBox.alert(
      t("config.alert.multiuserAlert.message"),
      t("config.alert.multiuserAlert.title"),
      {
        // if you want to disable its autofocus
        autofocus: false,
        confirmButtonText: t("config.alert.multiuserAlert.confirm"),
        dangerouslyUseHTMLString: true
      }
    );
  }
};

// const handleLoadDownloadedVersion = () => {
//   send(ipcRouters.VERSION.getDownloadedVersions);
// };

const handleLoadSavedConfig = () => {
  loading.value = 1;
  send(ipcRouters.SERVER.getServerConfig);
};

onMounted(() => {
  // handleLoadDownloadedVersion();
  handleLoadSavedConfig();

  listenerCleanups.push(
    on(ipcRouters.SERVER.getServerConfig, data => {
      hydratingForm = true;
      if (data) {
        formData.value = data;
        Object.keys(defaultFormData).forEach(key => {
          if (
            formData.value[key] === undefined ||
            formData.value[key] === null
          ) {
            formData.value[key] = defaultFormData[key];
          }
        });
      }
      loading.value = 0;
      formDirty.value = false;
      nextTick(() => {
        hydratingForm = false;
      });
    })
  );

  // on(ipcRouters.VERSION.getDownloadedVersions, data => {
  //   // versions.value = data;
  //   checkAndResetVersion();
  // });

  listenerCleanups.push(
    on(ipcRouters.SERVER.saveConfig, () => {
      ElMessage({
        type: "success",
        message: t("config.message.saveSuccess")
      });
      loading.value = 0;
      formDirty.value = false;
      frpcStore.getLanguage();
    })
  );

  listenerCleanups.push(
    on(ipcRouters.SERVER.resetAllConfig, () => {
      ElMessageBox.alert(
        t("config.alert.resetConfigSuccess.message"),
        t("config.alert.resetConfigSuccess.title"),
        {
          closeOnClickModal: false,
          showClose: false,
          confirmButtonText: t("config.alert.resetConfigSuccess.confirm")
        }
      ).then(() => {
        send(ipcRouters.SYSTEM.relaunchApp);
      });
    })
  );

  listenerCleanups.push(
    on(ipcRouters.SERVER.saveLanguage, () => {
      ElMessage({
        type: "success",
        message: t("config.message.saveSuccess")
      });
      loading.value = 0;
      frpcStore.getLanguage();
    })
  );

  window.addEventListener("keydown", handleKeydown);
});

onActivated(() => {
  window.removeEventListener("keydown", handleKeydown);
  window.addEventListener("keydown", handleKeydown);
  if (!hasActivatedOnce) {
    hasActivatedOnce = true;
    return;
  }
  if (!formDirty.value) handleLoadSavedConfig();
});

onDeactivated(() => {
  window.removeEventListener("keydown", handleKeydown);
});

/**
 * 分享配置
 */
const handleCopyServerConfig2Base64 = useDebounceFn(() => {
  const { _id, webServer, system, log, udpPacketSize, loginFailExit, ...shareConfig } =
    _.cloneDeep(formData.value);
  shareConfig.transport.tls.certFile = "";
  shareConfig.transport.tls.keyFile = "";
  shareConfig.transport.tls.trustedCaFile = "";
  const base64str = Base64.encode(JSON.stringify(shareConfig));
  copyServerConfigBase64.value = protocol.value + base64str;
  visible.copyServerConfig = true;
}, 300);

const handlePasteServerConfig4Base64 = useDebounceFn(() => {
  visible.pasteServerConfig = true;
}, 300);

const handlePasteServerConfigBase64 = useDebounceFn(() => {
  const tips = () => {
    ElMessage({
      type: "warning",
      message: t("config.message.invalidLink")
    });
  };
  if (!pasteServerConfigBase64.value.startsWith(protocol.value)) {
    tips();
    return;
  }
  const ciphertext = pasteServerConfigBase64.value.replace("frp://", "");
  const plaintext = Base64.decode(ciphertext);
  let serverConfig = null;
  try {
    serverConfig = JSON.parse(plaintext);
  } catch {
    tips();
    return;
  }

  if (!serverConfig && !serverConfig.serverAddr) {
    tips();
    return;
  }
  if (!serverConfig && !serverConfig.serverPort) {
    tips();
    return;
  }
  formData.value.transport =
    serverConfig.transport || defaultFormData.transport;
  formData.value.auth = serverConfig.auth || defaultFormData.auth;
  formData.value.serverAddr =
    serverConfig.serverAddr || defaultFormData.serverAddr;
  formData.value.serverPort =
    serverConfig.serverPort || defaultFormData.serverPort;
  formData.value.metadatas =
    serverConfig.metadatas || defaultFormData.metadatas;
  formData.value.user = serverConfig.user || defaultFormData.user;
  handleSubmit();
  pasteServerConfigBase64.value = "";
  visible.pasteServerConfig = false;
}, 300);

const handleResetConfig = () => {
  ElMessageBox.alert(
    t("config.alert.resetConfig.message"),
    t("config.alert.resetConfig.title"),
    {
      showCancelButton: true,
      cancelButtonText: t("config.alert.resetConfig.cancel"),
      confirmButtonText: t("config.alert.resetConfig.confirm")
    }
  ).then(() => {
    send(ipcRouters.SERVER.resetAllConfig);
  });
};

const handleSystemLanguageChange = e => {
  send(ipcRouters.SERVER.saveLanguage, e);
};

onUnmounted(() => {
  window.removeEventListener("keydown", handleKeydown);
  listenerCleanups.splice(0).forEach(off => off());
});
</script>
<template>
  <div class="main">
    <ConfigToolbar
      @refresh="handleLoadSavedConfig"
      @reset="handleResetConfig"
      @save="handleSubmit"
    />
    <div v-loading="loading > 0" class="pr-2 app-container-breadcrumb">
      <div class="p-4 w-full bg-white rounded drop-shadow-lg">
        <el-form
          ref="formRef"
          :model="formData"
          :rules="rules"
          label-position="right"
          label-width="150"
        >
          <el-row :gutter="10">
            <el-col :span="24">
              <div class="flex justify-between h2">
                <div>{{ t("config.title.versionSelection") }}</div>
              </div>
            </el-col>
            <el-col :span="24">
              <el-form-item :label="t('config.form.frpcVerson.label')">
                <div class="flex gap-2 items-center w-full">
                  <el-tag size="small" type="success">
                    {{ bundledFrpcVersion }}
                  </el-tag>
                  <span class="text-xs text-gray-500">
                    {{ t("config.form.frpcVerson.androidBuiltin") }}
                  </span>
                </div>
              </el-form-item>
            </el-col>
            <el-col :span="24">
              <div class="flex justify-between h2">
                <div>{{ t("config.title.serverConfiguration") }}</div>
                <div class="flex justify-center items-center">
                  <IconifyIconOffline
                    class="mr-2 text-xl font-bold cursor-pointer"
                    icon="content-copy"
                    @click="handleCopyServerConfig2Base64"
                  />
                  <IconifyIconOffline
                    class="mr-2 text-xl font-bold cursor-pointer"
                    icon="content-paste-go"
                    @click="handlePasteServerConfig4Base64"
                  />
                </div>
              </div>
            </el-col>
            <el-col :span="24">
              <el-form-item
                :label="t('config.form.serverAddr.label')"
                prop="serverAddr"
              >
                <template #label>
                  <div class="flex items-center mr-1 h-full">
                    <el-popover placement="top" trigger="hover" width="300">
                      <template #default>
                        <div
                          v-html="
                            t('config.form.serverAddr.tips', {
                              frpParameter: t('config.popover.frpParameter')
                            })
                          "
                        ></div>
                      </template>
                      <template #reference>
                        <IconifyIconOffline
                          class="text-base"
                          color="#5A3DAA"
                          icon="info"
                        />
                      </template>
                    </el-popover>
                  </div>
                  {{ t("config.form.serverAddr.label") }}
                </template>
                <el-input
                  v-model="formData.serverAddr"
                  placeholder="127.0.0.1"
                ></el-input>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item
                :label="t('config.form.serverPort.label')"
                prop="serverPort"
              >
                <el-input-number
                  v-model="formData.serverPort"
                  placeholder="7000"
                  :min="0"
                  :max="65535"
                  controls-position="right"
                  class="!w-full"
                ></el-input-number>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item
                :label="t('config.form.authMethod.label')"
                prop="auth.method"
              >
                <template #label>
                  <div class="flex items-center mr-1 h-full">
                    <el-popover width="300" placement="top" trigger="hover">
                      <template #default>
                        {{ t("config.popover.frpParameter") }}:
                        <span class="font-black text-[#5A3DAA]"
                          >auth.method</span
                        >
                      </template>
                      <template #reference>
                        <!--                        <IconifyIconOffline class="text-base" color="#5A3DAA" icon="info"/>-->
                        <IconifyIconOffline
                          class="text-base"
                          color="#5A3DAA"
                          icon="info"
                        />
                      </template>
                    </el-popover>
                  </div>
                  {{ t("config.form.authMethod.label") }}
                </template>
                <el-select
                  v-model="formData.auth.method"
                  :placeholder="t('config.form.authMethod.requireMessage')"
                  clearable
                >
                  <el-option
                    :label="t('config.form.authMethod.none')"
                    value="none"
                  ></el-option>
                  <el-option
                    :label="t('config.form.authMethod.token')"
                    value="token"
                  ></el-option>
                  <!--                  <el-option label="多用户" value="multiuser"></el-option>-->
                </el-select>
              </el-form-item>
            </el-col>
            <el-col v-if="formData.auth.method === 'token'" :span="24">
              <el-form-item
                :label="t('config.form.authToken.label')"
                prop="authToken"
              >
                <template #label>
                  <div class="flex items-center mr-1 h-full">
                    <el-popover placement="top" trigger="hover" width="300">
                      <template #default>
                        {{ t("config.popover.frpParameter") }}:<span
                          class="font-black text-[#5A3DAA]"
                          >auth.token</span
                        >
                      </template>
                      <template #reference>
                        <IconifyIconOffline
                          class="text-base"
                          color="#5A3DAA"
                          icon="info"
                        />
                      </template>
                    </el-popover>
                  </div>
                  {{ t("config.form.authToken.label") }}
                </template>
                <el-input
                  v-model="formData.auth.token"
                  placeholder="token"
                  type="password"
                  :show-password="true"
                />
              </el-form-item>
            </el-col>
            <el-col :span="24">
              <el-form-item
                :label="t('config.form.multiuser.label')"
                prop="multiuser"
              >
                <!--
                <template #label>
                  <div class="flex items-center mr-1 h-full">
                    <el-popover placement="top" trigger="hover">
                      <template #default> 是否开启多用户模式 </template>
                      <template #reference>
                        <IconifyIconOffline
                          class="text-base"
                          color="#5A3DAA"
                          icon="info"
                        />
                      </template>
                    </el-popover>
                  </div>
                  {{ t("config.form.multiuser.label") }}
                </template>
                -->
                <el-switch
                  v-model="formData.multiuser"
                  :active-text="t('common.yes')"
                  :inactive-text="t('common.no')"
                  inline-prompt
                  @change="handleMultiuserChange"
                />
              </el-form-item>
            </el-col>
            <el-col v-if="formData.multiuser" :span="12">
              <el-form-item :label="t('config.form.user.label')" prop="user">
                <template #label>
                  <div class="flex items-center mr-1 h-full">
                    <el-popover placement="top" trigger="hover" width="300">
                      <template #default>
                        {{ t("config.popover.frpParameter") }}:<span
                          class="font-black text-[#5A3DAA]"
                          >user</span
                        >
                      </template>
                      <template #reference>
                        <IconifyIconOffline
                          class="text-base"
                          color="#5A3DAA"
                          icon="info"
                        />
                      </template>
                    </el-popover>
                  </div>
                  {{ t("config.form.user.label") }}
                </template>
                <el-input
                  v-model="formData.user"
                  :placeholder="t('config.form.user.placeholder')"
                />
              </el-form-item>
            </el-col>
            <el-col v-if="formData.multiuser" :span="12">
              <el-form-item
                :label="t('config.form.metadatasToken.label')"
                prop="metadatas.token"
              >
                <template #label>
                  <div class="flex items-center mr-1 h-full">
                    <el-popover width="300" placement="top" trigger="hover">
                      <template #default>
                        {{ t("config.popover.frpParameter") }}:<span
                          class="font-black text-[#5A3DAA]"
                          >metadatas.token</span
                        >
                      </template>
                      <template #reference>
                        <IconifyIconOffline
                          class="text-base"
                          color="#5A3DAA"
                          icon="info"
                        />
                      </template>
                    </el-popover>
                  </div>
                  {{ t("config.form.metadatasToken.label") }}
                </template>
                <el-input
                  v-model="formData.metadatas.token"
                  :placeholder="t('config.form.metadatasToken.placeholder')"
                  type="password"
                  :show-password="true"
                />
              </el-form-item>
            </el-col>
            <!-- <el-col :span="24">
              <div class="h2">TLS Config</div>
            </el-col> -->

            <el-col :span="24">
              <div class="h2">
                {{ t("config.title.transportConfiguration") }}
              </div>
            </el-col>
            <el-col :span="12">
              <el-form-item
                :label="t('config.form.transportProtocol.label')"
                prop="transport.protocol"
                label-width="180"
              >
                <template #label>
                  <div class="flex items-center mr-1 h-full">
                    <el-popover width="300" placement="top" trigger="hover">
                      <template #default>
                        <div
                          v-html="
                            t('config.form.transportProtocol.tips', {
                              frpParameter: t('config.popover.frpParameter')
                            })
                          "
                        ></div>
                      </template>
                      <template #reference>
                        <IconifyIconOffline
                          class="text-base"
                          color="#5A3DAA"
                          icon="info"
                        />
                      </template>
                    </el-popover>
                  </div>
                  {{ t("config.form.transportProtocol.label") }}
                </template>
                <el-select v-model="formData.transport.protocol">
                  <el-option label="tcp" value="tcp" />
                  <el-option label="kcp" value="kcp" />
                  <el-option label="quic" value="quic" />
                  <el-option label="websocket" value="websocket" />
                  <el-option label="wss" value="wss" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item
                :label="t('config.form.transportPoolCount.label')"
                prop="transport.poolCount"
                label-width="180"
              >
                <template #label>
                  <div class="flex items-center mr-1 h-full">
                    <el-popover width="300" placement="top" trigger="hover">
                      <template #default>
                        {{ t("config.popover.frpParameter") }}:<span
                          class="font-black text-[#5A3DAA]"
                          >transport.poolCount</span
                        >
                      </template>
                      <template #reference>
                        <IconifyIconOffline
                          class="text-base"
                          color="#5A3DAA"
                          icon="info"
                        />
                      </template>
                    </el-popover>
                  </div>
                  {{ t("config.form.transportPoolCount.label") }}
                </template>
                <el-input-number
                  v-model="formData.transport.poolCount"
                  class="w-full"
                  controls-position="right"
                ></el-input-number>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item
                :label="t('config.form.transportHeartbeatInterval.label')"
                prop="transport.heartbeatInterval"
                label-width="180"
              >
                <template #label>
                  <div class="flex items-center mr-1 h-full">
                    <el-popover width="300" placement="top" trigger="hover">
                      <template #default>
                        <div
                          v-html="
                            t('config.form.transportHeartbeatInterval.tips', {
                              frpParameter: t('config.popover.frpParameter')
                            })
                          "
                        />
                      </template>
                      <template #reference>
                        <IconifyIconOffline
                          class="text-base"
                          color="#5A3DAA"
                          icon="info"
                        />
                      </template>
                    </el-popover>
                  </div>
                  {{ t("config.form.transportHeartbeatInterval.label") }}
                </template>
                <el-input-number
                  v-model="formData.transport.heartbeatInterval"
                  class="w-full"
                  :min="1"
                  :max="600"
                  controls-position="right"
                />
                <!--                <el-input-->
                <!--                    placeholder="请输入心跳间隔"-->
                <!--                    type="number"-->
                <!--                    :min="0"-->
                <!--                    v-model="formData.heartbeatInterval"-->
                <!--                >-->
                <!--                  <template #append>秒</template>-->
                <!--                </el-input>-->
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item
                :label="t('config.form.transportHeartbeatTimeout.label')"
                prop="transport.heartbeatTimeout"
                label-width="180"
              >
                <template #label>
                  <div class="flex items-center mr-1 h-full">
                    <el-popover width="300" placement="top" trigger="hover">
                      <template #default>
                        <div
                          v-html="
                            t('config.form.transportHeartbeatTimeout.tips', {
                              frpParameter: t('config.popover.frpParameter')
                            })
                          "
                        />
                      </template>
                      <template #reference>
                        <IconifyIconOffline
                          class="text-base"
                          color="#5A3DAA"
                          icon="info"
                        />
                      </template>
                    </el-popover>
                  </div>
                  {{ t("config.form.transportHeartbeatTimeout.label") }}
                </template>
                <el-input-number
                  v-model="formData.transport.heartbeatTimeout"
                  class="w-full"
                  :min="1"
                  :max="600"
                  controls-position="right"
                />
                <!--                <el-input-->
                <!--                    placeholder="请输入心跳超时时间"-->
                <!--                    :min="0"-->
                <!--                    type="number"-->
                <!--                    v-model="formData.heartbeatTimeout"-->
                <!--                >-->
                <!--                  <template #append>秒</template>-->
                <!--                </el-input>-->
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item
                :label="t('config.form.transportDialServerTimeout.label')"
                prop="transport.dialServerTimeout"
                label-width="180"
              >
                <template #label>
                  <div class="flex items-center mr-1 h-full">
                    <el-popover width="300" placement="top" trigger="hover">
                      <template #default>
                        <div
                          v-html="
                            t('config.form.transportDialServerTimeout.tips', {
                              frpParameter: t('config.popover.frpParameter')
                            })
                          "
                        ></div>
                      </template>
                      <template #reference>
                        <IconifyIconOffline
                          class="text-base"
                          color="#5A3DAA"
                          icon="info"
                        />
                      </template>
                    </el-popover>
                  </div>
                  {{ t("config.form.transportDialServerTimeout.label") }}
                </template>
                <el-input-number
                  v-model="formData.transport.dialServerTimeout"
                  class="w-full"
                  controls-position="right"
                ></el-input-number>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item
                :label="t('config.form.transportDialServerKeepalive.label')"
                prop="transport.dialServerKeepalive"
                label-width="180"
              >
                <template #label>
                  <div class="flex items-center mr-1 h-full">
                    <el-popover width="300" placement="top" trigger="hover">
                      <template #default>
                        <div
                          v-html="
                            t('config.form.transportDialServerKeepalive.tips', {
                              frpParameter: t('config.popover.frpParameter')
                            })
                          "
                        ></div>
                      </template>
                      <template #reference>
                        <IconifyIconOffline
                          class="text-base"
                          color="#5A3DAA"
                          icon="info"
                        />
                      </template>
                    </el-popover>
                  </div>
                  {{ t("config.form.transportDialServerKeepalive.label") }}
                </template>
                <el-input-number
                  v-model="formData.transport.dialServerKeepalive"
                  class="w-full"
                  controls-position="right"
                ></el-input-number>
              </el-form-item>
            </el-col>

            <el-col :span="12">
              <el-form-item
                :label="t('config.form.transportTcpMux.label')"
                prop="transport.tcpMux"
                label-width="180"
              >
                <template #label>
                  <div class="flex items-center mr-1 h-full">
                    <el-popover width="300" placement="top" trigger="hover">
                      <template #default>
                        <div
                          v-html="
                            t('config.form.transportTcpMux.tips', {
                              frpParameter: t('config.popover.frpParameter')
                            })
                          "
                        ></div>
                      </template>
                      <template #reference>
                        <IconifyIconOffline
                          class="text-base"
                          color="#5A3DAA"
                          icon="info"
                        />
                      </template>
                    </el-popover>
                  </div>
                  {{ t("config.form.transportTcpMux.label") }}
                </template>
                <el-switch
                  v-model="formData.transport.tcpMux"
                  :active-text="t('common.yes')"
                  inline-prompt
                  :inactive-text="t('common.no')"
                />
              </el-form-item>
            </el-col>
            <el-col v-if="formData.transport.tcpMux" :span="12">
              <el-form-item
                :label="t('config.form.transportTcpMuxKeepaliveInterval.label')"
                prop="transport.tcpMuxKeepaliveInterval"
                label-width="180"
              >
                <template #label>
                  <div class="flex items-center mr-1 h-full">
                    <el-popover width="300" placement="top" trigger="hover">
                      <template #default>
                        <div
                          v-html="
                            t(
                              'config.form.transportTcpMuxKeepaliveInterval.tips',
                              {
                                frpParameter: t('config.popover.frpParameter')
                              }
                            )
                          "
                        ></div>
                      </template>
                      <template #reference>
                        <IconifyIconOffline
                          class="text-base"
                          color="#5A3DAA"
                          icon="info"
                        />
                      </template>
                    </el-popover>
                  </div>
                  {{ t("config.form.transportTcpMuxKeepaliveInterval.label") }}
                </template>
                <el-input-number
                  v-model="formData.transport.tcpMuxKeepaliveInterval"
                  class="w-full"
                  controls-position="right"
                ></el-input-number>
              </el-form-item>
            </el-col>
            <!--            <el-col :span="24">-->
            <!--              <el-form-item label="启用代理：" prop="proxyConfigEnable">-->
            <!--                <el-switch-->
            <!--                  active-text="开"-->
            <!--                  inline-prompt-->
            <!--                  inactive-text="关"-->
            <!--                  v-model="formData.proxyConfigEnable"-->
            <!--                />-->
            <!--              </el-form-item>-->
            <!--            </el-col>-->
            <!--            <template v-if="formData.proxyConfigEnable">-->
            <el-col :span="24">
              <el-form-item
                :label="t('config.form.transportProxyURL.label')"
                prop="transport.proxyURL"
                label-width="180"
              >
                <template #label>
                  <div class="flex items-center mr-1 h-full">
                    <el-popover width="300" placement="top" trigger="hover">
                      <template #default>
                        {{ t("config.popover.frpParameter") }}:<span
                          class="font-black text-[#5A3DAA]"
                          >transport.proxyURL</span
                        >
                      </template>
                      <template #reference>
                        <IconifyIconOffline
                          class="text-base"
                          color="#5A3DAA"
                          icon="info"
                        />
                      </template>
                    </el-popover>
                  </div>
                  {{ t("config.form.transportProxyURL.label") }}
                </template>
                <el-input
                  v-model="formData.transport.proxyURL"
                  placeholder="http://user:pwd@192.168.1.128:8080"
                />
              </el-form-item>
            </el-col>
            <!--            </template>-->
            <el-col :span="24">
              <el-form-item
                :label="t('config.form.tlsEnable.label')"
                prop="transport.tls.enable"
                label-width="180"
              >
                <el-switch
                  v-model="formData.transport.tls.enable"
                  :active-text="t('common.yes')"
                  :inactive-text="t('common.no')"
                  inline-prompt
                />
              </el-form-item>
            </el-col>
            <template v-if="formData.transport.tls.enable">
              <el-col :span="24">
                <el-form-item
                  :label="t('config.form.tlsCertFile.label')"
                  prop="tlsConfigCertFile"
                  label-width="180"
                >
                  <template #label>
                    <div class="flex items-center mr-1 h-full">
                      <el-popover width="300" placement="top" trigger="hover">
                        <template #default>
                          {{ t("config.popover.frpParameter") }}:<span
                            class="font-black text-[#5A3DAA]"
                            >transport.tls.certFile</span
                          >
                        </template>
                        <template #reference>
                          <IconifyIconOffline
                            class="text-base"
                            color="#5A3DAA"
                            icon="info"
                          />
                        </template>
                      </el-popover>
                    </div>
                    {{ t("config.form.tlsCertFile.label") }}
                  </template>
                  <el-input
                    v-model="formData.transport.tls.certFile"
                    :placeholder="t('config.form.tlsCertFile.placeholder')"
                    clearable
                  />
                  <el-button
                    v-if="formData.transport.tls.certFile"
                    class="ml-2"
                    type="danger"
                    @click="formData.transport.tls.certFile = ''"
                    >{{ t("config.button.clear") }}
                  </el-button>
                </el-form-item>
              </el-col>
              <el-col :span="24">
                <el-form-item
                  :label="t('config.form.tlsKeyFile.label')"
                  prop="transport.tls.keyFile"
                  label-width="180"
                >
                  <template #label>
                    <div class="flex items-center mr-1 h-full">
                      <el-popover width="300" placement="top" trigger="hover">
                        <template #default>
                          {{ t("config.popover.frpParameter") }}:<span
                            class="font-black text-[#5A3DAA]"
                            >transport.tls.keyFile</span
                          >
                        </template>
                        <template #reference>
                          <IconifyIconOffline
                            class="text-base"
                            color="#5A3DAA"
                            icon="info"
                          />
                        </template>
                      </el-popover>
                    </div>
                    {{ t("config.form.tlsKeyFile.label") }}
                  </template>
                  <el-input
                    v-model="formData.transport.tls.keyFile"
                    :placeholder="t('config.form.tlsKeyFile.placeholder')"
                    clearable
                  />
                  <el-button
                    v-if="formData.transport.tls.keyFile"
                    class="ml-2"
                    type="danger"
                    @click="formData.transport.tls.keyFile = ''"
                    >{{ t("config.button.clear") }}
                  </el-button>
                </el-form-item>
              </el-col>
              <el-col :span="24">
                <el-form-item
                  :label="t('config.form.caCertFile.label')"
                  prop="transport.tls.trustedCaFile"
                  label-width="180"
                >
                  <template #label>
                    <div class="flex items-center mr-1 h-full">
                      <el-popover width="300" placement="top" trigger="hover">
                        <template #default>
                          {{ t("config.popover.frpParameter") }}:<span
                            class="font-black text-[#5A3DAA]"
                            >transport.tls.trustedCaFile</span
                          >
                        </template>
                        <template #reference>
                          <IconifyIconOffline
                            class="text-base"
                            color="#5A3DAA"
                            icon="info"
                          />
                        </template>
                      </el-popover>
                    </div>
                    {{ t("config.form.caCertFile.label") }}
                  </template>
                  <el-input
                    v-model="formData.transport.tls.trustedCaFile"
                    :placeholder="t('config.form.caCertFile.placeholder')"
                    clearable
                  />
                  <el-button
                    v-if="formData.transport.tls.trustedCaFile"
                    class="ml-2"
                    type="danger"
                    @click="formData.transport.tls.trustedCaFile = ''"
                    >{{ t("config.button.clear") }}
                  </el-button>
                </el-form-item>
              </el-col>
              <el-col :span="24">
                <el-form-item
                  :label="t('config.form.tlsServerName.label')"
                  prop="tlsConfigServerName"
                  label-width="180"
                >
                  <template #label>
                    <div class="flex items-center mr-1 h-full">
                      <el-popover width="300" placement="top" trigger="hover">
                        <template #default>
                          {{ t("config.popover.frpParameter") }}:<span
                            class="font-black text-[#5A3DAA]"
                            >transport.tls.serverName</span
                          >
                        </template>
                        <template #reference>
                          <IconifyIconOffline
                            class="text-base"
                            color="#5A3DAA"
                            icon="info"
                          />
                        </template>
                      </el-popover>
                    </div>
                    {{ t("config.form.tlsServerName.label") }}
                  </template>
                  <el-input
                    v-model="formData.transport.tls.serverName"
                    :placeholder="t('config.form.tlsServerName.placeholder')"
                    clearable
                  />
                </el-form-item>
              </el-col>
            </template>

            <WebServerConfig v-model="formData" />
            <LogConfig v-model="formData" />
            <SystemConfig
              v-model="formData"
              @language-change="handleSystemLanguageChange"
            />
          </el-row>
        </el-form>
      </div>
    </div>
    <!--  链接导入服务器  -->
    <el-dialog
      v-model="visible.copyServerConfig"
      class="config-link-dialog"
      :title="t('config.dialog.copyLink.title')"
      width="min(720px, calc(100% - 32px))"
      top="5%"
    >
      <el-alert
        class="mb-4"
        :title="t('config.dialog.copyLink.warning.message')"
        type="warning"
        :closable="false"
      />
      <el-input
        v-model="copyServerConfigBase64"
        class="config-link-textarea"
        type="textarea"
        :rows="8"
      ></el-input>
    </el-dialog>
    <!--    链接导出服务器-->
    <el-dialog
      v-model="visible.pasteServerConfig"
      class="config-link-dialog"
      :title="t('config.dialog.importLink.title')"
      width="min(720px, calc(100% - 32px))"
      top="5%"
    >
      <el-input
        v-model="pasteServerConfigBase64"
        class="config-link-textarea"
        type="textarea"
        placeholder="frp://......"
        :rows="8"
      ></el-input>
      <template #footer>
        <div class="dialog-footer">
          <el-button
            plain
            type="primary"
            @click="handlePasteServerConfigBase64"
          >
            <IconifyIconOffline
              class="mr-2 cursor-pointer"
              icon="label-important-rounded"
            />
            {{ t("config.button.import") }}
          </el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<style lang="scss" scoped>
.button-input {
  width: calc(100% - 68px);
}

.config-link-dialog {
  display: flex;
  max-height: 90vh;
  flex-direction: column;

  :deep(.el-dialog__body) {
    min-height: 0;
    overflow-y: auto;
  }
}

.config-link-textarea {
  display: block;
}
</style>
