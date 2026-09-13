#include "world.h"

#include <Jolt/Jolt.h>

#include <Jolt/Core/Factory.h>
#include <Jolt/Core/JobSystemSingleThreaded.h>
#include <Jolt/Core/TempAllocator.h>
#include <Jolt/Physics/Body/BodyActivationListener.h>
#include <Jolt/Physics/Body/BodyCreationSettings.h>
#include <Jolt/Physics/Collision/ContactListener.h>
#include <Jolt/Physics/Collision/Shape/BoxShape.h>
#include <Jolt/Physics/Collision/Shape/ConvexHullShape.h>
#include <Jolt/Physics/Collision/Shape/CylinderShape.h>
#include <Jolt/Physics/Collision/Shape/StaticCompoundShape.h>
#include <Jolt/Physics/PhysicsSettings.h>
#include <Jolt/Physics/PhysicsSystem.h>
#include <Jolt/RegisterTypes.h>

#include <android/log.h>

#include <cmath>
#include <cstdarg>
#include <cstdio>
#include <mutex>
#include <vector>

JPH_SUPPRESS_WARNINGS

namespace dinfinity {
namespace {

using namespace JPH;

constexpr const char* kTag = "dinfinity.jolt";

// Two object layers, and one broad phase layer they both map to. The tray is
// static and could have had a tree of its own, but there is exactly one tray
// body and a second tree would cost a broad phase query per step to save
// nothing.
namespace layers {
constexpr ObjectLayer kDice = 0;
constexpr ObjectLayer kTray = 1;
}  // namespace layers

namespace broad_phase {
constexpr BroadPhaseLayer kMoving(0);
constexpr uint kCount = 1;
}  // namespace broad_phase

class BroadPhaseLayers final : public BroadPhaseLayerInterface {
 public:
  uint GetNumBroadPhaseLayers() const override { return broad_phase::kCount; }

  BroadPhaseLayer GetBroadPhaseLayer(ObjectLayer) const override { return broad_phase::kMoving; }

#if defined(JPH_EXTERNAL_PROFILE) || defined(JPH_PROFILE_ENABLED)
  const char* GetBroadPhaseLayerName(BroadPhaseLayer) const override { return "MOVING"; }
#endif
};

class ObjectVsBroadPhase final : public ObjectVsBroadPhaseLayerFilter {
 public:
  bool ShouldCollide(ObjectLayer, BroadPhaseLayer) const override { return true; }
};

/// Dice collide with dice and with the tray; the tray does not collide with
/// itself.
class ObjectPairs final : public ObjectLayerPairFilter {
 public:
  bool ShouldCollide(ObjectLayer first, ObjectLayer second) const override {
    return first == layers::kDice || second == layers::kDice;
  }
};

void TraceToLogcat(const char* format, ...) {
  va_list args;
  va_start(args, format);
  __android_log_vprint(ANDROID_LOG_INFO, kTag, format, args);
  va_end(args);
}

#ifdef JPH_ENABLE_ASSERTS
bool AssertToLogcat(const char* expression, const char* message, const char* file, uint line) {
  __android_log_print(ANDROID_LOG_ERROR, kTag, "%s:%u: (%s) %s", file, line, expression,
                      message != nullptr ? message : "");
  return false;
}
#endif

/// Jolt's allocator, factory and type registry are process-wide and are set up
/// exactly once. There is no matching teardown on purpose: a roll can start at
/// any moment, and unregistering under one world while another is being built
/// would be a race for no gain.
void EnsureJoltReady() {
  static std::once_flag once;
  std::call_once(once, [] {
    RegisterDefaultAllocator();
    Trace = TraceToLogcat;
    JPH_IF_ENABLE_ASSERTS(AssertFailed = AssertToLogcat;)
    Factory::sInstance = new Factory();
    RegisterTypes();
  });
}

Vec3 ToVec3(const float* v) { return Vec3(v[0], v[1], v[2]); }

Quat ToQuat(const float* q) { return Quat(q[1], q[2], q[3], q[0]).Normalized(); }

/// How vertical a contact normal has to be before it counts as one body
/// standing on another rather than brushing past it.
constexpr float kVerticalNormal = 0.6f;

/// The rounding on the tray's own shapes, in simulation units.
///
/// Half a millimetre. It has to be given explicitly, because Jolt's default is
/// 5 cm — the whole thickness of a tray wall, and a box cannot be rounded by
/// more than its own half-extent. Left at the default the shape is refused
/// outright with "Invalid convex radius" rather than quietly coming out a
/// different tray, which at least fails where it can be read.
constexpr float kTrayConvexRadius = 0.05f;

/// Body user data: 0 is the tray, a die is its index plus one. It is read in
/// contact callbacks, where there is no map to consult.
constexpr std::uint64_t kTrayUserData = 0;

}  // namespace

struct World::Impl : public ContactListener {
  Impl(const TraySpec& tray, int max_dice)
      : temp_allocator(4 * 1024 * 1024), job_system(cMaxPhysicsJobs) {
    dice.reserve(static_cast<std::size_t>(max_dice));
    contacts.reserve(static_cast<std::size_t>(max_dice));

    PhysicsSettings settings;
    // Almost everything here is Jolt's default, and that is the point: at one
    // centimetre to the unit a die is 1.6 units across, which is inside the
    // range Jolt is tuned for, so its tolerances mean what they were written
    // to mean (`docs/architecture.md`, decision 41). The two exceptions are
    // below.
    //
    // Restitution: Jolt stops bouncing below 1 unit/s, which is a centimetre a
    // second — far slower than the point at which a real die on felt has
    // stopped bouncing. Fifteen is about where a die lands and stays.
    settings.mMinVelocityForRestitution = 15.0f;
    // The app decides when a die has stopped, not the engine. Jolt's own
    // sleeping would freeze a body at its thresholds rather than at the ones
    // `SettleRule` documents, and two rules for the same question is one too
    // many.
    settings.mAllowSleeping = false;
    settings.mDeterministicSimulation = true;

    // Room for the tray plus every die the capacity rule can allow, and their
    // pairs. Jolt pre-allocates these, so they are an upper bound rather than
    // a guess that can be exceeded mid-roll.
    const uint body_limit = static_cast<uint>(max_dice) + 8u;
    system.Init(body_limit, 0, body_limit * 4u, body_limit * 4u, broad_phase_layers,
                object_vs_broad_phase, object_pairs);
    system.SetPhysicsSettings(settings);
    system.SetContactListener(this);
    // Up is +Z here, as it is everywhere else in the app (`Vector3.Up`).
    system.SetGravity(Vec3(0.0f, 0.0f, -981.0f));

    tray_body = CreateTray(tray);
  }

