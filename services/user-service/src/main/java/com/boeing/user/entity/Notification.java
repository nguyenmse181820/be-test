package com.boeing.user.entity;

import com.boeing.user.common.Auditable;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.GenericGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "notification")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification extends Auditable {

    @Id
    @GeneratedValue(generator = "uuid2")
    @GenericGenerator(name = "uuid2", strategy = "uuid2")
    private UUID id;

    @Column(nullable = false)
    private String type;

    @ManyToOne
    @JoinColumn(name = "template_id")
    private NotificationTemplate template;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "content_rendered", nullable = false, columnDefinition = "text")
    private String contentRendered;

    @Column(nullable = false)
    private String status;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;
}

