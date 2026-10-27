package io.github.meistermods.slimic.entity;

/** One saved companion controls two physical bodies and their shared state. */
public interface LinkedColony extends BoundColony {
  LinkedColonyBody<?> getLinkedBody();

  double getLinkRange();

  default boolean isBodyInMotion(LinkedColonyBody<?> body) {
    return false;
  }
}
