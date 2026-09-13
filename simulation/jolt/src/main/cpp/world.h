// A tray with dice in it, and nothing that decides anything.
//
// This is deliberately the dumbest half of the bridge. It creates bodies,
// advances them by a fixed step, and reports what they are doing; every
// judgement — when a die has stopped, whether it may be touched, what it read,
// whether to throw it again — is made in Kotlin, where it can be tested on the
// JVM without a device (`docs/architecture.md`, decision 40).
//
// Units are centimetres, grams and seconds. That is not a whim: at metre scale
// a die's inertia tensor falls under a hard-coded "near zero" test inside Jolt
// and is silently replaced by that of a sphere a metre across, which no amount
// of friction can stop turning. The millimetres the rest of the app speaks are
// converted once, on the Kotlin side of the JNI boundary.

#pragma once

#include <cstdint>

namespace dinfinity {

/// How many floats `World::ReadStates` writes per die.
inline constexpr int kStateStride = 10;

/// Bits of the contact word in a die's state.
enum ContactFlags : std::uint32_t {
  kTouchingFloor = 1u << 0,
  kTouchingWall = 1u << 1,
  /// Resting on another die — rung 2 of the correction ladder's first question.
  kSupportedByDie = 1u << 2,
};

/// The tray, in simulation units. Its mesh is fixed; only friction and
/// restitution come from the selected table (`docs/tables.md`).
struct TraySpec {
  float half_long;
  float half_short;
  float wall_height;
  float ceiling_height;
  float corner_radius;
  float friction;
  float restitution;
};

/// One die's body, as the shape catalogue and the set file describe it.
struct DieSpec {
  /// Hull corners as xyz triples in the die's own space.
  const float* hull_points;
  int point_count;
  /// The small margin that lets a sharp solid tumble instead of catching.
  float convex_radius;
  /// Grams per cubic centimetre, which is what a set file quotes and what the
  /// solver's units want — so it needs no conversion at all.
  float density;
  float friction;
  float restitution;
};

/// Where a die starts, or restarts: pose and the motion it is thrown with, in
/// simulation units and radians.
struct Placement {
  float position[3];
  /// w, x, y, z.
  float rotation[4];
  float linear_velocity[3];
  float angular_velocity[3];
};

/// One roll's world. Created per throw and destroyed with it.
class World {
 public:
  World(const TraySpec& tray, int max_dice);
  ~World();

  World(const World&) = delete;
  World& operator=(const World&) = delete;

  /// True when the tray and every die asked for was actually created. A hull
  /// that Jolt refuses is reported rather than silently producing a roll with
  /// a die missing.
  bool Ok() const { return ok_; }

  /// Adds one die. Dice are added in throw order and keep that index for the
  /// life of the world, because that is what the outcome is reported against.
  void AddDie(const DieSpec& die, const Placement& placement);

  /// Called once after the last die: builds the broad phase in one go rather
  /// than incrementally, which is both faster and — because the tree is then
  /// built from a fixed set rather than an insertion order — one less thing
  /// for determinism to depend on.
  void Finish();

  /// Which way down is, and how hard. The only thing a shake changes: the
  /// tray is the screen and never moves, so the hand reaches the dice as an
  /// inverse acceleration folded into gravity.
  void SetGravity(float x, float y, float z);

  /// Advances by exactly `dt`, always. Never a frame time.
  void Step(float dt);

  /// Writes `kStateStride` floats per die: position xyz, rotation wxyz, speed,
  /// spin, contact flags.
  void ReadStates(float* out) const;

  /// Adds `v` to a die's velocity — rung 2 of the ladder. Whether this die may
  /// be touched at all was decided in Kotlin by `CorrectionLadder.mayTouch`.
  void ApplyBias(int index, float x, float y, float z);

  /// Picks one die up and throws it again, leaving every other die where it
  /// is. The visible last resort, not a nudge (`docs/physics-and-rendering.md`).
  void Respawn(int index, const Placement& placement);

 private:
  struct Impl;
  Impl* impl_;
  bool ok_ = true;
};

}  // namespace dinfinity
