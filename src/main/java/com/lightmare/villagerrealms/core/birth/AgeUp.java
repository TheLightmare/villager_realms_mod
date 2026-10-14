package com.lightmare.villagerrealms.core.birth;

import com.lightmare.villagerrealms.core.consequence.Consequences;
import com.lightmare.villagerrealms.core.record.Identity;
import com.lightmare.villagerrealms.core.record.NPCRecord;
import com.lightmare.villagerrealms.core.record.RoleState;
import com.lightmare.villagerrealms.core.record.VillageRecord;
import com.lightmare.villagerrealms.core.record.VillageWorkstation;
import com.lightmare.villagerrealms.core.role.Role;
import com.lightmare.villagerrealms.core.role.RoleRegistry;

/**
 * Pure age-up: a child becomes an adult with the role its bed carries
 * (CLAUDE.md: the audit assigns roles to beds, so a child born into a
 * dead farmer's bed grows up a farmer). Workplace resolution mirrors the
 * auditor: the nearest workstation of that role, or the village centre for
 * roles without a station.
 */
public final class AgeUp {

    private AgeUp() {}

    public static NPCRecord apply(NPCRecord child, VillageRecord village, long now) {
        if (child == null) return null;
        String roleId = RoleRegistry.LABORER;
        int bedIdx = village == null ? -1 : village.bedIndexOf(child.identity().uuid());
        if (bedIdx >= 0) {
            String bedRole = village.beds().get(bedIdx).role();
            if (bedRole != null && !bedRole.isEmpty() && !Births.CHILD_ROLE.equals(bedRole)) roleId = bedRole;
        }
        Role def = RoleRegistry.getOrLaborer(roleId);
        String workplace = workplaceFor(def, child, village);

        Identity old = child.identity();
        String name = roleName(roleId) + "-" + old.uuid().toString().substring(0, 6);
        Identity grown = new Identity(old.uuid(), name, Births.ADULT_AGE, old.gender(), old.traits());
        NPCRecord renamed = new NPCRecord(
                child.dataVersion(), grown, child.location(), child.vitals(), child.inventory(),
                child.economy(), child.role(), child.factionId(), child.relationships(),
                child.memory(), child.action());
        return Consequences.roleChanged(renamed, new RoleState(roleId, workplace, 0L), now);
    }

    private static String workplaceFor(Role def, NPCRecord child, VillageRecord village) {
        if (!def.worker() || village == null) return null;
        if (!def.workstationKind().isEmpty()) {
            VillageWorkstation best = null;
            double bestSq = Double.MAX_VALUE;
            double cx = child.location().x(), cy = child.location().y(), cz = child.location().z();
            for (VillageWorkstation w : village.workstations()) {
                if (!def.id().equals(w.role())) continue;
                double dx = w.x() - cx, dy = w.y() - cy, dz = w.z() - cz;
                double d = dx * dx + dy * dy + dz * dz;
                if (d < bestSq) { bestSq = d; best = w; }
            }
            if (best != null) return best.x() + "," + best.y() + "," + best.z();
        }
        return village.centerX() + "," + village.groundY() + "," + village.centerZ();
    }

    private static String roleName(String role) {
        if (role == null || role.isEmpty()) return "NPC";
        return Character.toUpperCase(role.charAt(0)) + role.substring(1);
    }
}
