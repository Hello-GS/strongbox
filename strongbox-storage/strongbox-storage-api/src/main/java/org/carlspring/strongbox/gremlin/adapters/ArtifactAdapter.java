package org.carlspring.strongbox.gremlin.adapters;

import static org.apache.tinkerpop.gremlin.structure.VertexProperty.Cardinality.set;
import static org.apache.tinkerpop.gremlin.structure.VertexProperty.Cardinality.single;
import static org.carlspring.strongbox.gremlin.adapters.EntityTraversalUtils.extractObject;
import static org.carlspring.strongbox.gremlin.adapters.EntityTraversalUtils.extractList;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import javax.inject.Inject;

import org.apache.tinkerpop.gremlin.process.traversal.Traversal;
import org.apache.tinkerpop.gremlin.process.traversal.Traverser;
import org.apache.tinkerpop.gremlin.structure.Edge;
import org.apache.tinkerpop.gremlin.structure.Element;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.carlspring.strongbox.artifact.coordinates.ArtifactCoordinates;
import org.carlspring.strongbox.db.schema.Edges;
import org.carlspring.strongbox.db.schema.Vertices;
import org.carlspring.strongbox.domain.Artifact;
import org.carlspring.strongbox.domain.ArtifactArchiveListing;
import org.carlspring.strongbox.domain.ArtifactEntity;
import org.carlspring.strongbox.domain.GenericArtifactCoordinatesEntity;
import org.carlspring.strongbox.gremlin.dsl.EntityTraversal;
import org.carlspring.strongbox.gremlin.dsl.__;
import org.springframework.stereotype.Component;

/**
 * @author sbespalov
 */
@Component
public class ArtifactAdapter extends VertexEntityTraversalAdapter<Artifact>
{

    @Inject
    GenericArtifactCoordinatesArapter genericArtifactCoordinatesArapter;
    @Inject
    ArtifactCoordinatesAdapter artifactCoordinatesAdapter;

    @Override
    public Set<String> labels()
    {
        return Collections.singleton(Vertices.ARTIFACT);
    }

    @Override
    public EntityTraversal<Vertex, Artifact> fold()
    {
        return __.<Vertex, Object>project("uuid", "storageId", "repositoryId", "filenames", "genericArtifactCoordinates")
                 .by(__.enrichPropertyValue("uuid"))
                 .by(__.enrichPropertyValue("storageId"))
                 .by(__.enrichPropertyValue("repositoryId"))
                 .by(__.enrichPropertyValues("filenames"))
                 .by(__.outE(Edges.ARTIFACT_HAS_ARTIFACT_COORDINATES)
                       .mapToObject(__.inV()
                                      .sideEffect(EntityTraversalUtils::traceVertex)
                                      .map(genericArtifactCoordinatesArapter.fold())
                                      .map(EntityTraversalUtils::castToObject)))
                 .map(this::map);
    }

    private Artifact map(Traverser<Map<String, Object>> t)
    {
        String storageId = extractObject(String.class, t.get().get("storageId"));
        String repositoryId = extractObject(String.class, t.get().get("repositoryId"));
        GenericArtifactCoordinatesEntity artifactCoordinates = extractObject(GenericArtifactCoordinatesEntity.class,
                                                                             t.get().get("genericArtifactCoordinates"));

        ArtifactEntity result = new ArtifactEntity(storageId, repositoryId, artifactCoordinates);
        result.setUuid(extractObject(String.class, t.get().get("uuid")));

        result.getArtifactArchiveListing()
              .setFilenames(Optional.ofNullable(extractList(String.class, t.get().get("filenames")))
                                    .map(HashSet::new)
                                    .orElse(null));

        return result;
    }

    @Override
    public UnfoldEntityTraversal<Vertex, Vertex> unfold(Artifact entity)
    {
        ArtifactCoordinates artifactCoordinates = entity.getArtifactCoordinates();

        EntityTraversal<Vertex, Vertex> t = __.<Vertex, Edge>coalesce(updateArtifactCoordinates(artifactCoordinates),
                                                                      createArtifactCoordinates(artifactCoordinates))
                                              .outV()
                                              .map(unfoldArtifact(entity));

        return new UnfoldEntityTraversal<>(Vertices.ARTIFACT, t);
    }

    private Traversal<Vertex, Edge> updateArtifactCoordinates(ArtifactCoordinates artifactCoordinates)
    {
        return __.<Vertex>outE(Edges.ARTIFACT_HAS_ARTIFACT_COORDINATES)
                 .as(Edges.ARTIFACT_HAS_ARTIFACT_COORDINATES)
                 .inV()
                 .map(saveArtifactCoordinates(artifactCoordinates))
                 .select(Edges.ARTIFACT_HAS_ARTIFACT_COORDINATES);
    }

    private Traversal<Vertex, Edge> createArtifactCoordinates(ArtifactCoordinates artifactCoordinates)
    {
        return __.<Vertex>addE(Edges.ARTIFACT_HAS_ARTIFACT_COORDINATES)
                 .from(__.identity())
                 .to(saveArtifactCoordinates(artifactCoordinates));
    }

    private <S2> EntityTraversal<S2, Vertex> saveArtifactCoordinates(ArtifactCoordinates artifactCoordinates)
    {
        UnfoldEntityTraversal<Vertex, Vertex> artifactCoordinatesUnfold = artifactCoordinatesAdapter.unfold(artifactCoordinates);
        String artifactCoordinatesLabel = artifactCoordinatesUnfold.entityLabel();

        return __.<S2>V()
                 .saveV(artifactCoordinatesLabel,
                        artifactCoordinates.getUuid(),
                        artifactCoordinatesUnfold)
                 .sideEffect(EntityTraversalUtils::traceVertex)
                 .outE(Edges.ARTIFACT_COORDINATES_INHERIT_GENERIC_ARTIFACT_COORDINATES)
                 .inV()
                 .sideEffect(EntityTraversalUtils::traceVertex);
    }

    private EntityTraversal<Vertex, Vertex> unfoldArtifact(Artifact entity)
    {
        EntityTraversal<Vertex, Vertex> t = __.<Vertex>identity();

        if (entity.getStorageId() != null)
        {
            t = t.property(single, "storageId", entity.getStorageId());
        }
        if (entity.getRepositoryId() != null)
        {
            t = t.property(single, "repositoryId", entity.getRepositoryId());
        }

        ArtifactArchiveListing artifactArchiveListing = entity.getArtifactArchiveListing();
        t = t.sideEffect(__.properties("filenames").drop());
        Set<String> filenames = artifactArchiveListing.getFilenames();
        for (String filename : filenames)
        {
            t = t.property(set, "filenames", filename);
        }

        return t;
    }

    @Override
    public EntityTraversal<Vertex, ? extends Element> cascade()
    {
        return __.<Vertex>aggregate("x")
                 .optional(__.outE(Edges.ARTIFACT_HAS_ARTIFACT_COORDINATES)
                             .inV()
                             .where(__.inE(Edges.ARTIFACT_HAS_ARTIFACT_COORDINATES).count().is(1))
                             .aggregate("x")
                             .inE(Edges.ARTIFACT_COORDINATES_INHERIT_GENERIC_ARTIFACT_COORDINATES)
                             .outV()
                             .aggregate("x"))
                 .select("x")
                 .unfold();
    }

}
