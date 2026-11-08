// Bedrock Client - render hooks.
//
// Version independent: we never touch libminecraftpe.so internals. Instead we hook the
// system graphics libraries (libGLESv2/v3, libEGL, libvulkan) that every Minecraft
// version calls, so a game update does not break the client.
//
//  * Zoom (real / FOV): scale the projection matrices the game uploads to its shaders.
//    Only perspective matrices are touched, so the 2D UI/HUD is NOT zoomed.
//  * Zoom (digital): enlarge the final pass that draws to the screen.
//  * FPS: count presented frames (eglSwapBuffers / vkQueuePresentKHR).

#define _GNU_SOURCE
#include <jni.h>
#include <android/log.h>
#include <math.h>
#include <pthread.h>
#include <stdatomic.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>
#include <time.h>

#include "shadowhook.h"

#define TAG "BClient"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define EXPORT __attribute__((visibility("default")))

#ifndef SHADOWHOOK_ERRNO_PENDING
#define SHADOWHOOK_ERRNO_PENDING 1
#endif

// ----------------------------------------------------------------- minimal GL/EGL/VK types
typedef unsigned int GLenum;
typedef unsigned int GLuint;
typedef int GLint;
typedef int GLsizei;
typedef float GLfloat;
typedef unsigned char GLboolean;
typedef char GLchar;
typedef unsigned int GLbitfield;
typedef void *EGLDisplay;
typedef void *EGLSurface;
typedef unsigned int EGLBoolean;
typedef int32_t EGLint;
typedef int32_t VkResult;
typedef void *VkQueue;
typedef void *VkDevice;
typedef void *VkInstance;
typedef void (*voidfn)(void);

#define GL_FRAMEBUFFER 0x8D40
#define GL_DRAW_FRAMEBUFFER 0x8CA9
#define GL_COLOR_BUFFER_BIT 0x00004000
#define GL_LINEAR 0x2601

// ----------------------------------------------------------------- state
static volatile float g_target = 1.0f;   // requested zoom
static volatile float g_zoom = 1.0f;     // current (animated) zoom
static volatile int g_mode = 0;          // 0 = real (projection), 1 = digital
static volatile int g_smooth = 1;
static volatile int g_backend = 0;       // 1 GL, 2 VK
static atomic_ullong g_frames;
static struct timespec g_last;

static __thread GLuint t_prog = 0;
static __thread GLuint t_draw_fbo = 0;
static __thread GLint t_vp[4];

static void frame_tick(int backend) {
    atomic_fetch_add_explicit(&g_frames, 1, memory_order_relaxed);
    g_backend = backend;
    struct timespec now;
    clock_gettime(CLOCK_MONOTONIC, &now);
    float dt = (float) (now.tv_sec - g_last.tv_sec) + (float) (now.tv_nsec - g_last.tv_nsec) / 1e9f;
    g_last = now;
    if (dt <= 0.0f || dt > 0.5f) dt = 0.016f;
    float t = g_target, z = g_zoom;
    if (!g_smooth) {
        z = t;
    } else {
        float a = 1.0f - expf(-dt * 14.0f);
        z += (t - z) * a;
        if (fabsf(t - z) < 0.004f) z = t;
    }
    g_zoom = z;
}

// ----------------------------------------------------------------- (program, location) set
#define SET_CAP 16384
static atomic_ullong g_set[SET_CAP];

static inline unsigned long long mkkey(GLuint prog, GLint loc) {
    return ((unsigned long long) prog << 32) | (uint32_t) loc;
}

static inline uint32_t hkey(unsigned long long k) {
    k ^= k >> 33;
    k *= 0xff51afd7ed558ccdULL;
    k ^= k >> 33;
    return (uint32_t) k;
}

static void set_add(unsigned long long k) {
    uint32_t i = hkey(k) & (SET_CAP - 1);
    for (int n = 0; n < 256; n++) {
        unsigned long long cur = atomic_load(&g_set[i]);
        if (cur == k) return;
        if (cur == 0) {
            unsigned long long zero = 0;
            if (atomic_compare_exchange_strong(&g_set[i], &zero, k)) return;
            if (zero == k) return;
        }
        i = (i + 1) & (SET_CAP - 1);
    }
}