  ~Impl() override = default;

  BodyID CreateTray(const TraySpec& tray) {
    const float half_long = tray.half_long;
    const float half_short = tray.half_short;
    // Five centimetres, which is three times the width of a die and far
    // thicker than a tray needs to look right. None of it is visible — it is
    // all outside the floor area — and it is cheap insurance: a die driven
    // into a wall at the speed a hard shake gives it crosses several
    // millimetres in a step, and the cost of a wall it can get through is a
    // die falling for ever outside the box.
    const float thickness = 5.0f;
    const float ceiling = tray.ceiling_height;
    const float radius = tray.corner_radius;

    StaticCompoundShapeSettings compound;
    compound.SetEmbedded();

    // The floor sits below z = 0, so the inside of the tray starts at zero and
    // every spawn height in Kotlin is a height above the floor.
    compound.AddShape(Vec3(0.0f, 0.0f, -thickness), Quat::sIdentity(),
                      new BoxShapeSettings(Vec3(half_long, half_short, thickness),
                                           kTrayConvexRadius));
    compound.AddShape(Vec3(0.0f, 0.0f, ceiling + thickness), Quat::sIdentity(),
                      new BoxShapeSettings(Vec3(half_long, half_short, thickness),
                                           kTrayConvexRadius));

    // The walls run all the way to the ceiling, not to `wall_height`.
    //
    // That height is what the *renderer* draws as the rim of the tray; the
    // physics needs a closed box, because "dice cannot leave the table no
    // matter how hard the phone is shaken" (`docs/tables.md`) is not true of a
    // box with a lid two hundred millimetres up and sides only sixty. A die
    // thrown hard goes up through the gap and never comes back.
    const float wall_half = ceiling * 0.5f;
    compound.AddShape(Vec3(half_long + thickness, 0.0f, wall_half), Quat::sIdentity(),
                      new BoxShapeSettings(Vec3(thickness, half_short, wall_half),
                                           kTrayConvexRadius));
    compound.AddShape(Vec3(-half_long - thickness, 0.0f, wall_half), Quat::sIdentity(),
                      new BoxShapeSettings(Vec3(thickness, half_short, wall_half),
                                           kTrayConvexRadius));
    compound.AddShape(Vec3(0.0f, half_short + thickness, wall_half), Quat::sIdentity(),
                      new BoxShapeSettings(Vec3(half_long, thickness, wall_half),
                                           kTrayConvexRadius));
    compound.AddShape(Vec3(0.0f, -half_short - thickness, wall_half), Quat::sIdentity(),
                      new BoxShapeSettings(Vec3(half_long, thickness, wall_half),
                                           kTrayConvexRadius));

    // The rounded inner corners: a cylinder tangent to both walls, so a die
    // driven into a corner slides back out instead of wedging in the angle
    // (`docs/tables.md`).
    //
    // They run to the ceiling for the same reason the walls do, and it took a
    // phone to see why. Stopping them at the rim left their top faces as four
    // horizontal ledges inside the tray, sixty millimetres up — and a die that
    // landed on one came to rest there, in mid-air as far as the player is
    // concerned, with its shadow on the floor well below it. The roll was then
    // read off a die that was never on the table.
    if (radius > 0.0f) {
      const float corner_half = ceiling * 0.5f;
      const Quat upright = Quat::sRotation(Vec3(1.0f, 0.0f, 0.0f), JPH_PI * 0.5f);
      for (int sx = -1; sx <= 1; sx += 2) {
        for (int sy = -1; sy <= 1; sy += 2) {
          compound.AddShape(Vec3(static_cast<float>(sx) * (half_long - radius),
                                 static_cast<float>(sy) * (half_short - radius), corner_half),
                            upright,
                            new CylinderShapeSettings(corner_half, radius, kTrayConvexRadius));
        }
      }
    }

    ShapeSettings::ShapeResult shape = compound.Create();
    if (shape.HasError()) {
      __android_log_print(ANDROID_LOG_ERROR, kTag, "tray: %s", shape.GetError().c_str());
      failed = true;
      return BodyID();
    }

    // Static, and it stays that way. The tray is the phone's screen, so it
    // does not move in the frame the dice are simulated in; a shake reaches
    // them through gravity instead (`ShakeDriver`). It also means the tray is
    // never a moving wall, which is what a 16 mm die cannot survive.
    BodyCreationSettings body(shape.Get(), RVec3::sZero(), Quat::sIdentity(), EMotionType::Static,
                              layers::kTray);
    body.mFriction = tray.friction;
    body.mRestitution = tray.restitution;
    body.mAllowSleeping = false;
    body.mUserData = kTrayUserData;
    return system.GetBodyInterface().CreateAndAddBody(body, EActivation::Activate);
  }

