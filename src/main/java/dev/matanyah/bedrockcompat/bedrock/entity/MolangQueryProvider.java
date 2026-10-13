package dev.matanyah.bedrockcompat.bedrock.entity;

import java.util.List;
import java.util.Optional;

/** Optional, per-context runtime integration. Empty means unavailable, never a fabricated zero.
 * Implementations must return frame snapshots, not mutate gameplay or load chunks during evaluation.
 * A present zero/false/empty string is a valid result and must not fall through to another provider.
 */
@FunctionalInterface
public interface MolangQueryProvider {
    Optional<Object> query(String name, List<Object> arguments);

    MolangQueryProvider UNAVAILABLE = (name, arguments) -> Optional.empty();

    default Molang.Queries orElse(Molang.Queries fallback) {
        return (name, arguments) -> query(name, arguments).orElseGet(() -> fallback.get(name, arguments));
    }
}
