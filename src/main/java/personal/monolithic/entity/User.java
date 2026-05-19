package personal.monolithic.entity;

import static personal.monolithic.constants.CommonColumns.COL_EMAIL;
import static personal.monolithic.constants.CommonColumns.COL_ID;
import static personal.monolithic.constants.CommonColumns.COL_PWD;
import static personal.monolithic.constants.CommonColumns.COL_ROLE;
import static personal.monolithic.constants.CommonColumns.COL_USER_ID;
import static personal.monolithic.constants.CommonColumns.COL_USR;
import static personal.monolithic.constants.Constants.NORMAL_COLUMN_LENGTH;
import static personal.monolithic.constants.Constants.UUID_COLUMN_LENGTH;

import java.io.Serializable;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.NamedQueries;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import personal.monolithic.entity.base.BaseEntity;

@SQLDelete(sql = "UPDATE T_USER SET STATE = 'DELETED' WHERE ID = ?")
@SQLRestriction("state = 'ACTIVE'")
@Table(name = "T_USER")
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@Getter
@Setter
@Entity
@NamedQueries({
    @NamedQuery(name = User.NQ_FIND_BY_USR_OR_EMAIL, query = "SELECT DISTINCT u FROM User u "
                                                             + "LEFT JOIN FETCH u.roles r "
                                                             + "LEFT JOIN FETCH r.permissions "
                                                             + "WHERE u.email = :usr "
                                                             + "OR u.usr = :usr")
})
public class User extends BaseEntity implements Serializable {

    public static final String NQ_FIND_BY_USR_OR_EMAIL = "User.NQ_FIND_BY_USR_OR_EMAIL";

    @Id
    @GeneratedValue
    @UuidGenerator
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = COL_ID, length = UUID_COLUMN_LENGTH, nullable = false)
    private UUID id;

    @Column(name = COL_EMAIL, length = NORMAL_COLUMN_LENGTH)
    private String email;

    @Column(name = COL_USR, length = NORMAL_COLUMN_LENGTH)
    private String usr;

    @Column(name = COL_PWD, length = NORMAL_COLUMN_LENGTH)
    private String pwd;

    @OneToOne(mappedBy = "user", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private UserInfo userInfo;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "T_USER_ROLE", joinColumns = @JoinColumn(name = COL_USER_ID), inverseJoinColumns = @JoinColumn(name = COL_ROLE))
    private Set<Role> roles;

}