  // ContactListener. With a single-threaded job system these arrive in one
  // thread and in a fixed order, which is half of why the job system is what
  // it is.
  void OnContactAdded(const Body& first, const Body& second, const ContactManifold& manifold,
                      ContactSettings& settings) override {
    Record(first, second, manifold);
    (void)settings;
  }

  void OnContactPersisted(const Body& first, const Body& second, const ContactManifold& manifold,
                          ContactSettings& settings) override {
    Record(first, second, manifold);
    (void)settings;
  }

  /// The manifold normal is the direction body 2 has to move to come out of
  /// the collision, so a normal pointing up means body 2 is the one on top.
  void Record(const Body& first, const Body& second, const ContactManifold& manifold) {
    const std::uint64_t first_data = first.GetUserData();
    const std::uint64_t second_data = second.GetUserData();
    const float up = manifold.mWorldSpaceNormal.GetZ();

    if (first_data == kTrayUserData || second_data == kTrayUserData) {
      const std::uint64_t die_data = first_data == kTrayUserData ? second_data : first_data;
      if (die_data == kTrayUserData) return;
      const std::uint32_t flag =
          std::fabs(up) >= kVerticalNormal ? kTouchingFloor : kTouchingWall;
      Flag(die_data, flag);
      return;
    }

    if (up >= kVerticalNormal) {
      Flag(second_data, kSupportedByDie);
    } else if (up <= -kVerticalNormal) {
      Flag(first_data, kSupportedByDie);
    }
  }

  void Flag(std::uint64_t user_data, std::uint32_t flag) {
    const std::size_t index = static_cast<std::size_t>(user_data - 1);
    if (index < contacts.size()) contacts[index] |= flag;
  }

  TempAllocatorImpl temp_allocator;
  JobSystemSingleThreaded job_system;
  BroadPhaseLayers broad_phase_layers;
  ObjectVsBroadPhase object_vs_broad_phase;
  ObjectPairs object_pairs;
  PhysicsSystem system;

