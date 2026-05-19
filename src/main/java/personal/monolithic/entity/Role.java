package personal.monolithic.entity;

import static personal.monolithic.constants.CommonColumns.COL_DESCRIPTION;
import static personal.monolithic.constants.CommonColumns.COL_PERMISSION;
import static personal.monolithic.constants.CommonColumns.COL_ROLE;
import static personal.monolithic.constants.Constants.NORMAL_COLUMN_LENGTH;

import java.io.Serializable;
import java.util.Set;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;

import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import personal.monolithic.entity.base.BaseEntity;

@SQLDelete(sql = "UPDATE T_ROLE " + "SET STATE = 'DELETED' " + "WHERE ID = ?")
@SQLRestriction("state = 'ACTIVE'")
@Table(name = "T_ROLE")
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@Getter
@Setter
@Entity
public class Role extends BaseEntity implements Serializable {

    @Id
    @Column(name = COL_ROLE, length = NORMAL_COLUMN_LENGTH)
    private String role;

    @Column(name = COL_DESCRIPTION, length = NORMAL_COLUMN_LENGTH)
    private String description;

    @ManyToMany(mappedBy = "roles", fetch = FetchType.LAZY)
    private Set<User> users;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "ROLE_PERMISSION", joinColumns = @JoinColumn(name = COL_ROLE), inverseJoinColumns = @JoinColumn(name = COL_PERMISSION))
    private Set<Permission> permissions;

}

