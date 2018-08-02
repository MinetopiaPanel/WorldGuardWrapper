package org.codemc.worldguardwrapper.implementation.v6;

import java.util.Optional;

import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.FlagContext;
import com.sk89q.worldguard.protection.flags.InvalidFlagFormat;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.flags.registry.FlagConflictException;
import com.sk89q.worldguard.protection.flags.registry.FlagRegistry;
import com.sk89q.worldguard.protection.managers.RegionManager;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.codemc.worldguardwrapper.implementation.AbstractFlag;
import org.codemc.worldguardwrapper.implementation.AbstractWorldGuardImplementation;

import lombok.NonNull;

public class WorldGuardImplementation extends AbstractWorldGuardImplementation {

    private final WorldGuardPlugin plugin;
    private final FlagRegistry flagRegistry;

    public WorldGuardImplementation() {
        plugin = WorldGuardPlugin.inst();
        flagRegistry = plugin.getFlagRegistry();
    }

    private Optional<LocalPlayer> wrapPlayer(Player player) {
        return Optional.ofNullable(player).map(bukkitPlayer -> plugin.wrapPlayer(player));
    }

    private Optional<RegionManager> getWorldManager(@NonNull World world) {
        return Optional.ofNullable(plugin.getRegionManager(world));
    }

    private Optional<ApplicableRegionSet> getApplicableRegions(@NonNull Location location) {
        return getWorldManager(location.getWorld()).map(manager -> manager.getApplicableRegions(location));
    }

    private <V> Optional<V> queryValue(Player player, @NonNull Location location, @NonNull Flag<V> flag) {
        return getApplicableRegions(location).map(applicableRegions -> applicableRegions.queryValue(wrapPlayer(player).orElse(null), flag));
    }

    private Optional<StateFlag.State> queryState(Player player, @NonNull Location location, @NonNull StateFlag... stateFlags) {
        return getApplicableRegions(location).map(applicableRegions -> applicableRegions.queryState(wrapPlayer(player).orElse(null), stateFlags));
    }

    @Override
    public JavaPlugin getWorldGuardPlugin() {
        return WorldGuardPlugin.inst();
    }

    @Override
    public int getApiVersion() {
        return 6;
    }

    @Override
    public Optional<Boolean> queryStateFlag(Player player, @NonNull Location location, @NonNull String flagId) {
        Flag<?> flag = flagRegistry.get(flagId);
        if (!(flag instanceof StateFlag)) {
            return Optional.empty();
        }
        return queryState(player, location, (StateFlag) flag).map(state -> state == StateFlag.State.ALLOW);
    }

    @Override
    public boolean registerStateFlag(@NonNull String flagId, @NonNull Boolean defaultValue) {
        try {
            flagRegistry.register(new StateFlag(flagId, defaultValue));
            return true;
        } catch (FlagConflictException ignored) {
        }
        return false;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Optional<T> queryFlag(Player player, Location location, AbstractFlag<T> flag) {        
        Flag<?> wgFlag = flagRegistry.get(flag.getName());
        Object value = queryValue(player, location, wgFlag).orElse(null);
        if (flag.getType().isInstance(value)) {
            return Optional.of((T) value);
        }
        return Optional.empty();
    }

    @Override
    public <T> boolean registerFlag(AbstractFlag<T> flag) {
        Flag<T> wgFlag = new Flag<T>(flag.getName()) {
            @Override
            public T getDefault() {
                return flag.getDefaultValue();
            }

            @Override
            public Object marshal(T o) {
                return flag.serialize(o);
            }

            @Override
            public T unmarshal(Object o) {
                return flag.deserialize(o);
            }

            @Override
            public T parseInput(FlagContext context) throws InvalidFlagFormat {
                return flag.parse(context.getPlayerSender(), context.getUserInput());
            }
        };

        try {
            flagRegistry.register(wgFlag);
            return true;
        } catch (FlagConflictException ignored) {
        }
        return false;
    }
}
