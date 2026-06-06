package com.chuamgwei.common;

/**
 * 请求上下文（ThreadLocal），存储当前请求的操作人身份信息
 */
public class RequestContext {

    private static final ThreadLocal<String> OPERATOR_UUID = new ThreadLocal<>();
    private static final ThreadLocal<String> OPERATOR_NAME = new ThreadLocal<>();
    private static final ThreadLocal<Integer> ADMIN_PERMISSIONS = new ThreadLocal<>();

    public static void setOperatorUuid(String uuid) {
        OPERATOR_UUID.set(uuid);
    }

    public static void setOperatorName(String name) {
        OPERATOR_NAME.set(name);
    }

    public static void setAdminPermissions(Integer adminPermissions) {
        ADMIN_PERMISSIONS.set(adminPermissions);
    }

    public static String getOperatorUuid() {
        return OPERATOR_UUID.get();
    }

    public static String getOperatorName() {
        return OPERATOR_NAME.get();
    }

    public static Integer getAdminPermissions() {
        return ADMIN_PERMISSIONS.get();
    }

    public static boolean isAdmin() {
        Integer adminPermissions = ADMIN_PERMISSIONS.get();
        return adminPermissions != null && adminPermissions >= 2;
    }

    /** 请求结束后必须清理，防止内存泄漏 */
    public static void clear() {
        OPERATOR_UUID.remove();
        OPERATOR_NAME.remove();
        ADMIN_PERMISSIONS.remove();
    }
}