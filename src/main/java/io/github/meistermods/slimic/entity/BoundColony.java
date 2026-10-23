package io.github.meistermods.slimic.entity;

public interface BoundColony {
  ColonyMembers members();

  static ColonyMembers ready(FriendlySlime slime) {
    if (!(slime instanceof BoundColony colony)) return null;
    ColonyMembers members = colony.members();
    return members != null && members.ready() && !members.projecting() ? members : null;
  }
}