static int set_has(unsigned long long k) {
    uint32_t i = hkey(k) & (SET_CAP - 1);
    for (int n = 0; n < 256; n++) {
        unsigned long long cur = atomic_load_explicit(&g_set[i], memory_order_relaxed);
        if (cur == k) return 1;
        if (cur == 0) return 0;
        i = (i + 1) & (SET_CAP - 1);
    }
    return 0;
}

static int ci_has(const char *s, const char *sub) {
    size_t n = strlen(sub);
    for (; *s; s++) {
        size_t i = 0;
        while (i < n && s[i]) {
            char a = s[i], b = sub[i];
            if (a >= 'A' && a <= 'Z') a += 32;
            if (b >= 'A' && b <= 'Z') b += 32;
            if (a != b) break;
            i++;
        }
        if (i == n) return 1;
    }
    return 0;
}

// Uniforms that carry a camera projection (bgfx names: u_proj, u_viewProj, u_modelViewProj ...)
static int is_proj_name(const char *n) {
    if (!n || !ci_has(n, "proj")) return 0;
    if (ci_has(n, "inv") || ci_has(n, "prev") || ci_has(n, "shadow") || ci_has(n, "light") ||
        ci_has(n, "tex") || ci_has(n, "jitter") || ci_has(n, "sun") || ci_has(n, "cascade"))
        return 0;
    return 1;
}

// S * M with S = diag(z, z, 1, 1)  ==  narrower field of view, same centre.
static void zoom_mat(GLfloat *m, GLboolean tr, float z) {
#define E(r, c) m[tr ? ((r) * 4 + (c)) : ((c) * 4 + (r))]
    // orthographic / affine (2D UI) has w-row = (0,0,0,1): leave it alone
    if (fabsf(E(3, 0)) < 1e-6f && fabsf(E(3, 1)) < 1e-6f && fabsf(E(3, 2)) < 1e-6f) return;
    for (int c = 0; c < 4; c++) {
        E(0, c) *= z;
        E(1, c) *= z;
    }
#undef E
}

// ----------------------------------------------------------------- GL hooks
static void (*o_glUseProgram)(GLuint);
static GLint (*o_glGetUniformLocation)(GLuint, const GLchar *);
static void (*o_glUniformMatrix4fv)(GLint, GLsizei, GLboolean, const GLfloat *);
static void (*o_glProgramUniformMatrix4fv)(GLuint, GLint, GLsizei, GLboolean, const GLfloat *);
static void (*o_glBindFramebuffer)(GLenum, GLuint);
static void (*o_glViewport)(GLint, GLint, GLsizei, GLsizei);
static void (*o_glScissor)(GLint, GLint, GLsizei, GLsizei);
static void (*o_glBlitFramebuffer)(GLint, GLint, GLint, GLint, GLint, GLint, GLint, GLint, GLbitfield, GLenum);

static void p_glUseProgram(GLuint prog) {
    t_prog = prog;
    o_glUseProgram(prog);
}

static GLint p_glGetUniformLocation(GLuint prog, const GLchar *name) {
    GLint loc = o_glGetUniformLocation(prog, name);
    if (loc >= 0 && prog != 0 && is_proj_name(name)) set_add(mkkey(prog, loc));
    return loc;
}

static int want_matrix(GLuint prog, GLint loc, GLsizei cnt, const GLfloat *v) {
    return g_mode == 0 && g_zoom > 1.001f && loc >= 0 && v && cnt > 0 && cnt <= 8 && set_has(mkkey(prog, loc));
}

static void p_glUniformMatrix4fv(GLint loc, GLsizei cnt, GLboolean tr, const GLfloat *v) {
    if (want_matrix(t_prog, loc, cnt, v)) {
        GLfloat buf[16 * 8];
        float z = g_zoom;
        memcpy(buf, v, sizeof(GLfloat) * 16 * (size_t) cnt);
        for (int i = 0; i < cnt; i++) zoom_mat(buf + i * 16, tr, z);
        o_glUniformMatrix4fv(loc, cnt, tr, buf);
        return;
    }
    o_glUniformMatrix4fv(loc, cnt, tr, v);
}

