# THIS FILE IS AUTO-GENERATED. DO NOT MODIFY!!

# Copyright 2020-2023 Tauri Programme within The Commons Conservancy
# SPDX-License-Identifier: Apache-2.0
# SPDX-License-Identifier: MIT

-keep class priv.fdmdf.frpc.android.* {
  native <methods>;
}

-keep class priv.fdmdf.frpc.android.WryActivity {
  public <init>(...);

  void setWebView(priv.fdmdf.frpc.android.RustWebView);
  java.lang.Class getAppClass(...);
  int getId();
  java.lang.String getVersion();
  int startActivity(...);
}

-keep class priv.fdmdf.frpc.android.Ipc {
  public <init>(...);

  @android.webkit.JavascriptInterface public <methods>;
}

-keep class priv.fdmdf.frpc.android.RustWebView {
  public <init>(...);

  void loadUrlMainThread(...);
  void loadHTMLMainThread(...);
  void evalScript(...);
}

-keep class priv.fdmdf.frpc.android.RustWebChromeClient,priv.fdmdf.frpc.android.RustWebViewClient {
  public <init>(...);
}
