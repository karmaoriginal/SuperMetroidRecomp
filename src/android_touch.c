/* Android touch-controller bridge.
 *
 * Calling SDLActivity.onNativeKeyDown() with an Android DPAD key relies on
 * SDL's Java keycode translation and is device/OEM dependent. Push explicit
 * SDL key events instead: the desktop host then sees exactly the same keycodes
 * as its default SNES keyboard bindings, including modal overlay navigation.
 */
#ifdef __ANDROID__
#include <jni.h>
#include <android/keycodes.h>
#include <string.h>

#include "desktop/sdl_compat.h"

static SDL_Keycode SmAndroidTouchKey(int android_key) {
  switch (android_key) {
  case AKEYCODE_DPAD_UP:    return SDLK_UP;
  case AKEYCODE_DPAD_DOWN:  return SDLK_DOWN;
  case AKEYCODE_DPAD_LEFT:  return SDLK_LEFT;
  case AKEYCODE_DPAD_RIGHT: return SDLK_RIGHT;
  case AKEYCODE_A:          return SDLK_a;       /* SNES Y */
  case AKEYCODE_S:          return SDLK_s;       /* SNES X */
  case AKEYCODE_Z:          return SDLK_z;       /* SNES B */
  case AKEYCODE_X:          return SDLK_x;       /* SNES A */
  case AKEYCODE_C:          return SDLK_c;       /* SNES L */
  case AKEYCODE_V:          return SDLK_v;       /* SNES R */
  case AKEYCODE_SHIFT_RIGHT:return SDLK_RSHIFT;  /* Select */
  case AKEYCODE_ENTER:      return SDLK_RETURN;  /* Start */
  default:                  return SDLK_UNKNOWN;
  }
}

JNIEXPORT void JNICALL
Java_com_snesrecomp_supermetroid_TouchControlsView_nativeSetKey(
    JNIEnv *env, jclass clazz, jint android_key, jboolean pressed) {
  (void)env;
  (void)clazz;
  SDL_Keycode key = SmAndroidTouchKey((int)android_key);
  if (key == SDLK_UNKNOWN) return;

  SDL_Event event;
  memset(&event, 0, sizeof(event));
  event.type = pressed ? SDL_KEYDOWN : SDL_KEYUP;
  event.key.type = event.type;
  event.key.state = pressed ? SDL_PRESSED : SDL_RELEASED;
  event.key.repeat = 0;
  event.key.keysym.sym = key;
  event.key.keysym.scancode = SDL_GetScancodeFromKey(key);
  event.key.keysym.mod = KMOD_NONE;
  SDL_PushEvent(&event);
}
#endif
