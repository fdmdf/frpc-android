# THIS FILE IS AUTO-GENERATED. DO NOT MODIFY!!

# Copyright 2020-2023 Tauri Programme within The Commons Conservancy
# SPDX-License-Identifier: Apache-2.0
# SPDX-License-Identifier: MIT

-keep class priv.liujiawei.frpc.desktop.* {
  native <methods>;
}

-keep class priv.liujiawei.frpc.desktop.WryActivity {
  public <init>(...);

  void setWebView(priv.liujiawei.frpc.desktop.RustWebView);
  java.lang.Class getAppClass(...);
  int getId();
  java.lang.String getVersion();
  int startActivity(...);
}

-keep class priv.liujiawei.frpc.desktop.Ipc {
  public <init>(...);

  @android.webkit.JavascriptInterface public <methods>;
}

-keep class priv.liujiawei.frpc.desktop.RustWebView {
  public <init>(...);

  void loadUrlMainThread(...);
  void loadHTMLMainThread(...);
  void evalScript(...);
}

-keep class priv.liujiawei.frpc.desktop.RustWebChromeClient,priv.liujiawei.frpc.desktop.RustWebViewClient {
  public <init>(...);
}
