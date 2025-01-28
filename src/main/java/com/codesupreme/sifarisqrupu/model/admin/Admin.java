package com.codesupreme.sifarisqrupu.model.admin;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Table
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
public class Admin {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String oneSignal;
    private String name;
    private String phoneNumber;
    private List<Long> notificationHistory;
    private Boolean isMutedNotifications;
    private String password;
    private Boolean isRealAdmin;
    private Boolean isDisable;

}