  BodyID tray_body;
  std::vector<BodyID> dice;
  std::vector<std::uint32_t> contacts;
  bool failed = false;
};

World::World(const TraySpec& tray, int max_dice) : impl_(nullptr) {
  EnsureJoltReady();
  impl_ = new Impl(tray, max_dice);
  ok_ = !impl_->failed;
}

World::~World() { delete impl_; }

void World::AddDie(const DieSpec& die, const Placement& placement) {
  Array<Vec3> points;
  points.reserve(static_cast<Array<Vec3>::size_type>(die.point_count));
  for (int i = 0; i < die.point_count; ++i) {
    points.push_back(Vec3(die.hull_points[i * 3], die.hull_points[i * 3 + 1],
                          die.hull_points[i * 3 + 2]));
  }

  ConvexHullShapeSettings hull(points, die.convex_radius);
  hull.SetDensity(die.density);
  hull.SetEmbedded();

  ShapeSettings::ShapeResult shape = hull.Create();
  if (shape.HasError()) {
    __android_log_print(ANDROID_LOG_ERROR, kTag, "hull: %s", shape.GetError().c_str());
    ok_ = false;
    return;
  }

  BodyCreationSettings body(shape.Get(), RVec3(ToVec3(placement.position)),
                            ToQuat(placement.rotation), EMotionType::Dynamic, layers::kDice);
  body.mFriction = die.friction;
  body.mRestitution = die.restitution;
  body.mLinearVelocity = ToVec3(placement.linear_velocity);
  body.mAngularVelocity = ToVec3(placement.angular_velocity);
  // A 16 mm die thrown hard in a 240 mm tray crosses its own width in a single
  // step, which discrete collision detection would let it pass straight
  // through a wall (`docs/TODO.md`, Step 5.4: no tunnelling at maximum shake
  // velocity).
  body.mMotionQuality = EMotionQuality::LinearCast;
  body.mAllowSleeping = false;
  // Jolt's default caps a body at about 47 rad/s, which is well inside what a
  // thrown die actually spins at; a cap that low is a silent hand on the roll.
  body.mMaxAngularVelocity = 200.0f;
  // A die thrown hard crosses the tray in a fraction of a second, and Jolt's
  // default cap is five metres a second.
  body.mMaxLinearVelocity = 2000.0f;
  // Stands in for everything this simulation does not model — the air, the
  // felt giving under a corner, the sound a die makes. Small: the spin is
  // taken out by friction now that the solver knows how heavy a die is to
  // turn, and damping doing that job instead would be a thumb on the roll.
  body.mLinearDamping = 0.02f;
  body.mAngularDamping = 0.02f;
  body.mUserData = static_cast<std::uint64_t>(impl_->dice.size()) + 1u;

  impl_->dice.push_back(
      impl_->system.GetBodyInterface().CreateAndAddBody(body, EActivation::Activate));
  impl_->contacts.push_back(0u);
}

void World::Finish() { impl_->system.OptimizeBroadPhase(); }

void World::SetGravity(float x, float y, float z) { impl_->system.SetGravity(Vec3(x, y, z)); }

void World::Step(float dt) {
  for (auto& flags : impl_->contacts) flags = 0u;
  // One collision step per simulation step: the timestep is already fixed and
  // small, and sub-stepping it would make the roll depend on a number nobody
  // has written down (`docs/physics-and-rendering.md`).
  impl_->system.Update(dt, 1, &impl_->temp_allocator, &impl_->job_system);
}

void World::ReadStates(float* out) const {
  const BodyInterface& bodies = impl_->system.GetBodyInterfaceNoLock();
  for (std::size_t i = 0; i < impl_->dice.size(); ++i) {
    const BodyID id = impl_->dice[i];
    const RVec3 position = bodies.GetPosition(id);
    const Quat rotation = bodies.GetRotation(id);
    const Vec3 linear = bodies.GetLinearVelocity(id);
    const Vec3 angular = bodies.GetAngularVelocity(id);

    float* slot = out + i * kStateStride;
    slot[0] = static_cast<float>(position.GetX());
    slot[1] = static_cast<float>(position.GetY());
    slot[2] = static_cast<float>(position.GetZ());
    slot[3] = rotation.GetW();
    slot[4] = rotation.GetX();
    slot[5] = rotation.GetY();
    slot[6] = rotation.GetZ();
    slot[7] = linear.Length();
    slot[8] = angular.Length();
    slot[9] = static_cast<float>(impl_->contacts[i]);
  }
}

void World::ApplyBias(int index, float x, float y, float z) {
  if (index < 0 || static_cast<std::size_t>(index) >= impl_->dice.size()) return;
  impl_->system.GetBodyInterface().AddLinearVelocity(impl_->dice[static_cast<std::size_t>(index)],
                                                     Vec3(x, y, z));
}

void World::Respawn(int index, const Placement& placement) {
  if (index < 0 || static_cast<std::size_t>(index) >= impl_->dice.size()) return;
  impl_->system.GetBodyInterface().SetPositionRotationAndVelocity(
      impl_->dice[static_cast<std::size_t>(index)], RVec3(ToVec3(placement.position)),
      ToQuat(placement.rotation), ToVec3(placement.linear_velocity),
      ToVec3(placement.angular_velocity));
}

}  // namespace dinfinity
