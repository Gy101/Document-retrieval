package com.doccheck.common;

/**
 * 当前登录用户上下文。由 AuthInterceptor 写入与清理，避免 Controller 层层传递 userId。
 */
public final class UserContext {

    private static final ThreadLocal<LoginUser> HOLDER = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(LoginUser user) {
        HOLDER.set(user);
    }

    public static LoginUser get() {
        return HOLDER.get();
    }

    public static Long userId() {
        LoginUser user = HOLDER.get();
        if (user == null) {
            throw new BizException(401, "登录状态已失效，请重新登录");
        }
        return user.id();
    }

    public static boolean isAdmin() {
        LoginUser user = HOLDER.get();
        return user != null && "ADMIN".equalsIgnoreCase(user.role());
    }

    public static void clear() {
        HOLDER.remove();
    }

    public record LoginUser(Long id, String username, String displayName, String role) {
    }
}