static void p_glProgramUniformMatrix4fv(GLuint prog, GLint loc, GLsizei cnt, GLboolean tr, const GLfloat *v) {
    if (want_matrix(prog, loc, cnt, v)) {
        GLfloat buf[16 * 8];
        float z = g_zoom;
        memcpy(buf, v, sizeof(GLfloat) * 16 * (size_t) cnt);
        for (int i = 0; i < cnt; i++) zoom_mat(buf + i * 16, tr, z);
        o_glProgramUniformMatrix4fv(prog, loc, cnt, tr, buf);
        return;
    }
    o_glProgramUniformMatrix4fv(prog, loc, cnt, tr, v);
}

static void p_glBindFramebuffer(GLenum target, GLuint fb) {
    if (target == GL_FRAMEBUFFER || target == GL_DRAW_FRAMEBUFFER) t_draw_fbo = fb;
    o_glBindFramebuffer(target, fb);
}

static int digital_on(void) { return g_mode == 1 && g_zoom > 1.001f && t_draw_fbo == 0; }

static void p_glViewport(GLint x, GLint y, GLsizei w, GLsizei h) {
    t_vp[0] = x; t_vp[1] = y; t_vp[2] = w; t_vp[3] = h;
    if (digital_on() && w > 0 && h > 0) {
        float z = g_zoom;
        float zmax = 8192.0f / (float) (w > h ? w : h);   // stay under GL_MAX_VIEWPORT_DIMS
        if (z > zmax) z = zmax;
        GLsizei nw = (GLsizei) (w * z), nh = (GLsizei) (h * z);
        o_glViewport(x - (nw - w) / 2, y - (nh - h) / 2, nw, nh);
        return;
    }
    o_glViewport(x, y, w, h);
}

static void p_glScissor(GLint x, GLint y, GLsizei w, GLsizei h) {
    if (digital_on() && t_vp[2] > 0 && t_vp[3] > 0) {
        float z = g_zoom;
        float cx = t_vp[0] + t_vp[2] * 0.5f, cy = t_vp[1] + t_vp[3] * 0.5f;
        o_glScissor((GLint) (cx + (x - cx) * z), (GLint) (cy + (y - cy) * z), (GLsizei) (w * z), (GLsizei) (h * z));
        return;
    }
    o_glScissor(x, y, w, h);
}

static void p_glBlitFramebuffer(GLint sx0, GLint sy0, GLint sx1, GLint sy1, GLint dx0, GLint dy0, GLint dx1,
                                GLint dy1, GLbitfield mask, GLenum filter) {
    if (digital_on() && mask == GL_COLOR_BUFFER_BIT) {
        float z = g_zoom;
        float cx = (sx0 + sx1) * 0.5f, cy = (sy0 + sy1) * 0.5f;
        float hw = (sx1 - sx0) * 0.5f / z, hh = (sy1 - sy0) * 0.5f / z;
        o_glBlitFramebuffer((GLint) (cx - hw), (GLint) (cy - hh), (GLint) (cx + hw), (GLint) (cy + hh),
                            dx0, dy0, dx1, dy1, mask, GL_LINEAR);
        return;
    }
    o_glBlitFramebuffer(sx0, sy0, sx1, sy1, dx0, dy0, dx1, dy1, mask, filter);
}

// ----------------------------------------------------------------- EGL hooks
static EGLBoolean (*o_eglSwapBuffers)(EGLDisplay, EGLSurface);
static EGLBoolean (*o_eglSwapDmgKHR)(EGLDisplay, EGLSurface, EGLint *, EGLint);
static EGLBoolean (*o_eglSwapDmgEXT)(EGLDisplay, EGLSurface, EGLint *, EGLint);
static voidfn (*o_eglGetProcAddress)(const char *);

static EGLBoolean p_eglSwapBuffers(EGLDisplay d, EGLSurface s) {
    frame_tick(1);
    return o_eglSwapBuffers(d, s);
}

static EGLBoolean p_eglSwapDmgKHR(EGLDisplay d, EGLSurface s, EGLint *r, EGLint n) {
    frame_tick(1);
    return o_eglSwapDmgKHR(d, s, r, n);
}

