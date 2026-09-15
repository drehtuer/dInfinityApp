// The JNI surface of the physics bridge.
//
// It does three things and no more: unpack a float array, call one method on
// `dinfinity::World`, pack a float array. There is no logic here to test,
// which is the point — everything that could be wrong about a roll lives in
// Kotlin, on the JVM, where a test can reach it (`docs/architecture.md`,
// decision 40).
//
// Arrays rather than objects on purpose. A roll is 1,440 steps of up to eighty
// dice; allocating a Java object per die per step would cost more than the
// physics.

#include "world.h"

#include <jni.h>

#include <cstdint>
#include <vector>

namespace {

dinfinity::World* AsWorld(jlong handle) {
  return reinterpret_cast<dinfinity::World*>(static_cast<std::uintptr_t>(handle));
}

/// Reads a placement out of the 13 floats Kotlin packs it into: position,
/// rotation as w-x-y-z, linear velocity, angular velocity.
dinfinity::Placement ReadPlacement(const jfloat* values) {
  dinfinity::Placement placement{};
  for (int i = 0; i < 3; ++i) placement.position[i] = values[i];
  for (int i = 0; i < 4; ++i) placement.rotation[i] = values[3 + i];
  for (int i = 0; i < 3; ++i) placement.linear_velocity[i] = values[7 + i];
  for (int i = 0; i < 3; ++i) placement.angular_velocity[i] = values[10 + i];
  return placement;
}

}  // namespace

extern "C" {

JNIEXPORT jlong JNICALL
Java_de_drehtuer_dinfinity_simulation_jolt_JoltNative_nativeCreateWorld(JNIEnv* env, jobject,
                                                                       jfloatArray tray,
                                                                       jint max_dice) {
  jfloat values[7] = {};
  env->GetFloatArrayRegion(tray, 0, 7, values);

  dinfinity::TraySpec spec{};
  spec.half_long = values[0];
  spec.half_short = values[1];
  spec.wall_height = values[2];
  spec.ceiling_height = values[3];
  spec.corner_radius = values[4];
  spec.friction = values[5];
  spec.restitution = values[6];

  auto* world = new dinfinity::World(spec, max_dice);
  return static_cast<jlong>(reinterpret_cast<std::uintptr_t>(world));
}

JNIEXPORT jboolean JNICALL
Java_de_drehtuer_dinfinity_simulation_jolt_JoltNative_nativeAddDie(
    JNIEnv* env, jobject, jlong handle, jfloatArray hull, jfloat convex_radius, jfloat density,
    jfloat friction, jfloat restitution, jfloatArray placement) {
  dinfinity::World* world = AsWorld(handle);
  if (world == nullptr) return JNI_FALSE;

  const jsize length = env->GetArrayLength(hull);
  std::vector<float> points(static_cast<std::size_t>(length));
  env->GetFloatArrayRegion(hull, 0, length, points.data());

  jfloat placement_values[13] = {};
  env->GetFloatArrayRegion(placement, 0, 13, placement_values);

  dinfinity::DieSpec spec{};
  spec.hull_points = points.data();
  spec.point_count = static_cast<int>(length / 3);
  spec.convex_radius = convex_radius;
  spec.density = density;
  spec.friction = friction;
  spec.restitution = restitution;

  world->AddDie(spec, ReadPlacement(placement_values));
  return world->Ok() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL Java_de_drehtuer_dinfinity_simulation_jolt_JoltNative_nativeFinish(
    JNIEnv*, jobject, jlong handle) {
  AsWorld(handle)->Finish();
}

JNIEXPORT void JNICALL Java_de_drehtuer_dinfinity_simulation_jolt_JoltNative_nativeSetGravity(
    JNIEnv*, jobject, jlong handle, jfloat x, jfloat y, jfloat z) {
  AsWorld(handle)->SetGravity(x, y, z);
}

JNIEXPORT void JNICALL Java_de_drehtuer_dinfinity_simulation_jolt_JoltNative_nativeStep(
    JNIEnv*, jobject, jlong handle, jfloat dt) {
  AsWorld(handle)->Step(dt);
}

JNIEXPORT void JNICALL Java_de_drehtuer_dinfinity_simulation_jolt_JoltNative_nativeReadStates(
    JNIEnv* env, jobject, jlong handle, jfloatArray out) {
  const jsize length = env->GetArrayLength(out);
  std::vector<float> states(static_cast<std::size_t>(length));
  AsWorld(handle)->ReadStates(states.data());
  env->SetFloatArrayRegion(out, 0, length, states.data());
}

JNIEXPORT jfloat JNICALL
Java_de_drehtuer_dinfinity_simulation_jolt_JoltNative_nativeDeepestPenetration(JNIEnv*, jobject,
                                                                              jlong handle) {
  dinfinity::World* world = AsWorld(handle);
  return world == nullptr ? 0.0f : world->DeepestDiePenetration();
}

JNIEXPORT void JNICALL Java_de_drehtuer_dinfinity_simulation_jolt_JoltNative_nativeApplyBias(
    JNIEnv*, jobject, jlong handle, jint index, jfloat x, jfloat y, jfloat z) {
  AsWorld(handle)->ApplyBias(index, x, y, z);
}

JNIEXPORT void JNICALL Java_de_drehtuer_dinfinity_simulation_jolt_JoltNative_nativeRespawn(
    JNIEnv* env, jobject, jlong handle, jint index, jfloatArray placement) {
  jfloat values[13] = {};
  env->GetFloatArrayRegion(placement, 0, 13, values);
  AsWorld(handle)->Respawn(index, ReadPlacement(values));
}

JNIEXPORT void JNICALL
Java_de_drehtuer_dinfinity_simulation_jolt_JoltNative_nativeReadBody(JNIEnv* env, jobject,
                                                                    jlong handle, jint index,
                                                                    jfloatArray out) {
  dinfinity::World* world = AsWorld(handle);
  if (world == nullptr) return;

  float values[dinfinity::kBodyStride] = {};
  world->ReadBody(index, values);
  env->SetFloatArrayRegion(out, 0, dinfinity::kBodyStride, values);
}

JNIEXPORT jint JNICALL
Java_de_drehtuer_dinfinity_simulation_jolt_JoltNative_nativeReadFaces(JNIEnv* env, jobject,
                                                                     jlong handle, jint index,
                                                                     jfloatArray out) {
  dinfinity::World* world = AsWorld(handle);
  if (world == nullptr) return 0;

  const jsize length = env->GetArrayLength(out);
  const int capacity = static_cast<int>(length) / dinfinity::kPlaneStride;
  std::vector<float> values(static_cast<std::size_t>(capacity * dinfinity::kPlaneStride));
  const int faces = world->ReadFaces(index, values.data(), capacity);
  if (!values.empty()) {
    env->SetFloatArrayRegion(out, 0, static_cast<jsize>(values.size()), values.data());
  }
  return faces;
}

JNIEXPORT jboolean JNICALL Java_de_drehtuer_dinfinity_simulation_jolt_JoltNative_nativeOk(
    JNIEnv*, jobject, jlong handle) {
  return AsWorld(handle)->Ok() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL Java_de_drehtuer_dinfinity_simulation_jolt_JoltNative_nativeDestroy(
    JNIEnv*, jobject, jlong handle) {
  delete AsWorld(handle);
}

}  // extern "C"
