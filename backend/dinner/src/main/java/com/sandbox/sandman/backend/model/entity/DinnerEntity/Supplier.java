package com.sandbox.sandman.backend.model.entity.DinnerEntity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Nationalized;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "suppliers", schema = "dinner")
public class Supplier {
    @Id
    @Column(name = "supplier_id", nullable = false)
    private Integer id;

    @Size(max = 100)
    @NotNull
    @Nationalized
    @Column(name = "supplier_name", nullable = false, length = 100)
    private String supplierName;

    @Size(max = 50)
    @NotNull
    @Nationalized
    @Column(name = "contact_person", nullable = false, length = 50)
    private String contactPerson;

    @Size(max = 20)
    @NotNull
    @Column(name = "phone", nullable = false, length = 20)
    private String phone;

    @Size(max = 100)
    @Column(name = "email", length = 100)
    private String email;

    @NotNull
    @Nationalized
    @Lob
    @Column(name = "address", nullable = false)
    private String address;

    @ColumnDefault("getdate()")
    @Column(name = "created_at")
    private Instant createdAt;

}