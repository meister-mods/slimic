package io.github.meistermods.slimic.entity;

import java.util.Set;

/** A companion's order, execution state, blocking reason, and actionable support requests. */
public record WorkStatus(Order order, State state, Reason reason, Set<Request> requests) {
  public enum Order {
    FOLLOW,
    WAIT,
    MINE,
    HAUL,
    MONITOR,
    FIGHT,
    CLEAN,
    FARM,
    LOG
  }

  public enum State {
    IDLE,
    ACTIVE,
    BLOCKED,
    COMPLETE
  }

  public enum Reason {
    NONE,
    INVENTORY_FULL,
    TOOL_MISSING,
    TOOL_TIER,
    EXHAUSTED,
    NO_STORAGE,
    STORAGE_FULL,
    UNREACHABLE,
    SUPPORT_MISSING,
    DISABLED,
    SEEDS_MISSING
  }

  public enum Request {
    CARGO_PICKUP,
    TOOL_SUPPLY
  }

  public WorkStatus {
    requests = Set.copyOf(requests);
  }

  public static WorkStatus of(Order order, State state) {
    return new WorkStatus(order, state, Reason.NONE, Set.of());
  }

  public static WorkStatus blocked(Order order, Reason reason, Request... requests) {
    return new WorkStatus(order, State.BLOCKED, reason, Set.of(requests));
  }

  public boolean requests(Request request) {
    return requests.contains(request);
  }
}
