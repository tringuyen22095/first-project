package personal.monolithic.entity;

import static personal.monolithic.constants.CommonColumns.COL_DOB;
import static personal.monolithic.constants.CommonColumns.COL_FULL_NAME;
import static personal.monolithic.constants.CommonColumns.COL_GENDER;
import static personal.monolithic.constants.CommonColumns.COL_PERMANENT_ADDRESS;
import static personal.monolithic.constants.CommonColumns.COL_PHONE;
import static personal.monolithic.constants.CommonColumns.COL_TEMPORARY_ADDRESS;
import static personal.monolithic.constants.CommonColumns.COL_USER_ID;
import static personal.monolithic.constants.Constants.UUID_COLUMN_LENGTH;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import personal.monolithic.constants.Gender;

@Table(name = "T_USER_INFO")
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
@Entity
public class UserInfo implements Serializable {

    @Id
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = COL_USER_ID, length = UUID_COLUMN_LENGTH, nullable = false)
    private UUID userId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = COL_USER_ID)
    private User user;

    @Column(name = COL_FULL_NAME)
    private String fullName;

    @Column(name = COL_DOB)
    private LocalDate dob;

    @Column(name = COL_PERMANENT_ADDRESS)
    private String permanentAddress;

    @Column(name = COL_TEMPORARY_ADDRESS)
    private String temporaryAddress;

    @Column(name = COL_PHONE)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(name = COL_GENDER)
    private Gender gender;

}
