package com.frpc.android.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.frpc.android.R
import com.frpc.shared.validate.Reason
import com.frpc.shared.validate.Scope
import com.frpc.shared.validate.ValidationIssue

/**
 * 校验问题 → 本地化文案。
 * 字段标签从字段路径末段映射；文案模板见 strings.xml（reason_*）。
 */
fun fieldLabelRes(field: String): Int {
    val key = field.substringAfterLast('.')
    return when (key) {
        "serverAddr" -> R.string.field_server_addr
        "serverPort" -> R.string.field_server_port
        "user" -> R.string.field_user
        "clientID" -> R.string.field_client_id
        "token" -> R.string.field_auth_token
        "method" -> R.string.field_auth_method
        "addr" -> R.string.field_admin_addr
        "port" -> R.string.field_admin_port
        "protocol" -> R.string.field_transport_protocol
        "poolCount" -> R.string.field_pool_count
        "name" -> R.string.field_proxy_name
        "type" -> R.string.field_proxy_type
        "localIP" -> R.string.field_local_ip
        "localPort" -> R.string.field_local_port
        "remotePort" -> R.string.field_remote_port
        "subdomain" -> R.string.field_subdomain
        "customDomains" -> R.string.field_custom_domains
        "secretKey" -> R.string.field_secret_key
        "multiplexer" -> R.string.field_multiplexer
        "serverName" -> R.string.field_server_name
        "level" -> R.string.field_log_level
        else -> R.string.unknown
    }
}

fun reasonTemplateRes(reason: Reason): Int = when (reason) {
    Reason.EMPTY_REQUIRED_FIELD -> R.string.reason_empty_required_field
    Reason.PORT_OUT_OF_RANGE -> R.string.reason_port_out_of_range
    Reason.PORT_IN_USE -> R.string.reason_port_in_use
    Reason.INVALID_VALUE -> R.string.reason_invalid_value
    Reason.DUPLICATE_NAME -> R.string.reason_duplicate_name
    Reason.DOMAIN_OR_SUBDOMAIN_REQUIRED -> R.string.reason_domain_or_subdomain_required
    Reason.UNSUPPORTED_VALUE -> R.string.reason_unsupported_value
    Reason.TYPE_FIELD_MISSING -> R.string.reason_type_field_missing
}

fun ValidationIssue.entityPrefix(): String =
    entityName?.let { "[$it] " } ?: ""

fun ValidationIssue.scopePrefixRes(): Int = when (scope) {
    Scope.GLOBAL -> R.string.field_server_addr
    Scope.AUTH -> R.string.field_auth_method
    Scope.WEB_SERVER -> R.string.field_admin_enabled
    Scope.TRANSPORT -> R.string.field_transport_protocol
    Scope.LOG -> R.string.field_log_level
    Scope.PROXY -> R.string.proxies_title
    Scope.VISITOR -> R.string.visitors_title
}

/** 校验问题 → 本地化文案 */
@Composable
fun validationMessage(issue: ValidationIssue): String {
    val fieldLabel = stringResource(fieldLabelRes(issue.field))
    return stringResource(reasonTemplateRes(issue.reason), fieldLabel)
}

/** 从 "proxies[0].remotePort" 这类路径中取出表单字段键（"remotePort"） */
fun ValidationIssue.formFieldKey(): String =
    field.substringAfter("].", field)
