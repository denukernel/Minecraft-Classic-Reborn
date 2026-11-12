package net.classicremastered.minecraft.chat.commands;

import net.classicremastered.minecraft.Minecraft;
import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.mob.CrossbowTurret;
import net.classicremastered.minecraft.mob.Defender;
import net.classicremastered.minecraft.mob.Mob;
import net.classicremastered.minecraft.mob.MobRegistry;
import net.classicremastered.minecraft.mob.TurretVariant;
import net.classicremastered.minecraft.player.Player;
import net.classicremastered.minecraft.render.entity.RenderManager;

import java.util.ArrayDeque;
import java.util.Locale;

public class SpawnMobCommand implements Command, AliasedCommand {
    private static final int TICK_BATCH = 64;
    private static final ArrayDeque<Request> pending = new ArrayDeque<>();

    private record Type(String name, short id, TurretVariant variant) {}

    private static final class Request {
        final Minecraft client;
        final Level level;
        final Type type, mount;
        final String owner;
        final float x, y, z;
        final int count;
        final boolean spread;
        int spawned;

        Request(
                Minecraft client,
                Type type,
                Type mount,
                String owner,
                float x,
                float y,
                float z,
                int count,
                boolean spread) {
            this.client = client;
            this.level = client.level;
            this.type = type;
            this.mount = mount;
            this.owner = owner;
            this.x = x;
            this.y = y;
            this.z = z;
            this.count = count;
            this.spread = spread;
        }

        int spawn(int budget) {
            int attempted = 0;
            while (spawned < count && attempted < budget) {
                attempted++;
                float px = x + (spread ? level.random.nextFloat() * 2 - 1 : 0);
                float pz = z + (spread ? level.random.nextFloat() * 2 - 1 : 0);
                Mob mob = create(type, px, pz);
                Mob vehicle = mount == null ? null : create(mount, px, pz);
                if (mob == null || mount != null && vehicle == null) {
                    finish("&cCould not create the requested mob or mount.");
                    return -attempted;
                }
                if (vehicle != null) {
                    mob.mount(vehicle);
                    if (mob.riding != vehicle) {
                        finish(
                                "&cThe rider could not mount here. Check clearance and mount requirements.");
                        return -attempted;
                    }
                    level.addEntity(vehicle);
                }
                level.addEntity(mob);
                spawned++;
            }
            if (spawned == count) finish("");
            return attempted;
        }

        private Mob create(Type type, float x, float z) {
            Mob mob = MobRegistry.create(type.id(), level, x, y, z);
            if (mob == null
                    || mob.getRegistryId() < 0
                    || RenderManager.getRenderer(mob) == null
                            && (mob.modelName == null || mob.modelName.isEmpty())) return null;
            if (mob instanceof CrossbowTurret turret && type.variant() != null) {
                turret.variant = type.variant();
                turret.owner = owner;
                turret.setGuardPost();
            }
            if (mob instanceof Defender defender) defender.owner = owner;
            return mob;
        }

        private void finish(String error) {
            if (!error.isEmpty()) hud(client, error);
            String amount = spawned == count ? Integer.toString(spawned) : spawned + " of " + count;
            hud(
                    client,
                    "&aSpawned &f"
                            + amount
                            + "&a x &f"
                            + type.name()
                            + (mount == null ? "" : "&a riding &f" + mount.name())
                            + "&a.");
        }
    }

    @Override
    public String getName() {
        return "spawnmob";
    }

    @Override
    public String[] getAliases() {
        return new String[] {"spawn", "summon", "sm"};
    }

    @Override
    public String getUsage() {
        return "/summon <mob> [count] [x y z] [mount]";
    }