static EGLBoolean p_eglSwapDmgEXT(EGLDisplay d, EGLSurface s, EGLint *r, EGLint n) {
    frame_tick(1);
    return o_eglSwapDmgEXT(d, s, r, n);
}

typedef struct {
    const char *name;
    void *proxy;
    void **orig;
} entry;

static entry k_gl[] = {
        {"glUseProgram", (void *) p_glUseProgram, (void **) &o_glUseProgram},
        {"glGetUniformLocation", (void *) p_glGetUniformLocation, (void **) &o_glGetUniformLocation},
        {"glUniformMatrix4fv", (void *) p_glUniformMatrix4fv, (void **) &o_glUniformMatrix4fv},
        {"glProgramUniformMatrix4fv", (void *) p_glProgramUniformMatrix4fv, (void **) &o_glProgramUniformMatrix4fv},
        {"glBindFramebuffer", (void *) p_glBindFramebuffer, (void **) &o_glBindFramebuffer},
        {"glViewport", (void *) p_glViewport, (void **) &o_glViewport},
        {"glScissor", (void *) p_glScissor, (void **) &o_glScissor},
        {"glBlitFramebuffer", (void *) p_glBlitFramebuffer, (void **) &o_glBlitFramebuffer},
};
#define N_GL (sizeof(k_gl) / sizeof(k_gl[0]))

static entry k_egl_ext[] = {
        {"eglSwapBuffersWithDamageKHR", (void *) p_eglSwapDmgKHR, (void **) &o_eglSwapDmgKHR},
        {"eglSwapBuffersWithDamageEXT", (void *) p_eglSwapDmgEXT, (void **) &o_eglSwapDmgEXT},
};

// Functions fetched through eglGetProcAddress bypass the libGLESv2 exports, so wrap them too.
static voidfn p_eglGetProcAddress(const char *name) {
    voidfn r = o_eglGetProcAddress(name);
    if (!r || !name) return r;
    for (size_t i = 0; i < N_GL; i++) {
        if (strcmp(name, k_gl[i].name) == 0) {
            if (*k_gl[i].orig == NULL) *k_gl[i].orig = (void *) r;
            return (voidfn) k_gl[i].proxy;
        }
    }
    for (size_t i = 0; i < 2; i++) {
        if (strcmp(name, k_egl_ext[i].name) == 0) {
            *k_egl_ext[i].orig = (void *) r;
            return (voidfn) k_egl_ext[i].proxy;
        }
    }
    return r;
}

// ----------------------------------------------------------------- Vulkan (FPS only)
static VkResult (*o_vkQueuePresentKHR)(VkQueue, const void *);
static VkResult (*o_vkQueuePresentKHR_dev)(VkQueue, const void *);
static voidfn (*o_vkGetDeviceProcAddr)(VkDevice, const char *);
static voidfn (*o_vkGetInstanceProcAddr)(VkInstance, const char *);

static VkResult p_vkQueuePresentKHR(VkQueue q, const void *info) {
    frame_tick(2);
    return o_vkQueuePresentKHR(q, info);
}

static VkResult p_vkQueuePresentKHR_dev(VkQueue q, const void *info) {
    frame_tick(2);
    return o_vkQueuePresentKHR_dev(q, info);
}

static voidfn wrap_vk(const char *n, voidfn r) {
    if (r && n && strcmp(n, "vkQueuePresentKHR") == 0 && (void *) r != (void *) p_vkQueuePresentKHR_dev &&
        (void *) r != (void *) p_vkQueuePresentKHR) {
        o_vkQueuePresentKHR_dev = (VkResult (*)(VkQueue, const void *)) r;
        return (voidfn) p_vkQueuePresentKHR_dev;
    }
    return r;
}

static voidfn p_vkGetDeviceProcAddr(VkDevice d, const char *n) { return wrap_vk(n, o_vkGetDeviceProcAddr(d, n)); }
static voidfn p_vkGetInstanceProcAddr(VkInstance i, const char *n) { return wrap_vk(n, o_vkGetInstanceProcAddr(i, n)); }

