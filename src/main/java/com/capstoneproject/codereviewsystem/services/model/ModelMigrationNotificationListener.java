package com.capstoneproject.codereviewsystem.services.model;

import com.capstoneproject.codereviewsystem.services.email.EmailContentService;
import com.capstoneproject.codereviewsystem.services.email.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ModelMigrationNotificationListener {

    private final EmailService emailService;
    private final EmailContentService emailContentService;

    @Async
    @EventListener
    public void handleModelMigration(ModelMigrationEvent event) {
        log.info("Sending migration notifications to {} users",
                event.getAffectedRepositories().size());

        for (ModelMigrationEvent.AffectedRepository repo : event.getAffectedRepositories()) {
            try {
                emailService.sendEmail(
                        repo.ownerEmail(),
                        emailContentService.modelMigrationSubject(repo.repoName()),
                        emailContentService.modelMigrationBody(
                                repo.ownerName(),
                                repo.repoName(),
                                event.getNewDefaultModelName()
                        )
                );
            } catch (Exception e) {
                log.error("Failed to send migration email to {} for repo {}: {}",
                        repo.ownerEmail(), repo.repoName(), e.getMessage());
            }
        }
    }
}