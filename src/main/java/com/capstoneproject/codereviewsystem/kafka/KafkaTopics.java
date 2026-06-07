package com.capstoneproject.codereviewsystem.kafka;

public final class KafkaTopics {
    private KafkaTopics() {}

    public static final String REVIEW_SUBMITTED = "review.submitted";

    public static final String REVIEW_CLEAN     = "review.clean";

    public static final String REVIEW_READY     = "review.ready";
    
    public static final String EMAIL_NOTIFICATIONS   = "email.notifications";
}
