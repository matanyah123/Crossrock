package dev.matanyah.eef;

import java.util.List;

/** Platform hook for resource-pack subject queries. Results must be ordered nearest-first. */
@FunctionalInterface
public interface EefSubjectResolver {
	EefSubjectResolver NONE = (query, event) -> List.of();
	List<EntityEvent> resolve(EefSubjectQuery query, EntityEvent event);
}