// ----------------------------------------------------------------- install
static char g_status[512];

static int hook(const char *lib, const char *sym, void *proxy, void **orig, int *pending) {
    void *stub = shadowhook_hook_sym_name(lib, sym, proxy, orig);
    int err = shadowhook_get_errno();
    if (stub == NULL) {
        LOGI("hook %s!%s failed: %d %s", lib, sym, err, shadowhook_to_errmsg(err));
        return 0;
    }
    if (err == SHADOWHOOK_ERRNO_PENDING && pending) (*pending)++;
    return 1;
}

static void hook_gl_lib(const char *lib, char *out, size_t outsz) {
    int ok = 0, pend = 0;
    for (size_t i = 0; i < N_GL; i++) ok += hook(lib, k_gl[i].name, k_gl[i].proxy, k_gl[i].orig, &pend);
    snprintf(out, outsz, "%d/%d%s", ok, (int) N_GL, pend ? "~" : "");
}

EXPORT JNIEXPORT jstring JNICALL Java_com_yamn_bclient_core_Native_init(JNIEnv *env, jclass cls) {
    (void) cls;
    clock_gettime(CLOCK_MONOTONIC, &g_last);
    int r = shadowhook_init(SHADOWHOOK_MODE_UNIQUE, false);
    if (r != 0) {
        snprintf(g_status, sizeof(g_status), "shadowhook init failed (%d)", r);
        return (*env)->NewStringUTF(env, g_status);
    }
    char gl2[16], gl3[16];
    hook_gl_lib("libGLESv2.so", gl2, sizeof(gl2));
    hook_gl_lib("libGLESv3.so", gl3, sizeof(gl3));

    int egl = 0;
    egl += hook("libEGL.so", "eglSwapBuffers", (void *) p_eglSwapBuffers, (void **) &o_eglSwapBuffers, NULL);
    egl += hook("libEGL.so", "eglGetProcAddress", (void *) p_eglGetProcAddress, (void **) &o_eglGetProcAddress, NULL);

    int vk = 0;
    vk += hook("libvulkan.so", "vkQueuePresentKHR", (void *) p_vkQueuePresentKHR, (void **) &o_vkQueuePresentKHR, NULL);
    vk += hook("libvulkan.so", "vkGetDeviceProcAddr", (void *) p_vkGetDeviceProcAddr, (void **) &o_vkGetDeviceProcAddr, NULL);
    vk += hook("libvulkan.so", "vkGetInstanceProcAddr", (void *) p_vkGetInstanceProcAddr, (void **) &o_vkGetInstanceProcAddr, NULL);

    snprintf(g_status, sizeof(g_status), "GLv2 %s, GLv3 %s, EGL %d/2, VK %d/3", gl2, gl3, egl, vk);
    LOGI("%s", g_status);
    return (*env)->NewStringUTF(env, g_status);
}

EXPORT JNIEXPORT void JNICALL Java_com_yamn_bclient_core_Native_setZoom(JNIEnv *env, jclass cls, jfloat z) {
    (void) env; (void) cls;
    if (z < 1.0f) z = 1.0f;
    if (z > 30.0f) z = 30.0f;
    g_target = z;
}

EXPORT JNIEXPORT void JNICALL Java_com_yamn_bclient_core_Native_setMode(JNIEnv *env, jclass cls, jint m) {
    (void) env; (void) cls;
    g_mode = m == 1 ? 1 : 0;
}

EXPORT JNIEXPORT void JNICALL Java_com_yamn_bclient_core_Native_setSmooth(JNIEnv *env, jclass cls, jboolean s) {
    (void) env; (void) cls;
    g_smooth = s ? 1 : 0;
}

EXPORT JNIEXPORT jlong JNICALL Java_com_yamn_bclient_core_Native_frames(JNIEnv *env, jclass cls) {
    (void) env; (void) cls;
    return (jlong) atomic_load(&g_frames);
}

EXPORT JNIEXPORT jint JNICALL Java_com_yamn_bclient_core_Native_backend(JNIEnv *env, jclass cls) {
    (void) env; (void) cls;
    return g_backend;
}
