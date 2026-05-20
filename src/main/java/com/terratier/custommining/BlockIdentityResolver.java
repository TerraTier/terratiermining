package com.terratier.custommining;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.bukkit.block.Block;

final class BlockIdentityResolver {
    List<String> candidates(Block block, MiningConfig config) {
        Set<String> ids = new LinkedHashSet<>();

        for (BlockIdentityRule identityRule : config.customBlockIdentities()) {
            if (identityRule.matches(block)) {
                ids.add(identityRule.id());
            }
        }

        ids.add(block.getType().getKey().asString());
        return new ArrayList<>(ids);
    }
}
