package org.carlspring.strongbox.gremlin.adapters;

import static org.carlspring.strongbox.gremlin.dsl.EntityTraversalDsl.NULL;

import java.util.Collections;

import javax.inject.Inject;

import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.carlspring.strongbox.db.schema.Edges;
import org.carlspring.strongbox.db.schema.Vertices;
import org.carlspring.strongbox.domain.LayoutArtifactCoordinatesEntity;
import org.carlspring.strongbox.gremlin.dsl.EntityTraversal;
import org.carlspring.strongbox.gremlin.dsl.__;

/**
 * @author sbespalov
 */
public abstract class LayoutArtifactCoordinatesArapter<C extends LayoutArtifactCoordinatesEntity<C, V>, V extends Comparable<V>>
        extends VertexEntityTraversalAdapter<C>
{
    @Inject
    private GenericArtifactCoordinatesArapter genericArtifactCoordinatesArapter;

    EntityTraversal<Vertex, Object> genericArtifactCoordinatesProjection()
    {
        return __.outE(Edges.ARTIFACT_COORDINATES_INHERIT_GENERIC_ARTIFACT_COORDINATES)
                 .mapToObject(__.inV()
                                .hasLabel(Vertices.GENERIC_ARTIFACT_COORDINATES)
                                .map(genericArtifactCoordinatesArapter.fold(__.<Vertex>identity()
                                                                              .constant(Collections.singletonList(NULL))))
                                .map(EntityTraversalUtils::castToObject));
    }

    abstract <S> EntityTraversal<S, C> fold(EntityTraversal<Vertex, Object> genericArtifactCoordinatesTraversal);

}
