package com.capstoneproject.codereviewsystem.services.model;
import java.util.List;

public class ModelMigrationEvent {

    private final List<AffectedRepository> affectedRepositories;
    private final String newDefaultModelName;

    public ModelMigrationEvent(List<AffectedRepository> affectedRepositories,
                                String newDefaultModelName) {
        this.affectedRepositories = affectedRepositories;
        this.newDefaultModelName  = newDefaultModelName;
    }

    public List<AffectedRepository> getAffectedRepositories() {
        return affectedRepositories;
    }

    public String getNewDefaultModelName() {
        return newDefaultModelName;
    }


    public record AffectedRepository(
            Long   repoId,
            String repoName,
            String ownerEmail,
            String ownerName
    ) {}
}