    @Override
    public void execute(Minecraft mc, Player sender, String[] args) {
        if (mc == null || mc.level == null || mc.player == null) {
            hud(mc, "&cNo level/player loaded.");
            return;
        }
        if (args.length < 1 || args.length > 6) {
            hud(mc, "&cUsage: " + getUsage());
            return;
        }
        MobRegistry.bootstrapDefaults();
        try {
            Type type = resolve(args[0]);
            int count = 1;
            int index = 1;
            int remaining = args.length - 1;
            // Three arguments are coordinates. Four can be count + coordinates or
            // coordinates + mount, so the final token resolves the ambiguity.
            boolean hasCount =
                    remaining == 5
                            || remaining == 4 && isCoordinate(args[4])
                            || remaining <= 2 && remaining > 0 && isCountToken(args[1]);
            if (hasCount) count = parseCount(args[index++]);
            Player origin = sender == null ? mc.player : sender;
            float x = origin.x, y = origin.y, z = origin.z;
            boolean explicitPosition = args.length - index >= 3;
            if (explicitPosition) {
                x = parseCoord(args[index++], x);
                y = parseCoord(args[index++], y);
                z = parseCoord(args[index++], z);
            }
            if (args.length - index > 1)
                throw new IllegalArgumentException("Expected x y z coordinates.");
            Type mount = index < args.length ? resolve(args[index]) : null;
            Request request =
                    new Request(
                            mc,
                            type,
                            mount,
                            CrossbowTurret.playerName(origin),
                            x,
                            y,
                            z,
                            count,
                            !explicitPosition);
            if (!pending.isEmpty()) {
                pending.addLast(request);
            } else if (request.spawn(TICK_BATCH) > 0 && request.spawned < count) {
                pending.addLast(request);
            }
        } catch (IllegalArgumentException ex) {
            hud(mc, "&c" + ex.getMessage());
        }
    }

    public static void tick(Minecraft mc) {
        int budget = TICK_BATCH;
        while (budget > 0 && !pending.isEmpty()) {
            Request request = pending.peekFirst();
            if (request.client != mc || request.level != mc.level) {
                pending.removeFirst();
                continue;
            }
            int attempted = request.spawn(budget);
            budget -= Math.abs(attempted);
            if (attempted < 0 || request.spawned == request.count) pending.removeFirst();
        }
    }

    public static void worldChanged(Minecraft mc) {
        pending.removeIf(request -> request.client == mc);
    }

    private static Type resolve(String input) {
        String name = input.toLowerCase(Locale.ROOT);
        switch (name) {
            case "entity",
                    "mob",
                    "human",
                    "humanoid",
                    "humanoidmob",
                    "quadrupedmob",
                    "player",
                    "bot",
                    "playerclone":
                throw new IllegalArgumentException("This entity type cannot be summoned.");
            default:
                break;
        }
        TurretVariant variant =
                switch (name) {
                    case "rover" -> TurretVariant.ROVER;
                    case "arrow_shotgun" -> TurretVariant.SHOTGUN;
                    case "bullet" -> TurretVariant.BULLET;
                    case "bullet_shotgun" -> TurretVariant.BULLET_SHOTGUN;
                    default -> null;
                };
        short id = variant == null ? MobRegistry.idOfName(name) : CrossbowTurret.REGISTRY_ID;
        if (id < 0)
            throw new IllegalArgumentException(
                    "Unknown mob or mount: " + name.replaceAll("[^a-z0-9_.-]", "?"));
        return new Type(name, id, variant);
    }

    private static boolean isCountToken(String token) {
        return token.matches("[+-]?[0-9].*");
    }

    private static int parseCount(String token) {
        try {
            int count = Integer.parseInt(token);
            if (count > 0) return count;
        } catch (NumberFormatException ignored) {
        }
        throw new IllegalArgumentException("Count must be a positive integer.");
    }

    private static boolean isCoordinate(String token) {
        if (token.startsWith("~")) return true;
        try {
            Float.parseFloat(token);
            return true;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    private static float parseCoord(String token, float origin) {
        try {
            boolean relative = token.startsWith("~");
            float coordinate =
                    token.equals("~")
                            ? origin
                            : relative
                                    ? origin + Float.parseFloat(token.substring(1))
                                    : Float.parseFloat(token);
            if (Float.isFinite(coordinate)) return coordinate;
        } catch (NumberFormatException ignored) {
        }
        throw new IllegalArgumentException(
                "Coordinates must be finite numbers or relative offsets.");
    }

    private static void hud(Minecraft mc, String msg) {
        if (mc != null && mc.hud != null) mc.hud.addChat(msg);
    }
}

