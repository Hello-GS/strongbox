package org.carlspring.strongbox.gremlin.adapters;

import static org.apache.tinkerpop.gremlin.structure.VertexProperty.Cardinality.single;
import static org.carlspring.strongbox.gremlin.adapters.EntityTraversalUtils.extractObject;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;

import org.apache.tinkerpop.gremlin.process.traversal.Traversal;
import org.apache.tinkerpop.gremlin.process.traversal.Traverser;
import org.apache.tinkerpop.gremlin.structure.Edge;
import org.apache.tinkerpop.gremlin.structure.Element;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.carlspring.strongbox.db.schema.Edges;
import org.carlspring.strongbox.db.schema.Vertices;
import org.carlspring.strongbox.domain.Artifact;
import org.carlspring.strongbox.domain.ArtifactEntity;
import org.carlspring.strongbox.domain.RemoteArtifact;
import org.carlspring.strongbox.domain.RemoteArtifactEntity;
import org.carlspring.strongbox.gremlin.dsl.EntityTraversal;
import org.carlspring.strongbox.gremlin.dsl.EntityTraversalDsl;
import org.carlspring.strongbox.gremlin.dsl.__;
import org.springframework.stereotype.Component;

/**
 * @author sbespalov
 */
@Component
public class RemoteAritfactAdapter extends VertexEntityTraversalAdapter<RemoteArtifact>
        implements ArtifactHierarchyAdapter<RemoteArtifact>
{

    @Inject
    private ArtifactAdapter artifactAdapter;

    @Override
    public Set<String> labels()
    {
        return Collections.singleton(Vertices.REMOTE_ARTIFACT);
    }

    @Override
    public Class<? extends RemoteArtifact> entityClass()
    {
        return RemoteArtifact.class;
    }

    @Override
    public EntityTraversal<Vertex, RemoteArtifact> fold()
    {
        return foldHierarchy(parentProjection(), childProjection());
    }

    @Override
    public EntityTraversal<Vertex, RemoteArtifact> foldHierarchy(EntityTraversal<Vertex, Object> parentProjection,
                                                                 EntityTraversal<Vertex, Object> childProjection)
    {
        return __.<Vertex, Object>project("uuid", "cached", "artifact")
                 .by(__.enrichPropertyValue("uuid"))
                 .by(__.enrichPropertyValue("cached"))
                 .by(parentProjection)
                 .map(this::map);
    }

    @Override
    public EntityTraversal<Vertex, Object> parentProjection()
    {
        return __.outE(Edges.REMOTE_ARTIFACT_INHERIT_ARTIFACT)
                 .mapToObject(__.inV()
                                .hasLabel(Vertices.ARTIFACT)
                                .map(artifactAdapter.foldHierarchy(artifactAdapter.parentProjection(),
                                                                   __.<Vertex>V().constant(EntityTraversalDsl.NULL)))
                                .map(EntityTraversalUtils::castToObject));
    }

    @Override
    public EntityTraversal<Vertex, Object> childProjection()
    {
        return __.<Vertex>V().constant(EntityTraversalDsl.NULL);
    }

    private RemoteArtifact map(Traverser<Map<String, Object>> t)
    {
        RemoteArtifactEntity result = new RemoteArtifactEntity(
                extractObject(ArtifactEntity.class, t.get().get("artifact")));
        result.setUuid(extractObject(String.class, t.get().get("uuid")));
        result.setIsCached(extractObject(Boolean.class, t.get().get("cached")));

        return result;
    }

    @Override
    public UnfoldEntityTraversal<Vertex, Vertex> unfold(RemoteArtifact entity)
    {
        EntityTraversal<Vertex, Vertex> t = __.<Vertex, Edge>coalesce(updateArtifact(entity),
                                                                      createArtifact(entity))
                                              .outV()
                                              .map(unfoldRemoteArtifact(entity));
        return new UnfoldEntityTraversal<>(Vertices.REMOTE_ARTIFACT, t);
    }

    private Traversal<Vertex, Edge> updateArtifact(Artifact artifact)
    {
        return __.<Vertex>outE(Edges.REMOTE_ARTIFACT_INHERIT_ARTIFACT)
                 .as(Edges.REMOTE_ARTIFACT_INHERIT_ARTIFACT)
                 .inV()
                 .map(saveArtifact(artifact))
                 .select(Edges.REMOTE_ARTIFACT_INHERIT_ARTIFACT);
    }

    private Traversal<Vertex, Edge> createArtifact(Artifact artifactGroup)
    {
        return __.<Vertex>addE(Edges.REMOTE_ARTIFACT_INHERIT_ARTIFACT)
                 .from(__.<Vertex>identity())
                 .to(saveArtifact(artifactGroup));
    }

    private <S2> EntityTraversal<S2, Vertex> saveArtifact(Artifact artifactGroup)
    {
        return __.<S2>V()
                 .saveV(Vertices.REMOTE_ARTIFACT,
                        artifactGroup.getUuid(),
                        artifactAdapter.unfold(artifactGroup));
    }

    private EntityTraversal<Vertex, Vertex> unfoldRemoteArtifact(RemoteArtifact entity)
    {
        EntityTraversal<Vertex, Vertex> t = __.<Vertex>identity();

        if (entity.getIsCached() != null)
        {
            t = t.property(single, "сached", entity.getIsCached());
        }

        return t;
    }

    @Override
    public EntityTraversal<Vertex, ? extends Element> cascade()
    {
        return __.<Vertex>aggregate("x")
                 .outE(Edges.REMOTE_ARTIFACT_INHERIT_ARTIFACT)
                 .inV()
                 .map(artifactAdapter.cascade())
                 .select("x")
                 .unfold();
    }

}
